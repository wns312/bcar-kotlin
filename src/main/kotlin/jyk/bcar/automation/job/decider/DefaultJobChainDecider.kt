package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.job.result.JobResult
import org.springframework.stereotype.Component

@Component
class DefaultJobChainDecider : JobChainDecider {
    override fun decide(
        currentJobName: String,
        result: JobResult,
    ): List<NextJobRequest> {
        if (!result.success) return emptyList()

        return when (result) {
            is CollectDraftResult ->
                listOf(
                    NextJobRequest(
                        jobName = "collect-detail",
                        parameters = mapOf("draftIds" to result.draftIds.joinToString(",")),
                    ),
                )
            is AssignCarsResult ->
                result.carIds.map {
                    NextJobRequest(
                        jobName = "sync-and-upload",
                        parameters = mapOf("carId" to it),
                    )
                }
            else -> emptyList()
        }
    }
}
