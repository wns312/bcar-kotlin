package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.CarType
import jyk.bcar.automation.job.act.draft.CollectCarListRequest
import jyk.bcar.automation.job.act.draft.CollectCarSearchRangeRequest
import jyk.bcar.automation.job.act.draft.CollectDraftCarList
import jyk.bcar.automation.job.act.draft.CollectDraftCarSearchRange
import jyk.bcar.automation.job.act.draft.Login
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.client.GoogleSheetsClient
import jyk.bcar.configuration.GoogleProperties
import jyk.bcar.domain.DraftCar
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
    private val googleSheetsClient: GoogleSheetsClient,
    private val googleProperties: GoogleProperties,
    private val webClient: WebClient,
) : AutomationJob<CollectDraftResult> {
    companion object {
        private val busSearchRangeRequest = CollectCarSearchRangeRequest(carType = CarType.BUS, minPrice = 100, maxPrice = 4000)
        private val truckSearchRangeRequest = CollectCarSearchRangeRequest(carType = CarType.TRUCK, minPrice = 100, maxPrice = 4000)
        private val allSearchRangeRequest = CollectCarSearchRangeRequest(carType = CarType.ALL, minPrice = 100, maxPrice = 2500)
        private const val DRAFT_SHEET_NAME = "Draft목록"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult = withContext(Dispatchers.IO) {
        logger.info("Collecting draft ids.")

        val (busRange, truckRange, allRange) = collectRanges()

        val collectDraftListJob = CollectDraftCarList(webClient)
        val busDraftCars = collectDraftListJob.doAct(
            CollectCarListRequest(carType = CarType.BUS, minPrice = 100, maxPrice = 4000, busRange),
        )
        val truckDraftCars = collectDraftListJob.doAct(
            CollectCarListRequest(carType = CarType.TRUCK, minPrice = 100, maxPrice = 4000, truckRange),
        )
        val allDraftCars = collectDraftListJob.doAct(
            CollectCarListRequest(carType = CarType.ALL, minPrice = 100, maxPrice = 2500, allRange),
        )

        uploadDrafts(busDraftCars + truckDraftCars + allDraftCars)

        CollectDraftResult(message = "drafts collected")
    }

    private suspend fun collectRanges(): Triple<IntRange, IntRange, IntRange> {
        val sourceAdminUser = userRepository.findSourceAdminUser()
        return runner.withSession { session ->
            session.usePage {
                Login(it).doAct(sourceAdminUser)
                val busRange = CollectDraftCarSearchRange(it).doAct(input = busSearchRangeRequest)
                val truckRange = CollectDraftCarSearchRange(it).doAct(input = truckSearchRangeRequest)
                val allRange = CollectDraftCarSearchRange(it).doAct(input = allSearchRangeRequest)

                Triple(busRange, truckRange, allRange)
            }
        }
    }

    private suspend fun uploadDrafts(drafts: List<DraftCar>) {
        val header = listOf("차량 번호", "차량 제목", "차량 제조사", "상세페이지 번호", "중고차 사무실", "판매자", "판매자 휴대전화", "가격")

        val rows = drafts.map {
            listOf(it.carNumber, it.title, it.company, it.detailPageNum, it.agency, it.seller, it.sellerPhone, it.price)
        }

        val values: List<List<Any>> = buildList {
            add(header)
            addAll(rows)
        }

        googleSheetsClient.clearRange(
            spreadsheetId = googleProperties.sheets.id,
            sheet = DRAFT_SHEET_NAME,
        )

        googleSheetsClient.updateRange(
            spreadsheetId = googleProperties.sheets.id,
            sheet = DRAFT_SHEET_NAME,
            rangeA1 = "A1",
            values = values,
        )
    }
}
