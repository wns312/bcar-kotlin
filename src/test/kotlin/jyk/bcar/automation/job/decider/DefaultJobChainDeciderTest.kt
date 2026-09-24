package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.automation.job.result.SyncUploadResult
import jyk.bcar.configuration.BatchProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultJobChainDeciderTest {
    private val decider = DefaultJobChainDecider(BatchProperties(detailShards = 3, detailMaxHops = 5, detailMaxIdleHops = 2))

    @Test
    fun draftFansOutOneDetailJobPerShard() {
        val next = decider.decide("collect-draft", CollectDraftResult())

        assertEquals(listOf("0", "1", "2"), next.map { it.parameters.getValue("shard") })
        assertTrue(next.all { it.jobName == "collect-detail" && it.parameters["shards"] == "3" && it.parameters["hop"] == "0" })
        assertEquals("collect-detail-shards3-shard1-", next[1].skipIfActive)
    }

    @Test
    fun detailChainsSameShardWhileWorkRemains() {
        val next = decider.decide("collect-detail", CollectDetailResult(shard = 2, shards = 3, hop = 1, remaining = 40))

        assertEquals(1, next.size)
        assertEquals(mapOf("shards" to "3", "shard" to "2", "hop" to "2", "idle" to "0"), next.single().parameters)
        assertEquals(null, next.single().skipIfActive)
    }

    @Test
    fun detailChainCarriesIdleCount() {
        val oneIdle = decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 40, idleHops = 1))

        assertEquals("1", oneIdle.single().parameters["idle"])
    }

    @Test
    fun onlyTheLastFinishedChainSubmitsAssign() {
        val notLast = decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 0, chainEnded = true, chainsDone = 2))
        assertTrue(notLast.isEmpty())

        val last = decider
            .decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 0, chainEnded = true, chainsDone = 3))
            .single()
        assertEquals("assign-cars", last.jobName)
        assertEquals("assign-cars", last.skipIfActive)
    }

    @Test
    fun stopFlagEndsThePipeline() {
        val stopped = CollectDetailResult(0, 3, hop = 1, remaining = 40, stopped = true, chainEnded = true, chainsDone = 3)

        assertTrue(decider.decide("collect-detail", stopped).isEmpty())
    }

    @Test
    fun assignStartsUploadChainWithTheFirstUser() {
        val next = decider.decide("assign-cars", AssignCarsResult(assigned = 10, uploadUserIds = listOf("u1", "u2")))

        assertEquals("sync-upload", next.single().jobName)
        assertEquals(mapOf("user" to "u1"), next.single().parameters)
        assertEquals("sync-upload-", next.single().skipIfActive)
    }

    @Test
    fun uploadChainWalksUsersOneAtATimeThenStops() {
        val next = decider.decide("sync-upload", SyncUploadResult(userId = "u1", nextUserId = "u2"))

        assertEquals(mapOf("user" to "u2"), next.single().parameters)
        // 체인 중간 제출은 자기 자신이 아직 돌고 있으므로 skipIfActive를 걸지 않는다
        assertEquals(null, next.single().skipIfActive)
        assertEquals(emptyList<NextJobRequest>(), decider.decide("sync-upload", SyncUploadResult(userId = "u2")))

        // 유저 하나가 실패해도 남은 유저는 돈다
        val afterFailure = decider.decide("sync-upload", SyncUploadResult(userId = "u1", nextUserId = "u2", success = false))
        assertEquals(mapOf("user" to "u2"), afterFailure.single().parameters)
        assertEquals(emptyList<NextJobRequest>(), decider.decide("assign-cars", AssignCarsResult(assigned = 0)))
    }
}
