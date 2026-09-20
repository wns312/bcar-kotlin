package jyk.bcar.automation.job.result

data class CollectDetailResult(
    val shard: Int,
    val shards: Int,
    val hop: Int,
    val remaining: Int,
    val stopped: Boolean = false,
    override val success: Boolean = true,
    override val message: String? = null,
) : JobResult
