package jyk.bcar.automation.job.result

data class CollectDraftResult(
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
