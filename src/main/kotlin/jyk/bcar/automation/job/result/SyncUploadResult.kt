package jyk.bcar.automation.job.result

data class SyncUploadResult(
    val userId: String,
    /** 다음 차례 유저. 없으면 체인 끝 */
    val nextUserId: String? = null,
    val onSite: Int = 0,
    val removed: Int = 0,
    val released: Int = 0,
    val uploaded: Int = 0,
    val failed: Int = 0,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
