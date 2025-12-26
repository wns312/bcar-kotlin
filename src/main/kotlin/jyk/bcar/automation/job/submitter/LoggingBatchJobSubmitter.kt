package jyk.bcar.automation.job.submitter

import jyk.bcar.automation.job.decider.NextJobRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingBatchJobSubmitter : BatchJobSubmitter {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun submit(request: NextJobRequest): SubmittedJob {
        logger.info("Submitting job '{}' with parameters={}", request.jobName, request.parameters)
        return SubmittedJob(jobName = request.jobName, jobId = null)
    }
}
