package jyk.bcar.automation.job.result

data class CollectCategoryResult(
    val companies: Int = 0,
    val models: Int = 0,
    val detailModels: Int = 0,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
