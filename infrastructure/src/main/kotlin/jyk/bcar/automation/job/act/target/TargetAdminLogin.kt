package jyk.bcar.automation.job.act.target

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.domain.TargetAdminUser

/** 로그인하고 매물 관리 페이지에 선다 */
class TargetAdminLogin(
    private val page: Page,
) : JobAct<TargetAdminUser, Unit> {
    companion object {
        private const val ID_INPUT = "#content > form > fieldset > div.form_inputbox > div:nth-child(1) > input"
        private const val PASSWORD_INPUT = "#content > form > fieldset > div.form_inputbox > div:nth-child(3) > input"
        private const val SUBMIT_BUTTON = "#content > form > fieldset > span > input"
    }

    override suspend fun doAct(input: TargetAdminUser) {
        page.navigate(
            input.loginUrlRedirecting(input.manageUrl),
            Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE),
        )

        page.locator(ID_INPUT).fill(input.id)
        page.locator(PASSWORD_INPUT).fill(input.password)
        page.locator(SUBMIT_BUTTON).click()

        page.waitForURL(input.manageUrl)
    }
}
