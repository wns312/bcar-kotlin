package jyk.bcar.automation.job

import jyk.bcar.domain.UploadStatus
import jyk.bcar.port.DraftCarSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CollectDraftJobTest {
    @Test
    fun reconcilesSnapshotBeforeResettingDetailChainCounter() = runTest {
        val existing = draftCar("existing").copy(detail = carDetail(), assignedUserId = targetUser.id, uploadStatus = UploadStatus.UPLOADED)
        val missing = draftCar("missing").copy(assignedUserId = targetUser.id, uploadStatus = UploadStatus.UPLOADED)
        val unchanged = draftCar("unchanged")
        val events = mutableListOf<String>()
        val cars = RecordingCars(listOf(existing, missing, unchanged)) { events += "saved" }
        val control = RecordingControl(onReset = { events += "reset" })
        val source = DraftCarSource { listOf(draftCar("existing").copy(price = 1700), draftCar("new"), unchanged) }

        val result = CollectDraftJob(source, cars, control).execute()

        assertTrue(result.success)
        assertEquals(listOf("saved", "reset"), events)
        assertEquals(1, control.resets)
        assertEquals(setOf("existing", "new", "missing"), cars.saved.map { it.carNumber }.toSet())
        val updated = cars.saved.single { it.carNumber == "existing" }
        assertEquals(1700, updated.price)
        assertEquals(existing.detail, updated.detail)
        assertEquals(existing.assignedUserId, updated.assignedUserId)
        assertEquals(UploadStatus.UPLOADED, updated.uploadStatus)
        val removed = cars.saved.single { it.carNumber == "missing" }
        assertFalse(removed.isActive)
        assertEquals(UploadStatus.NEEDS_REMOVAL, removed.uploadStatus)
    }

    @Test
    fun sourceFailureDoesNotSaveOrResetPipeline() = runTest {
        val cars = RecordingCars(listOf(draftCar("one")))
        val control = RecordingControl()
        val job = CollectDraftJob(DraftCarSource { error("login failed") }, cars, control)

        assertFailsWith<IllegalStateException> { job.execute() }

        assertTrue(cars.writes.isEmpty())
        assertTrue(cars.scans.isEmpty())
        assertEquals(0, control.resets)
    }

    @Test
    fun failedPersistenceDoesNotResetCounterForUncommittedSnapshot() = runTest {
        val cars = RecordingCars(emptyList()) { error("database unavailable") }
        val control = RecordingControl()
        val job = CollectDraftJob(DraftCarSource { listOf(draftCar("new")) }, cars, control)

        assertFailsWith<IllegalStateException> { job.execute() }

        assertEquals(0, control.resets)
    }
}
