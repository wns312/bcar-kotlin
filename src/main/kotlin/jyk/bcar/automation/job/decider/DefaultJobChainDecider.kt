package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.job.result.JobResult
import jyk.bcar.configuration.BatchProperties
import org.springframework.stereotype.Component

@Component
class DefaultJobChainDecider(
    private val batchProperties: BatchProperties,
) : JobChainDecider {
    override fun decide(
        currentJobName: String,
        result: JobResult,
    ): List<NextJobRequest> {
        if (!result.success) return emptyList()

        return when (result) {
            is CollectDraftResult -> (0 until batchProperties.detailShards).map { shard ->
                detailRequest(shard = shard, shards = batchProperties.detailShards, hop = 0)
            }
            // 잡 하나가 IP 하나 분량(~15건)만 처리하므로 남은 게 있으면 같은 shard를 새 잡(=새 IP)으로 이어간다
            is CollectDetailResult ->
                if (!result.stopped && result.remaining > 0 && result.hop < batchProperties.detailMaxHops) {
                    listOf(detailRequest(shard = result.shard, shards = result.shards, hop = result.hop + 1))
                } else {
                    emptyList()
                }
            is AssignCarsResult -> result.carIds.map {
                NextJobRequest(
                    jobName = "sync-and-upload",
                    parameters = mapOf("carId" to it),
                )
            }
            else -> emptyList()
        }
    }

    private fun detailRequest(shard: Int, shards: Int, hop: Int) =
        NextJobRequest(
            jobName = "collect-detail",
            parameters = mapOf("shards" to "$shards", "shard" to "$shard", "hop" to "$hop"),
        )
}
