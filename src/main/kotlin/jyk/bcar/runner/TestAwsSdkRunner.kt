package jyk.bcar.runner

import jyk.bcar.client.AwsBatchClient
import org.springframework.stereotype.Component

@Component
class TestAwsSdkRunner(
    private val awsBatchClient: AwsBatchClient,
) : Runner {
    override suspend fun run() {
        val jobs = awsBatchClient.listJobs("bcar-job-queue")
        jobs.forEach {
            println(it.jobName())
        }
    }
}
