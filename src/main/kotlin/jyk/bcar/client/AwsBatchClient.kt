package jyk.bcar.client

import org.springframework.stereotype.Component
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.batch.BatchAsyncClient
import software.amazon.awssdk.services.batch.model.JobStatus
import software.amazon.awssdk.services.batch.model.JobSummary
import software.amazon.awssdk.services.batch.model.ListJobsRequest
import java.time.Duration

@Component
class AwsBatchClient(
    private val batchAsyncClient: BatchAsyncClient = getDefaultAsyncClient(),
) {
    companion object {
        fun getDefaultAsyncClient(): BatchAsyncClient =
            BatchAsyncClient
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .httpClient(
                    NettyNioAsyncHttpClient
                        .builder()
                        .maxConcurrency(100) // Increase max concurrency to handle more simultaneous connections.
                        .connectionTimeout(Duration.ofSeconds(60)) // Set the connection timeout.
                        .readTimeout(Duration.ofSeconds(60)) // Set the read timeout.
                        .writeTimeout(Duration.ofSeconds(60)) // Set the write timeout.
                        .build(),
                ).overrideConfiguration(
                    ClientOverrideConfiguration
                        .builder()
                        .apiCallTimeout(Duration.ofMinutes(2)) // Set the overall API call timeout.
                        .apiCallAttemptTimeout(Duration.ofSeconds(90)) // Set the individual call attempt timeout.
                        .retryStrategy(AwsRetryStrategy.adaptiveRetryStrategy())
                        .build(),
                ).build()
    }

    suspend fun listJobs(jobQueue: String): List<JobSummary> {
        val request =
            ListJobsRequest
                .builder()
                .jobQueue(jobQueue)
                .jobStatus(JobStatus.SUCCEEDED)
                .maxResults(100)
                .build()
        val listJobsPaginator = batchAsyncClient.listJobsPaginator(request)
        val jobSummaries = mutableListOf<JobSummary>()

        val future =
            listJobsPaginator.subscribe { response ->
                jobSummaries.addAll(response.jobSummaryList())
            }

        future.join()

        return jobSummaries
    }
}
