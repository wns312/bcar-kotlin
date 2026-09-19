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
import jyk.bcar.client.RotatingWebClient
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class CollectDetailJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val webClient: WebClient,
    private val rotatingWebClient: RotatingWebClient,
) : AutomationJob<CollectDraftResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val collectDetailPageBytes = CollectDetailPageBytes(webClient, rotatingWebClient)

    override val name: String = "collect-detail"

    override suspend fun execute(): CollectDraftResult = withContext(Dispatchers.IO) {
        logger.info("Collecting detail cars.")
//        val loginResult = extractLoginCookieHeaders()

        val cars = carRepository.findAll().filter { it.isActive }

        cars.chunked(100).forEach { chunk ->
            val changed = chunk.mapNotNull { car ->
                val detail = getDetail(car)
                car.copy(detail = detail).takeIf { detail != car.detail }
            }
            carRepository.saveAll(changed)
            logger.info("Details updated: changed=${changed.size}, fetched=${chunk.size}")
            delay(1000)
        }

        CollectDraftResult(message = "details collected")
    }

    private suspend fun extractLoginCookieHeaders(): SourceAdminLoginResult {
        val sourceAdminUser = userRepository.findSourceAdminUser()
        val loginResult = runner.withSession { session ->
            session.usePage {
                SourceAdminLogin(it).doAct(sourceAdminUser)
            }
        }
        return loginResult
    }

    private suspend fun getDetail(car: Car): CarDetail {
        val bytes = collectDetailPageBytes.doAct(
            CollectDetailPageBytesRequest(
                detailPageNum = car.detailPageNum,
//                cookieHeader = cookieHeader,
            ),
        )

        return DetailExtractor().doAct(
            input = DetailExtractorRequest(
                htmlBytes = bytes,
                charSet = CharSet.EUC_KR,
                baseUri = "",
            ),
        )
    }
}
