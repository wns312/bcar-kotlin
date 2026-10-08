package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.sources.CarType
import jyk.bcar.automation.job.act.sources.SourceAdminLogin
import jyk.bcar.automation.job.act.sources.draft.CollectCarListRequest
import jyk.bcar.automation.job.act.sources.draft.CollectDraftCarList
import jyk.bcar.automation.job.act.sources.draft.CollectDraftCarSearchRange
import jyk.bcar.automation.job.act.sources.draft.DraftFilter
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.domain.Car
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.PipelineControlRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class CollectDraftJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val pipelineControl: PipelineControlRepository,
    private val webClient: WebClient,
) : AutomationJob<CollectDraftResult> {
    companion object {
        private const val MIN_PRICE = 100
        private val FILTERS = listOf(
            DraftFilter(CarType.BUS, MIN_PRICE, maxPrice = 4000),
            DraftFilter(CarType.TRUCK, MIN_PRICE, maxPrice = 4000),
            DraftFilter(CarType.ALL, MIN_PRICE, maxPrice = 2500),
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult = withContext(Dispatchers.IO) {
        logger.info("Collecting draft ids.")

        val sourceAdminUser = userRepository.findSourceAdminUser()
        val (cookieHeader, ranges) = runner.withSession { session ->
            session.usePage { page ->
                val login = SourceAdminLogin(page).doAct(sourceAdminUser)
                login.cookieHeader to FILTERS.associateWith { CollectDraftCarSearchRange(page).doAct(it) }
            }
        }

        val carList = CollectDraftCarList(webClient, cookieHeader)
        val collected = ranges.flatMap { (filter, range) -> carList.doAct(CollectCarListRequest(filter, range)) }
        val changes = Car.reconcile(existing = carRepository.findAll(), collected = collected)
        carRepository.saveAll(changes)
        // 이번 launch의 detail 체인 카운터. 마지막 체인이 assign을 제출하는 기준이 된다
        pipelineControl.resetDetailChains()
        logger.info("Drafts reconciled: collected=${collected.size}, changed=${changes.size}")

        CollectDraftResult(message = "drafts collected")
    }
}
