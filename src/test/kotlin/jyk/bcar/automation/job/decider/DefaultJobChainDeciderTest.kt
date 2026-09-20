package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.configuration.BatchProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultJobChainDeciderTest {
    private val decider = DefaultJobChainDecider(BatchProperties(detailShards = 3, detailMaxHops = 5, detailMaxIdleHops = 2))

    @Test
    fun draftSubmitsAssign() {
        val next = decider.decide("collect-draft", CollectDraftResult())

        assertEquals(listOf("assign-cars"), next.map { it.jobName })
    }

    @Test
    fun assignFansOutOneDetailJobPerShard() {
        val next = decider.decide("assign-cars", AssignCarsResult(assigned = 10))

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
    fun detailChainCarriesIdleCountAndStopsWhenTooIdle() {
        val oneIdle = decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 40, idleHops = 1))
        assertEquals("1", oneIdle.single().parameters["idle"])

        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 40, idleHops = 2)).isEmpty())
    }

    @Test
    fun detailChainStopsWhenDoneOrStoppedOrFused() {
        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 0)).isEmpty())
        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 40, stopped = true)).isEmpty())
        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 5, remaining = 40)).isEmpty())
    }
}
