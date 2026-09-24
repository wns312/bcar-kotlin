package jyk.bcar.automation.job.act.target

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import jyk.bcar.automation.job.act.JobAct
import org.slf4j.LoggerFactory

/**
 * 관리 페이지를 훑어 사이트에 실제로 올라와 있는 매물을 확인하고, DB가 모르는 매물은 지운다.
 * 관리자가 손으로 올리거나 내린 것까지 DB에 반영하려면 사이트가 원천이어야 한다.
 */
class SyncUploadedCars(
    private val page: Page,
) : JobAct<SyncUploadedCarsRequest, SyncUploadedCarsResult> {
    companion object {
        private const val PAGE_LINKS = "#_carManagement > div > *"
        private const val TABLE = "#_carManagement > table"
        private const val ROWS = "$TABLE > tbody > tr"
        private const val CAR_NUMBER = "td.photo > a > span"
        private const val CHECKBOX = "td.align-c > input[type=checkbox]"
        private const val DELETE_BUTTON = "#searchListForm > div.menu-bar.mylist_toolbar.clearfix > div > button.btn_del"
        private const val DELETE_CONFIRM = "#fallr-button-confirmButton2"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun doAct(input: SyncUploadedCarsRequest): SyncUploadedCarsResult {
        page.onDialog { it.accept() }
        openPage(input.manageUrl, 1)

        val found = mutableSetOf<String>()
        val deleted = mutableListOf<String>()
        // 매물이 한 페이지에 다 들어가면 페이지 링크가 아예 없다
        val pageCount = page.locator(PAGE_LINKS).count().coerceAtLeast(1)
        // 뒷 페이지부터 지워야 삭제 때문에 앞 페이지 목록이 밀리지 않는다
        for (pageNumber in pageCount downTo 1) {
            openPage(input.manageUrl, pageNumber)
            val checked = mutableListOf<String>()
            val rows = page.locator(ROWS)
            for (i in 0 until rows.count()) {
                val row = rows.nth(i)
                // 매물이 없으면 "조회된 매물이 없습니다" 행 하나가 온다
                val numberCell = row.locator(CAR_NUMBER)
                if (numberCell.count() == 0) continue
                val carNumber = numberCell.textContent().trim()
                if (carNumber in input.expected) {
                    found += carNumber
                    continue
                }
                if (input.delete) row.locator(CHECKBOX).check()
                checked += carNumber
            }
            val action = if (input.delete) "삭제" else "관찰만"
            logger.info("Page $pageNumber/$pageCount: ${found.size + checked.size}대 중 DB에 없는 ${checked.size}대 ($action)")
            if (checked.isEmpty() || !input.delete) continue
            deleteChecked()
            deleted += checked
        }

        return SyncUploadedCarsResult(found = found, deleted = deleted)
    }

    private fun openPage(manageUrl: String, pageNumber: Int) {
        page.navigate("$manageUrl?page=$pageNumber", Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE))
        page.waitForSelector(TABLE)
    }

    private fun deleteChecked() {
        page.locator(DELETE_BUTTON).click()
        page.waitForSelector(DELETE_CONFIRM)
        // 확인 버튼은 click()에 반응하지 않는다 — 이벤트를 직접 올려야 한다
        page.waitForNavigation(Page.WaitForNavigationOptions().setWaitUntil(WaitUntilState.NETWORKIDLE)) {
            page.evaluate(
                "selector => document.querySelector(selector).dispatchEvent(new Event('click', { bubbles: true }))",
                DELETE_CONFIRM,
            )
        }
    }
}

data class SyncUploadedCarsRequest(
    val manageUrl: String,
    /** 사이트에 있어야 하는 차량번호. 나머지는 지운다 */
    val expected: Set<String>,
    /** false면 지우지 않고 목록만 확인한다 */
    val delete: Boolean = true,
)

data class SyncUploadedCarsResult(
    val found: Set<String>,
    val deleted: List<String>,
)
