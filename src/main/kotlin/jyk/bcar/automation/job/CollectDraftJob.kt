package jyk.bcar.automation.job

import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CollectDraftJob(
    private val runner: PlaywrightSessionRunner,
) : AutomationJob<CollectDraftResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult {
        logger.info("Collecting draft ids.")

        runner.withSession { session ->
            session.usePage { page ->
                page.navigate("https://google.com")
                delay(10000)
            }
        }

        return CollectDraftResult(
            draftIds = listOf("draft-1", "draft-2"),
            message = "drafts collected",
        )
    }
}
