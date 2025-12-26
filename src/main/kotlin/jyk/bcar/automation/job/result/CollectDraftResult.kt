package jyk.bcar.automation.job.result

data class CollectDraftResult(
    val draftIds: List<String>,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
