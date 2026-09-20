package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.SourceAdminLogin
import jyk.bcar.automation.job.act.sources.SourceAdminLoginResult
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytes
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytesRequest
import jyk.bcar.automation.job.act.sources.detail.DetailExtractor
import jyk.bcar.automation.job.act.sources.detail.DetailExtractorRequest
import jyk.bcar.automation.job.result.CollectDraftResult
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

@Component
class CollectDetailJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val args: ApplicationArguments,
    webClient: WebClient,
) : AutomationJob<CollectDraftResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val collectDetailPageBytes = CollectDetailPageBytes(webClient)
    private val detailExtractor = DetailExtractor()

    override val name: String = "collect-detail"

    override suspend fun execute(): CollectDraftResult = withContext(Dispatchers.IO) {
        // Batch array job이면 자식마다 세그먼트 하나. 로컬/단일 실행이면 0/1
        val shards = args.getOptionValues("shards")?.firstOrNull()?.toInt() ?: 1
        val shard = System.getenv("AWS_BATCH_JOB_ARRAY_INDEX")?.toInt() ?: 0
        logger.info("Collecting detail cars. shard=$shard/$shards")

        // 비로그인 요청은 IP당 ~15건에서 차단됨. 딜러 세션으로 요청
        val cookieHeader = login().cookieHeader

        // 상세 페이지는 IP당 요청 예산이 매우 작다(~15건). 이미 수집된 차량은 건너뛰고 미수집분만
        val cars = carRepository
            .findAll(segment = shard, totalSegments = shards)
            .filter { it.isActive && it.detail == null }
        var done = 0

        try {
            // IP당 15~16건에서 죽으므로 청크가 크면 마지막 청크를 통째로 잃는다. 5건이면 손실 ≤ 1건
            cars.chunked(5).forEach { chunk ->
                val collected = chunk.mapNotNull { car ->
                    val detail = getDetail(car, cookieHeader)
                    done++
                    detail?.let { car.copy(detail = it) }
                }
                carRepository.saveAll(collected)
                logger.info("Details progress: $done/${cars.size}, saved in chunk=${collected.size}")
                delay(1000)
            }
        } catch (e: Exception) {
            // 자식 잡이 어디까지 갔는지 남긴다 — IP당 차단 임계치 측정용
            logger.error("Detail collection died after $done/${cars.size} cars in shard=$shard", e)
            throw e
        }

        CollectDraftResult(message = "details collected: shard=$shard/$shards, cars=${cars.size}")
    }

    // 파싱 실패(페이지 사라짐·구조 변경)는 차량 하나만 건너뛴다. 네트워크 실패는 재시도 후에도 안 되면 잡 자체를 죽임
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
            logger.warn("Skip detail for ${car.carNumber} (m_no=${car.detailPageNum}): ${e.message}")
            null
        } catch (e: IllegalStateException) {
            logger.warn("Skip detail for ${car.carNumber} (m_no=${car.detailPageNum}): ${e.message}")
            null
        }
    }
}
