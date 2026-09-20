package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.SourceAdminLogin
import jyk.bcar.automation.job.act.sources.SourceAdminLoginResult
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytes
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytesRequest
import jyk.bcar.automation.job.act.sources.detail.DetailExtractor
import jyk.bcar.automation.job.act.sources.detail.DetailExtractorRequest
import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException

/**
 * 상세 페이지는 IP당 ~15건에서 차단된다. 잡 하나 = IP 하나 분량: 차단되면 남은 건수를 결과로 돌려주고
 * 후속 잡(같은 shard, hop+1) 제출은 JobChainDecider에 맡긴다.
 */
@Component
class CollectDetailJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val args: ApplicationArguments,
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
        logger.info("Collecting detail cars. shard=$shard/$shards hop=$hop")

        if (carRepository.isDetailCollectionStopped()) {
            logger.warn("Detail collection stopped by _control.stopDetail")
            return@withContext CollectDetailResult(shard, shards, hop, remaining = 0, stopped = true, message = "stopped")
        }

        val cookieHeader = login().cookieHeader
        val cars = carRepository
            .findAll(segment = shard, totalSegments = shards)
            .filter { it.isActive && it.detail == null }
        var done = 0
        var blocked = false

        // 차단 시 마지막 청크를 통째로 잃지 않도록 작게 저장
        for (chunk in cars.chunked(5)) {
            val updates = mutableListOf<Car>()
            for (car in chunk) {
                val detail = try {
                    getDetail(car, cookieHeader)
                } catch (e: WebClientException) {
                    logger.warn("Blocked after $done cars: ${e.message}")
                    blocked = true
                    break
                }
                done++
                // 파싱 실패 = 페이지가 없거나 바뀜. 비활성으로 내려 다음 hop이 같은 걸 또 긁지 않게. 목록에 다시 나오면 reconcile이 되살림
                updates += detail?.let { car.copy(detail = it) } ?: car.copy(isActive = false)
            }
            carRepository.saveAll(updates)
            logger.info("Details progress: $done/${cars.size}")
            if (blocked) break
            delay(1000)
        }

        val remaining = cars.size - done
        CollectDetailResult(
            shard = shard,
            shards = shards,
            hop = hop,
            remaining = remaining,
            message = "shard=$shard/$shards hop=$hop done=$done remaining=$remaining blocked=$blocked",
        )
    }

    private fun intArg(name: String): Int? = args.getOptionValues(name)?.firstOrNull()?.toInt()

    private suspend fun login(): SourceAdminLoginResult {
        val sourceAdminUser = userRepository.findSourceAdminUser()
        return runner.withSession { session ->
            session.usePage { SourceAdminLogin(it).doAct(sourceAdminUser) }
        }
    }

    private suspend fun getDetail(car: Car, cookieHeader: String): CarDetail? {
        val bytes = collectDetailPageBytes.doAct(CollectDetailPageBytesRequest(car.detailPageNum, cookieHeader))
        return try {
            detailExtractor.doAct(DetailExtractorRequest(bytes, CharSet.EUC_KR, baseUri = ""))
        } catch (e: IllegalArgumentException) {
            logger.warn("Unparseable detail for ${car.carNumber} (m_no=${car.detailPageNum}): ${e.message}")
            null
        } catch (e: IllegalStateException) {
            logger.warn("Unparseable detail for ${car.carNumber} (m_no=${car.detailPageNum}): ${e.message}")
            null
        }
    }
}
