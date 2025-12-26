package jyk.bcar.automation.job.decider

data class NextJobRequest(
    val jobName: String,
    val parameters: Map<String, String> = emptyMap(),
)
