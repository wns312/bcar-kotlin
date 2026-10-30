package jyk.bcar.automation.job

data class DetailCollectionOptions(
    val shards: Int = 1,
    val shard: Int = 0,
    val hop: Int = 0,
    val idle: Int = 0,
)

data class SyncUploadOptions(
    val userId: String?,
    val delete: Boolean = true,
    val submit: Boolean = true,
    val limit: Int = Int.MAX_VALUE,
)
