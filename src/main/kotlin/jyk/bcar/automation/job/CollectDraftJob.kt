package jyk.bcar.automation.job

import com.microsoft.playwright.Page
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.domain.SourceAdminUser
import jyk.bcar.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CollectDraftJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
) : AutomationJob<CollectDraftResult> {
    companion object {
        const val COLLECT_LOGIN_URL = "http://thebestcar.kr/mypage/login.html"
        const val COLLECT_ADMIN_URL = "http://thebestcar.kr/mypage/mycar.html"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult {
        logger.info("Collecting draft ids.")

        val sourceAdminUser = userRepository.findSourceAdminUser()
        runner.withSession { session ->
            session.usePage {
                it.loginSourceAdminPage(sourceAdminUser)
            }
        }

        logger.info("Login succeeded")

        return CollectDraftResult(
            draftIds = listOf("draft-1", "draft-2"),
            message = "drafts collected",
        )
    }

    private suspend fun Page.loginSourceAdminPage(sourceAdminUser: SourceAdminUser) {
        this.navigate(COLLECT_LOGIN_URL)
        check(this.url() == COLLECT_LOGIN_URL)

        this.locator(".iptD").let { iptDs ->
            iptDs.nth(0).fill(sourceAdminUser.id)
            iptDs.nth(1).fill(sourceAdminUser.password)
        }

        this.locator("button[class=\"btn_login\"]").click()
        check(this.url() == COLLECT_ADMIN_URL)
    }
}
