package jyk.bcar.automation.job.result

data class CollectDetailResult(
    val shard: Int,
    val shards: Int,
    val hop: Int,
    val remaining: Int,
    /** 이번 hop 포함 연속으로 0건 처리한 hop 수. 사이트 장애 시 fuse까지 헛도는 걸 막는다 */
    val idleHops: Int = 0,
    val stopped: Boolean = false,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
