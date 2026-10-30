package jyk.bcar.automation.job

import jyk.bcar.domain.CarDetail
import jyk.bcar.domain.UploadStatus
import jyk.bcar.policy.DetailCollectionPolicy
import jyk.bcar.port.DetailCarSource
import jyk.bcar.port.TakeoverListing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CollectDetailJobTest {
    @Test
    fun fetchBlockPersistsAlreadyParsedCarsAndLeavesRemainingForNextHop() = runTest {
        val cars = RecordingCars(listOf(draftCar("one"), draftCar("blocked"), draftCar("later")))
        val control = RecordingControl()
        val source = FakeSource(fetch = { if (it == "blocked") error("connection reset") else it.toByteArray() })
        val job = CollectDetailJob(cars, control, DetailCollectionOptions(shards = 3, shard = 1, hop = 2), DetailCollectionPolicy(), source)

        val result = job.execute()

        assertEquals(listOf(1 to 3), cars.scans)
        assertEquals(listOf("one", "blocked"), source.fetched)
        assertEquals(listOf("one"), cars.saved.map { it.carNumber })
        assertEquals(carDetail(), cars.saved.single().detail)
        assertEquals(2, result.remaining)
        assertEquals(0, result.idleHops)
        assertFalse(result.chainEnded)
        assertEquals(0, control.completed)
    }

    @Test
    fun malformedAndTakeoverPagesUseDifferentDeactivationRulesAndFinishChain() = runTest {
        val uploaded = draftCar("excluded").copy(assignedUserId = targetUser.id, uploadStatus = UploadStatus.UPLOADED)
        val cars = RecordingCars(listOf(draftCar("malformed"), uploaded, draftCar("good")))
        val control = RecordingControl(doneCount = 3)
        val source = FakeSource(parse = {
            when (it.decodeToString()) {
                "malformed" -> throw IllegalArgumentException("missing detail box")
                "excluded" -> throw TakeoverListing()
                else -> carDetail()
            }
        })

        val result = CollectDetailJob(cars, control, DetailCollectionOptions(shards = 3), DetailCollectionPolicy(), source).execute()

        val malformed = cars.saved.single { it.carNumber == "malformed" }
        assertFalse(malformed.isActive)
        assertEquals("missing detail box", malformed.detailError)
        val excluded = cars.saved.single { it.carNumber == "excluded" }
        assertFalse(excluded.isActive)
        assertEquals(UploadStatus.NEEDS_REMOVAL, excluded.uploadStatus)
        assertEquals(targetUser.id, excluded.assignedUserId)
        assertEquals("리스·렌트 승계 매물", excluded.detailError)
        assertTrue(cars.saved.single { it.carNumber == "good" }.isActive)
        assertEquals(0, result.remaining)
        assertTrue(result.chainEnded)
        assertEquals(3, result.chainsDone)
        assertEquals(1, control.completed)
    }

    @Test
    fun consecutiveIdleHopFuseEndsBlockedChainExactlyOnce() = runTest {
        val control = RecordingControl(doneCount = 2)
        val cars = RecordingCars(listOf(draftCar("blocked")))
        val source = FakeSource(fetch = { error("timeout") })

        val result = CollectDetailJob(
            cars,
            control,
            DetailCollectionOptions(idle = 1),
            DetailCollectionPolicy(maxIdleHops = 2),
            source,
        ).execute()

        assertEquals(1, result.remaining)
        assertEquals(2, result.idleHops)
        assertTrue(result.chainEnded)
        assertEquals(2, result.chainsDone)
        assertEquals(1, control.completed)
        assertTrue(cars.saved.isEmpty())
    }

    @Test
    fun maxHopFuseEndsChainEvenWhenBlockedWorkRemains() = runTest {
        val control = RecordingControl()
        val source = FakeSource(fetch = { error("blocked") })

        val result = CollectDetailJob(
            RecordingCars(listOf(draftCar("one"))),
            control,
            DetailCollectionOptions(hop = 4),
            DetailCollectionPolicy(maxHops = 4),
            source,
        ).execute()

        assertTrue(result.chainEnded)
        assertEquals(1, result.remaining)
        assertEquals(1, control.completed)
    }

    @Test
    fun stopFlagReturnsWithoutReadingCarsOrAdvancingCompletionCounter() = runTest {
        val cars = RecordingCars(listOf(draftCar("one")))
        val control = RecordingControl(stopped = true)
        val source = FakeSource()

        val result = CollectDetailJob(cars, control, DetailCollectionOptions(), DetailCollectionPolicy(), source).execute()

        assertTrue(result.stopped)
        assertTrue(cars.scans.isEmpty())
        assertTrue(source.fetched.isEmpty())
        assertEquals(0, control.completed)
    }

    @Test
    fun cancellationIsNotTreatedAsIpBlockingOrChainCompletion() = runTest {
        val cancelled = CancellationException("shutdown")
        val cars = RecordingCars(listOf(draftCar("one")))
        val control = RecordingControl()
        val source = FakeSource(fetch = { throw cancelled })

        val thrown = assertFailsWith<CancellationException> {
            CollectDetailJob(cars, control, DetailCollectionOptions(), DetailCollectionPolicy(), source).execute()
        }

        assertEquals(cancelled.message, thrown.message)
        assertTrue(cars.writes.isEmpty())
        assertEquals(0, control.completed)
    }

    @Test
    fun cancellationWhileParsingNeverDeactivatesCar() = runTest {
        val cancelled = CancellationException("parse cancelled")
        val cars = RecordingCars(listOf(draftCar("one")))
        val control = RecordingControl()
        val source = FakeSource(parse = { throw cancelled })

        val thrown = assertFailsWith<CancellationException> {
            CollectDetailJob(cars, control, DetailCollectionOptions(), DetailCollectionPolicy(), source).execute()
        }

        assertEquals(cancelled.message, thrown.message)
        assertTrue(cars.writes.isEmpty())
        assertEquals(0, control.completed)
    }

    private class FakeSource(
        private val fetch: (String) -> ByteArray = { it.toByteArray() },
        private val parse: (ByteArray) -> CarDetail = { carDetail() },
    ) : DetailCarSource {
        val fetched = mutableListOf<String>()

        override suspend fun fetch(detailPageNumber: String): ByteArray {
            fetched += detailPageNumber
            return fetch.invoke(detailPageNumber)
        }

        override suspend fun parse(bytes: ByteArray): CarDetail = parse.invoke(bytes)
    }
}
