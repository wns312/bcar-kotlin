package jyk.bcar.automation.job.result

data class CollectDetailResult(
    val shard: Int,
    val shards: Int,
    val hop: Int,
    val remaining: Int,
    /** 이번 hop 포함 연속으로 0건 처리한 hop 수. 사이트 장애 시 fuse까지 헛도는 걸 막는다 */
    val idleHops: Int = 0,
    val stopped: Boolean = false,
    /** 이 shard가 더 이어갈 게 없다(남은 0 또는 fuse). 판단은 잡이 한다 — 조건이 두 곳에 흩어지지 않게 */
    val chainEnded: Boolean = false,
    /** 이번 launch에서 끝난 체인 수. shards와 같으면 마지막 */
    val chainsDone: Int = 0,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
