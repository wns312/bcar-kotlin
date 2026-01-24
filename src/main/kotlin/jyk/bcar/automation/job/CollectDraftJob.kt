package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.sources.CarType
import jyk.bcar.automation.job.act.sources.SourceAdminLogin
import jyk.bcar.automation.job.act.sources.SourceAdminLoginResult
import jyk.bcar.automation.job.act.sources.draft.CollectCarListRequest
import jyk.bcar.automation.job.act.sources.draft.CollectCarSearchRangeRequest
import jyk.bcar.automation.job.act.sources.draft.CollectDraftCarList
import jyk.bcar.automation.job.act.sources.draft.CollectDraftCarSearchRange
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.repository.DraftCarRepository
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
    private val draftCarRepository: DraftCarRepository,
    private val webClient: WebClient,
) : AutomationJob<CollectDraftResult> {
    companion object {
        private const val DEFAULT_MIN_PRICE = 100
        private const val BUS_TRUCK_MAX_PRICE = 4000
        private const val ALL_MAX_PRICE = 2500

        private val busSearchRangeRequest = CollectCarSearchRangeRequest(
            carType = CarType.BUS,
            minPrice = DEFAULT_MIN_PRICE,
            maxPrice = BUS_TRUCK_MAX_PRICE,
        )
        private val truckSearchRangeRequest = CollectCarSearchRangeRequest(
            carType = CarType.TRUCK,
            minPrice = DEFAULT_MIN_PRICE,
            maxPrice = BUS_TRUCK_MAX_PRICE,
        )
        private val allSearchRangeRequest = CollectCarSearchRangeRequest(
            carType = CarType.ALL,
            minPrice = DEFAULT_MIN_PRICE,
            maxPrice = ALL_MAX_PRICE,
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult = withContext(Dispatchers.IO) {
        logger.info("Collecting draft ids.")

        val (busRange, truckRange, allRange, loginResult) = collectRanges()

        val collectDraftListJob = CollectDraftCarList(webClient, loginResult.cookieHeader)
        val busDraftCars = collectDraftListJob.doAct(
            CollectCarListRequest(
                carType = CarType.BUS,
                minPrice = DEFAULT_MIN_PRICE,
                maxPrice = BUS_TRUCK_MAX_PRICE,
                pageRange = busRange,
            ),
        )
        val truckDraftCars = collectDraftListJob.doAct(
            CollectCarListRequest(
                carType = CarType.TRUCK,
                minPrice = DEFAULT_MIN_PRICE,
                maxPrice = BUS_TRUCK_MAX_PRICE,
                pageRange = truckRange,
            ),
        )
        val allDraftCars = collectDraftListJob.doAct(
            CollectCarListRequest(
                carType = CarType.ALL,
                minPrice = DEFAULT_MIN_PRICE,
                maxPrice = ALL_MAX_PRICE,
                pageRange = allRange,
            ),
        )

        draftCarRepository.updateAll(busDraftCars + truckDraftCars + allDraftCars)

        CollectDraftResult(message = "drafts collected")
    }

    private suspend fun collectRanges(): CollectDraftRangeResult {
        val sourceAdminUser = userRepository.findSourceAdminUser()
        return runner.withSession { session ->
            session.usePage {
                val loginResult = SourceAdminLogin(it).doAct(sourceAdminUser)

                val busRange = CollectDraftCarSearchRange(it).doAct(input = busSearchRangeRequest)
                val truckRange = CollectDraftCarSearchRange(it).doAct(input = truckSearchRangeRequest)
                val allRange = CollectDraftCarSearchRange(it).doAct(input = allSearchRangeRequest)

                CollectDraftRangeResult(busRange, truckRange, allRange, loginResult)
            }
        }
    }
}

private data class CollectDraftRangeResult(
    val busRange: IntRange,
    val truckRange: IntRange,
    val allRange: IntRange,
    val loginResult: SourceAdminLoginResult,
)
