package jyk.bcar.automation.job.decider

data class NextJobRequest(
    val jobName: String,
    val parameters: Map<String, String> = emptyMap(),
    /** 이 접두사로 시작하는 잡이 이미 진행 중이면 제출하지 않는다 (같은 shard 체인 중복 방지) */
    val skipIfActive: String? = null,
) {
    /** Batch 잡 이름. 파라미터 순서대로 jobName-key1value1-key2value2… — skipIfActive 접두사도 이 규칙으로 만든다 */
    fun batchJobName(): String =
        (listOf(jobName) + parameters.map { (k, v) -> "$k$v" })
            .joinToString("-")
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .take(128)
}
