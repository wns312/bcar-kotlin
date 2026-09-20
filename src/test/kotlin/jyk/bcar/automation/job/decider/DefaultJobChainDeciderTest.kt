package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.CollectDetailResult
import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.configuration.BatchProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultJobChainDeciderTest {
    private val decider = DefaultJobChainDecider(BatchProperties(detailShards = 3, detailMaxHops = 5))

    @Test
    fun draftFansOutOneDetailJobPerShard() {
        val next = decider.decide("collect-draft", CollectDraftResult())

        assertEquals(listOf("0", "1", "2"), next.map { it.parameters.getValue("shard") })
        assertTrue(next.all { it.jobName == "collect-detail" && it.parameters["shards"] == "3" && it.parameters["hop"] == "0" })
    }

    @Test
    fun detailChainsSameShardWhileWorkRemains() {
        val next = decider.decide("collect-detail", CollectDetailResult(shard = 2, shards = 3, hop = 1, remaining = 40))

        assertEquals(1, next.size)
        assertEquals(mapOf("shards" to "3", "shard" to "2", "hop" to "2"), next.single().parameters)
    }

    @Test
    fun detailChainStopsWhenDoneOrStoppedOrFused() {
        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 0)).isEmpty())
        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 1, remaining = 40, stopped = true)).isEmpty())
        assertTrue(decider.decide("collect-detail", CollectDetailResult(0, 3, hop = 5, remaining = 40)).isEmpty())
    }
}
