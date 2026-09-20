package jyk.bcar.automation.job.result

data class AssignCarsResult(
    val assigned: Int,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
