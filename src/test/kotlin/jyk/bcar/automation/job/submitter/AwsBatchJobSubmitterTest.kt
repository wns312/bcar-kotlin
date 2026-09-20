package jyk.bcar.automation.job.submitter

import jyk.bcar.automation.job.decider.NextJobRequest
import jyk.bcar.configuration.BatchProperties
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import software.amazon.awssdk.services.batch.BatchClient
import software.amazon.awssdk.services.batch.model.JobStatus
import software.amazon.awssdk.services.batch.model.JobSummary
import software.amazon.awssdk.services.batch.model.ListJobsRequest
import software.amazon.awssdk.services.batch.model.ListJobsResponse
import software.amazon.awssdk.services.batch.model.SubmitJobRequest
import software.amazon.awssdk.services.batch.model.SubmitJobResponse
import java.util.function.Consumer

class AwsBatchJobSubmitterTest {
    private val properties = BatchProperties(jobs = mapOf("collect-detail" to BatchProperties.Target("q", "def")))
    private val client = mock(BatchClient::class.java)
    private val submitter = AwsBatchJobSubmitter(properties, client)
    private val hop0 = NextJobRequest(
        jobName = "collect-detail",
        parameters = mapOf("shards" to "16", "shard" to "3", "hop" to "0", "idle" to "0"),
        skipIfActive = "collect-detail-shards16-shard3-",
    )

    @Test
    fun skipsWhenSameShardChainIsActive() = runTest {
        `when`(client.listJobs(any<Consumer<ListJobsRequest.Builder>>())).thenReturn(
            ListJobsResponse
                .builder()
                .jobSummaryList(
                    JobSummary
                        .builder()
                        .jobName("collect-detail-shards16-shard3-hop7")
                        .status(JobStatus.RUNNING)
                        .build(),
                ).build(),
        )

        val submitted = submitter.submit(hop0)

        assertEquals(null, submitted.jobId)
        verify(client, never()).submitJob(any<Consumer<SubmitJobRequest.Builder>>())
    }

    @Test
    fun submitsWhenOnlyFinishedJobsExist() = runTest {
        `when`(client.listJobs(any<Consumer<ListJobsRequest.Builder>>())).thenReturn(
            ListJobsResponse
                .builder()
                .jobSummaryList(
                    JobSummary
                        .builder()
                        .jobName("collect-detail-shards16-shard3-hop7")
                        .status(JobStatus.SUCCEEDED)
                        .build(),
                ).build(),
        )
        `when`(client.submitJob(any<Consumer<SubmitJobRequest.Builder>>())).thenReturn(SubmitJobResponse.builder().jobId("id-1").build())

        assertEquals("id-1", submitter.submit(hop0).jobId)
    }
}
