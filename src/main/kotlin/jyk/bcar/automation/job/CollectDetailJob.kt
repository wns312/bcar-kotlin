package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytes
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytesRequest
import jyk.bcar.automation.job.act.sources.detail.DetailExtractor
import jyk.bcar.automation.job.act.sources.detail.DetailExtractorRequest
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.repository.CarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class CollectDetailJob(
    private val carRepository: CarRepository,
    webClient: WebClient,
) : AutomationJob<CollectDraftResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val collectDetailPageBytes = CollectDetailPageBytes(webClient)
    private val detailExtractor = DetailExtractor()

    override val name: String = "collect-detail"

    override suspend fun execute(): CollectDraftResult = withContext(Dispatchers.IO) {
        logger.info("Collecting detail cars.")

        val cars = carRepository.findAll().filter { it.isActive }
        var done = 0

        cars.chunked(100).forEach { chunk ->
            val changed = chunk.mapNotNull { car ->
                val detail = getDetail(car) ?: return@mapNotNull null
                car.copy(detail = detail).takeIf { detail != car.detail }
            }
            carRepository.saveAll(changed)
            done += chunk.size
            logger.info("Details progress: $done/${cars.size}, changed in chunk=${changed.size}")
            delay(1000)
        }

        CollectDraftResult(message = "details collected")
    }

    // 파싱 실패(페이지 사라짐·구조 변경)는 차량 하나만 건너뛴다. 네트워크 실패는 재시도 후에도 안 되면 잡 자체를 죽임
    private suspend fun getDetail(car: Car): CarDetail? {
        val bytes = collectDetailPageBytes.doAct(CollectDetailPageBytesRequest(car.detailPageNum))
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
