package jyk.bcar.automation.job.result

data class AssignCarsResult(
    val assigned: Int,
    val released: Int = 0,
    val shortfall: Int = 0,
    /** 업로드 체인이 돌 유저 순서. 첫 유저부터 한 명씩 이어 제출된다 */
    val uploadUserIds: List<String> = emptyList(),
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
