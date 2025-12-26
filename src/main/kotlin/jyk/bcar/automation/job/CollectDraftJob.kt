package jyk.bcar.automation.job

import jyk.bcar.automation.job.result.CollectDraftResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CollectDraftJob : AutomationJob<CollectDraftResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult {
        logger.info("Collecting draft ids.")
        return CollectDraftResult(
            draftIds = listOf("draft-1", "draft-2"),
            message = "drafts collected",
        )
    }
}
