package jyk.bcar.automation.job.decider

data class NextJobRequest(
    val jobName: String,
    val parameters: Map<String, String> = emptyMap(),
    /** 이 접두사로 시작하는 잡이 이미 진행 중이면 제출하지 않는다 (같은 shard 체인 중복 방지) */
    val skipIfActive: String? = null,
)
