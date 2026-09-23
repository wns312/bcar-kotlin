package jyk.bcar.automation.job.decider

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
            // 체인 시작점. 이전 launch의 체인이 아직 도는 shard는 제출 측에서 건너뛴다
            is CollectDraftResult -> (0 until batchProperties.detailShards).map { shard ->
                detailRequest(shard = shard, shards = batchProperties.detailShards, hop = 0, idle = 0)
                    .let { it.copy(skipIfActive = it.chainPrefix()) }
            }
            is CollectDetailResult -> when {
                // _control.stopDetail은 파이프라인 전체를 세우는 스위치다
                result.stopped -> emptyList()
                // 잡 하나가 IP 하나 분량만 처리하므로 남은 게 있으면 같은 shard를 새 잡(=새 IP)으로 이어간다
                result.remaining > 0 &&
                    result.hop < batchProperties.detailMaxHops &&
                    result.idleHops < batchProperties.detailMaxIdleHops ->
                    listOf(detailRequest(shard = result.shard, shards = result.shards, hop = result.hop + 1, idle = result.idleHops))
                // shard마다 끝나는 시점이 달라 여러 번 제출될 수 있다. assign은 멱등이고 동시 실행만 막는다
                else -> listOf(NextJobRequest(jobName = "assign-cars", skipIfActive = "assign-cars"))
            }
            else -> emptyList()
        }
    }

    private fun detailRequest(shard: Int, shards: Int, hop: Int, idle: Int) =
        NextJobRequest(
            jobName = "collect-detail",
            parameters = mapOf("shards" to "$shards", "shard" to "$shard", "hop" to "$hop", "idle" to "$idle"),
        )

    // AwsBatchJobSubmitter의 잡 이름 규칙(jobName-key1value1-key2value2…)에서 hop 앞까지
    private fun NextJobRequest.chainPrefix() =
        "$jobName-shards${parameters["shards"]}-shard${parameters["shard"]}-"
}
