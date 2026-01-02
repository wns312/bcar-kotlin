package jyk.bcar.automation.job.act.draft

import com.microsoft.playwright.Page
import jyk.bcar.domain.SourceAdminUser

class Login(
    private val page: Page,
) : DraftAct<SourceAdminUser, Unit> {
    companion object {
        private const val COLLECT_LOGIN_URL = "http://thebestcar.kr/mypage/login.html"
        private const val COLLECT_ADMIN_LOGIN_OK_URL = "http://thebestcar.kr/mypage/login_ok.html"
    }

    override suspend fun doAct(input: SourceAdminUser) {
        page.navigate(COLLECT_LOGIN_URL)
        check(page.url() == COLLECT_LOGIN_URL)

        page.locator(".iptD").let { iptDs ->
            iptDs.nth(0).fill(input.id)
            iptDs.nth(1).fill(input.password)
        }

        page.locator("button[class=\"btn_login\"]").click()
        if (page.url() == COLLECT_ADMIN_LOGIN_OK_URL) {
            page.navigate(DraftAct.COLLECT_ADMIN_URL)
        }
        check(page.url() == DraftAct.COLLECT_ADMIN_URL)
    }
}
