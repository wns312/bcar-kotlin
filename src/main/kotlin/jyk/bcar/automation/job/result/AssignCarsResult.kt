package jyk.bcar.automation.job.result

data class AssignCarsResult(
    val assigned: Int,
    val released: Int = 0,
    val shortfall: Int = 0,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
