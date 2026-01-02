package jyk.bcar.automation.job.act.draft

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import jyk.bcar.automation.job.act.CarType

class CollectDraftCarSearchRange(
    private val page: Page,
) : DraftAct<CollectCarSearchRangeRequest, IntRange> {
    override suspend fun doAct(input: CollectCarSearchRangeRequest): IntRange {
        val (carType, minPrice, maxPrice) = input
        val url = buildString {
            append("${DraftAct.COLLECT_ADMIN_URL}?searchChecker=1&mode=&pageSize=100&c_cho=${carType.searchNum}")
            minPrice?.let { append("&c_price1=$it") }
            maxPrice?.let { append("&c_price2=$it") }
        }
        page.navigate(url)
        page.waitForLoadState(LoadState.NETWORKIDLE)

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

data class CollectCarSearchRangeRequest(
    val carType: CarType = CarType.ALL,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
)
