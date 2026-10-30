package jyk.bcar.automation.job.submitter

import jyk.bcar.automation.job.decider.NextJobRequest

interface BatchJobSubmitter {
    suspend fun submit(request: NextJobRequest): SubmittedJob
}

data class SubmittedJob(
    val jobName: String,
    val jobId: String? = null,
)
