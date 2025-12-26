package jyk.bcar.automation.job.result

data class AssignCarsResult(
    val carIds: List<String>,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
