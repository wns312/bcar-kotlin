package jyk.bcar.automation.job.act.sources

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.domain.SourceAdminUser

class SourceAdminLogin(
    private val page: Page,
) : JobAct<SourceAdminUser, SourceAdminLoginResult> {
    override suspend fun doAct(input: SourceAdminUser): SourceAdminLoginResult {
        page.navigate(
            SourceSite.LOGIN_URL,
            Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE),
        )
        check(page.url() == SourceSite.LOGIN_URL)

        page.locator(".iptD").let { iptDs ->
            iptDs.nth(0).fill(input.id)
            iptDs.nth(1).fill(input.password)
        }

        page.locator("button[class=\"btn_login\"]").click()
        if (page.url() == SourceSite.LOGIN_OK_URL) {
            page.navigate(
                SourceSite.MY_CAR_URL,
                Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE),
            )
        }
        check(page.url() == SourceSite.MY_CAR_URL)

        val cookieHeader = page.context().cookies().joinToString("; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }

        return SourceAdminLoginResult(cookieHeader = cookieHeader)
    }
}

data class SourceAdminLoginResult(
    val cookieHeader: String,
)
