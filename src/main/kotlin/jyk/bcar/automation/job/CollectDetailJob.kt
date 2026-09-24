package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytes
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytesRequest
import jyk.bcar.automation.job.act.sources.detail.DetailExtractor
import jyk.bcar.automation.job.act.sources.detail.DetailExtractorRequest
import jyk.bcar.automation.job.act.sources.detail.TakeoverListing
import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.configuration.BatchProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.repository.CarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

/**
 * 상세 페이지는 IP당 ~15건에서 차단된다. 잡 하나 = IP 하나 분량: 차단되면 남은 건수를 결과로 돌려주고
 * 후속 잡(같은 shard, hop+1) 제출은 JobChainDecider에 맡긴다.
 */
@Component
class CollectDetailJob(
    private val carRepository: CarRepository,
    private val args: ApplicationArguments,
    private val batchProperties: BatchProperties,
    webClient: WebClient,
) : AutomationJob<CollectDetailResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val collectDetailPageBytes = CollectDetailPageBytes(webClient)
    private val detailExtractor = DetailExtractor()

    override val name: String = "collect-detail"

    override suspend fun execute(): CollectDetailResult = withContext(Dispatchers.IO) {
        val shards = intArg("shards") ?: 1
        val shard = intArg("shard") ?: System.getenv("AWS_BATCH_JOB_ARRAY_INDEX")?.toInt() ?: 0
        val hop = intArg("hop") ?: 0
        val idle = intArg("idle") ?: 0
        logger.info("Collecting detail cars. shard=$shard/$shards hop=$hop idle=$idle")

        if (carRepository.isDetailCollectionStopped()) {
            logger.warn("Detail collection stopped by _control.stopDetail")
            return@withContext CollectDetailResult(shard, shards, hop, remaining = 0, stopped = true, message = "stopped")
        }

        val cars = carRepository
            .findAll(segment = shard, totalSegments = shards)
            .filter { it.isActive && it.detail == null }
        var done = 0
        var blocked = false

        // 차단 시 마지막 청크를 통째로 잃지 않도록 작게 저장
        for (chunk in cars.chunked(5)) {
            val updates = mutableListOf<Car>()
            for (car in chunk) {
                // fetch 단계 예외는 종류 불문 "이 IP는 끝" — reset, timeout, 빈 body 전부 차단 신호였다
                val bytes = try {
                    collectDetailPageBytes.doAct(CollectDetailPageBytesRequest(car.detailPageNum))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Blocked after $done cars: ${e.message}")
                    blocked = true
                    break
                }
                done++
                // 파싱 실패 = 페이지가 없거나 바뀜. 비활성으로 내려 다음 hop이 같은 걸 또 긁지 않게. 목록에 다시 나오면 reconcile이 되살림
                updates += when (val parsed = parseDetail(car, bytes)) {
                    is ParsedDetail.Ok -> car.copy(detail = parsed.detail, detailError = null)
                    is ParsedDetail.Failed -> car.copy(isActive = false, detailError = parsed.reason)
                    // 이미 올라가 있으면 내려야 하므로 deactivate 규칙을 태운다
                    // ponytail: 소스 목록에 계속 있으면 reconcile이 매 launch 되살려 상세를 다시 긁는다(수십 건 수준).
                    // 비용이 커지면 detailError를 보고 수집 대상에서 빼면 된다
                    is ParsedDetail.Excluded -> car.deactivate().copy(detailError = parsed.reason)
                }
            }
            carRepository.saveAll(updates)
            logger.info("Details progress: $done/${cars.size}")
            if (blocked) break
            delay(1000)
        }

        val remaining = cars.size - done
        val idleHops = if (done == 0) idle + 1 else 0
        val chainEnded = remaining == 0 || hop >= batchProperties.detailMaxHops || idleHops >= batchProperties.detailMaxIdleHops
        val chainsDone = if (chainEnded) carRepository.markDetailChainDone() else 0
        CollectDetailResult(
            shard = shard,
            shards = shards,
            hop = hop,
            remaining = remaining,
            idleHops = idleHops,
            chainEnded = chainEnded,
            chainsDone = chainsDone,
            message = "shard=$shard/$shards hop=$hop done=$done remaining=$remaining blocked=$blocked " +
                "idleHops=$idleHops chainEnded=$chainEnded chainsDone=$chainsDone/$shards",
        )
    }

    private fun intArg(name: String): Int? = args.getOptionValues(name)?.firstOrNull()?.toInt()

    private suspend fun parseDetail(car: Car, bytes: ByteArray): ParsedDetail =
        try {
            ParsedDetail.Ok(detailExtractor.doAct(DetailExtractorRequest(bytes, CharSet.EUC_KR, baseUri = "")))
        } catch (e: TakeoverListing) {
            logger.info("승계 매물 제외: ${car.carNumber} (m_no=${car.detailPageNum})")
            ParsedDetail.Excluded(e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            unparseable(car, e)
        } catch (e: IllegalStateException) {
            unparseable(car, e)
        }

    private fun unparseable(car: Car, e: Exception): ParsedDetail.Failed {
        val reason = e.message ?: e::class.simpleName.orEmpty()
        logger.warn("Unparseable detail for ${car.carNumber} (m_no=${car.detailPageNum}): $reason")
        return ParsedDetail.Failed(reason)
    }
}

private sealed interface ParsedDetail {
    data class Ok(
        val detail: CarDetail,
    ) : ParsedDetail

    data class Failed(
        val reason: String,
    ) : ParsedDetail

    /** 페이지는 멀쩡하지만 올리면 안 되는 차 */
    data class Excluded(
        val reason: String,
    ) : ParsedDetail
}
