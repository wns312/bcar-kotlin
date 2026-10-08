package jyk.bcar.automation.job.act.sources.draft

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.sources.CarType
import jyk.bcar.automation.job.act.sources.SourceSite

class CollectDraftCarSearchRange(
    private val page: Page,
) : JobAct<DraftFilter, IntRange> {
    override suspend fun doAct(input: DraftFilter): IntRange {
        val (carType, minPrice, maxPrice) = input
        page.navigate(
            "${SourceSite.MY_CAR_URL}?searchChecker=1&mode=&pageSize=100&c_cho=${carType.searchNum}&c_price1=$minPrice&c_price2=$maxPrice",
            Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE),
        )

        page.locator("#searchList").waitFor(
            Locator.WaitForOptions().apply {
                this.state = WaitForSelectorState.VISIBLE
            },
        )

        val rawSellCarCount = page
            .locator("#sellOpenCarCount")
            .textContent() ?: throw IllegalStateException("Cannot find $carType's selling carCount.")

        val sellCarCount = rawSellCarCount.replace(",", "").toInt()

        return parseCarCountToPageRange(sellCarCount)
    }

    private fun parseCarCountToPageRange(carCount: Int): IntRange {
        val end = carCount / 100 + (if (carCount % 100 == 0) 1 else 2)

        return 1 until end
    }
}

data class DraftFilter(
    val carType: CarType,
    val minPrice: Int,
    val maxPrice: Int,
)
