package jyk.bcar.automation.job.runner

import jyk.bcar.automation.job.AutomationJob
import jyk.bcar.automation.job.decider.JobChainDecider
import jyk.bcar.automation.job.result.JobResult
import jyk.bcar.automation.job.submitter.BatchJobSubmitter
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class JobRunner(
    private val jobRegistry: JobRegistry,
    private val jobChainDecider: JobChainDecider,
    private val batchJobSubmitter: BatchJobSubmitter,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(JobRunner::class.java)

    override fun run(args: ApplicationArguments) {
        val jobName = findJobName(args) ?: return

        val job =
            jobRegistry.get(jobName)
                ?: throw IllegalArgumentException("Unknown job '$jobName'. Available jobs: ${jobRegistry.names().sorted()}")

        runBlocking {
            val result = job.execute(jobName = jobName)

            val nextEnabled = parseNextEnabled(args.getOptionValues("next")?.firstOrNull())
            if (!nextEnabled) {
                logger.info("Next job submission disabled for '{}'.", jobName)
                return@runBlocking
            }

            submitNextJob(jobName = jobName, result = result)
        }
    }

    private fun findJobName(args: ApplicationArguments): String? {
        val jobName = args.getOptionValues("job")?.firstOrNull()
        if (jobName.isNullOrBlank()) {
            logger.info("No --job specified. Available jobs: {}", jobRegistry.names().sorted())
            return null
        }

        return jobName
    }

    private fun parseNextEnabled(raw: String?): Boolean {
        if (raw.isNullOrBlank()) {
            return true
        }

        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid --next value: '$raw'. Use true or false.")
        }
    }

    private suspend fun AutomationJob<JobResult>.execute(jobName: String): JobResult {
        val result = execute()
        if (!result.success) {
            throw IllegalStateException("Job '$jobName' failed: ${result.message ?: "no message"}")
        }

        logger.info("Job '{}' completed: {}", jobName, result.message ?: "success")

        return result
    }

    private suspend fun submitNextJob(
        jobName: String,
        result: JobResult,
    ) {
        val nextRequests = jobChainDecider.decide(jobName, result)
        if (nextRequests.isEmpty()) {
            logger.info("No next jobs to submit for '{}'.", jobName)
            return
        }

        nextRequests.forEach { request ->
            batchJobSubmitter.submit(request)
        }
    }
}
