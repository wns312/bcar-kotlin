package jyk.bcar.automation.job.act.sources

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.sources.draft.DraftAct
import jyk.bcar.domain.SourceAdminUser

class SourceAdminLogin(
    private val page: Page,
) : JobAct<SourceAdminUser, SourceAdminLoginResult> {
    companion object {
        private const val COLLECT_LOGIN_URL = "http://thebestcar.kr/mypage/login.html"
        private const val COLLECT_ADMIN_LOGIN_OK_URL = "http://thebestcar.kr/mypage/login_ok.html"
    }

    override suspend fun doAct(input: SourceAdminUser): SourceAdminLoginResult {
        page.navigate(
            COLLECT_LOGIN_URL,
            Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE),
        )
        check(page.url() == COLLECT_LOGIN_URL)

        page.locator(".iptD").let { iptDs ->
            iptDs.nth(0).fill(input.id)
            iptDs.nth(1).fill(input.password)
        }

        page.locator("button[class=\"btn_login\"]").click()
        if (page.url() == COLLECT_ADMIN_LOGIN_OK_URL) {
            page.navigate(
                DraftAct.COLLECT_ADMIN_URL,
                Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE),
            )
        }
        check(page.url() == DraftAct.COLLECT_ADMIN_URL)

        val cookieHeader = page.context().cookies().joinToString("; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }

        return SourceAdminLoginResult(cookieHeader = cookieHeader)
    }
}

data class SourceAdminLoginResult(
    val cookieHeader: String,
)
