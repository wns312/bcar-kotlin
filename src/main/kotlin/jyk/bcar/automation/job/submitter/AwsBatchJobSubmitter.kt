package jyk.bcar.automation.job.submitter

import jyk.bcar.automation.job.decider.NextJobRequest
import jyk.bcar.configuration.BatchProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.batch.BatchClient
import java.time.Duration

@Component
class AwsBatchJobSubmitter(
    private val properties: BatchProperties,
    private val client: BatchClient = defaultClient(),
) : BatchJobSubmitter {
    companion object {
        fun defaultClient(): BatchClient =
            BatchClient
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .overrideConfiguration(
                    ClientOverrideConfiguration
                        .builder()
                        .apiCallTimeout(Duration.ofMinutes(1))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20))
                        .build(),
                ).build()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun submit(request: NextJobRequest): SubmittedJob = withContext(Dispatchers.IO) {
        val target = properties.jobs[request.jobName]
        if (target == null) {
            logger.error("No Batch target configured for job '{}', skipping. parameters={}", request.jobName, request.parameters)
            return@withContext SubmittedJob(jobName = request.jobName)
        }

        val command = listOf("--job=${request.jobName}", "--next=true") + request.parameters.map { (k, v) -> "--$k=$v" }
        val name = (listOf(request.jobName) + request.parameters.map { (k, v) -> "$k$v" })
            .joinToString("-")
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .take(128)

        val response = client.submitJob {
            it
                .jobName(name)
                .jobQueue(target.queue)
                .jobDefinition(target.definition)
                .containerOverrides { c -> c.command(command) }
        }
        logger.info("Submitted job '{}' id={} command={}", name, response.jobId(), command)
        SubmittedJob(jobName = request.jobName, jobId = response.jobId())
    }
}
