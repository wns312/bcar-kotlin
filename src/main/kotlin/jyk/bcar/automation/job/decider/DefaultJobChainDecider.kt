package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.job.result.JobResult
import jyk.bcar.automation.job.result.SyncUploadResult
import jyk.bcar.configuration.BatchProperties
import org.springframework.stereotype.Component

@Component
class DefaultJobChainDecider(
    private val batchProperties: BatchProperties,
) : JobChainDecider {
    override fun decide(result: JobResult): List<NextJobRequest> {
        // 유저 하나가 실패해도 남은 유저는 돌아야 한다. 실패 자체는 Batch 잡 상태로 남는다
        if (result is SyncUploadResult) return syncUploadRequest(result.nextUserId)
        if (!result.success) return emptyList()

        return when (result) {
            // 체인 시작점. 이전 launch의 체인이 아직 도는 shard는 제출 측에서 건너뛴다
            is CollectDraftResult -> (0 until batchProperties.detailShards).map { shard ->
                val chain = detailChain(shard = shard, shards = batchProperties.detailShards)
                detailRequest(chain, hop = 0, idle = 0)
                    .copy(skipIfActive = NextJobRequest(DETAIL, chain).batchJobName() + "-")
            }
            is CollectDetailResult -> when {
                // _control.stopDetail은 파이프라인 전체를 세우는 스위치다
                result.stopped -> emptyList()
                // 잡 하나가 IP 하나 분량만 처리하므로 남은 게 있으면 같은 shard를 새 잡(=새 IP)으로 이어간다
                !result.chainEnded ->
                    listOf(detailRequest(detailChain(result.shard, result.shards), hop = result.hop + 1, idle = result.idleHops))
                // 마지막으로 끝난 체인만 제출한다. 앞서 끝난 체인들이 제출하면 아직 수집 중인 shard의 결과가 빠진 채로 할당된다
                result.chainsDone >= result.shards -> listOf(NextJobRequest(jobName = "assign-cars", skipIfActive = "assign-cars"))
                else -> emptyList()
            }
            // 업로드 체인 시작. 사이트 부하 때문에 유저 한 명씩 순서대로 돈다
            is AssignCarsResult -> syncUploadRequest(result.uploadUserIds.firstOrNull(), skipIfActive = SYNC_UPLOAD_PREFIX)
            else -> emptyList()
        }
    }

    private fun syncUploadRequest(userId: String?, skipIfActive: String? = null) =
        userId?.let {
            listOf(NextJobRequest(jobName = SYNC_UPLOAD, parameters = mapOf("user" to it), skipIfActive = skipIfActive))
        } ?: emptyList()

    private fun detailChain(shard: Int, shards: Int) = mapOf("shards" to "$shards", "shard" to "$shard")

    // 체인 파라미터가 앞에 와야 잡 이름이 체인 접두사로 시작한다
    private fun detailRequest(chain: Map<String, String>, hop: Int, idle: Int) =
        NextJobRequest(jobName = DETAIL, parameters = chain + mapOf("hop" to "$hop", "idle" to "$idle"))

    private companion object {
        const val DETAIL = "collect-detail"
        const val SYNC_UPLOAD = "sync-upload"
        val SYNC_UPLOAD_PREFIX = NextJobRequest(SYNC_UPLOAD).batchJobName() + "-"
    }
}
