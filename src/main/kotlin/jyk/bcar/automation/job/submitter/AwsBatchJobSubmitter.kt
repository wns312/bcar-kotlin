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
import software.amazon.awssdk.services.batch.model.JobStatus
import software.amazon.awssdk.services.batch.model.KeyValuesPair
import java.time.Duration

@Component
class AwsBatchJobSubmitter(
    private val properties: BatchProperties,
    private val client: BatchClient = defaultClient(),
) : BatchJobSubmitter {
    companion object {
        private val ACTIVE_STATUSES = setOf(
            JobStatus.SUBMITTED,
            JobStatus.PENDING,
            JobStatus.RUNNABLE,
            JobStatus.STARTING,
            JobStatus.RUNNING,
        )

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

        request.skipIfActive?.let { prefix ->
            val active = activeJobNames(target.queue, prefix)
            if (active.isNotEmpty()) {
                logger.warn("Skip submitting '{}': already active {}", request.jobName, active)
                return@withContext SubmittedJob(jobName = request.jobName)
            }
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

    // JOB_NAME 필터를 쓰면 상태 무관하게 최신순으로 오므로 첫 페이지에서 활성 상태만 골라낸다
    private fun activeJobNames(queue: String, prefix: String): List<String> =
        client
            .listJobs {
                it.jobQueue(queue).maxResults(100).filters(
                    KeyValuesPair
                        .builder()
                        .name("JOB_NAME")
                        .values("$prefix*")
                        .build(),
                )
            }.jobSummaryList()
            .filter { it.status() in ACTIVE_STATUSES }
            .map { it.jobName() }
}
