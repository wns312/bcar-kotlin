package jyk.bcar.automation.job

import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadSource
import jyk.bcar.domain.UploadStatus
import jyk.bcar.policy.UploadPolicy
import jyk.bcar.port.SiteListings
import jyk.bcar.port.TargetCarSession
import jyk.bcar.port.TargetCarSite
import jyk.bcar.port.UploadQuotaExhausted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SyncUploadJobTest {
    private fun pending(number: String) = draftCar(number).copy(
        detail = carDetail(),
        assignedUserId = targetUser.id,
        uploadStatus = UploadStatus.PENDING,
    )

    private fun job(
        cars: RecordingCars,
        site: FakeSite,
        options: SyncUploadOptions = SyncUploadOptions(targetUser.id),
    ) = SyncUploadJob(
        site,
        FixedUsers(),
        cars,
        FixedCategories(),
        UploadPolicy("설명", listOf(UploadPolicy.Margin(2000, 40, 60))),
        options,
    )

    @Test
    fun uploadsInOneSessionAndPersistsEachTransitionBeforeContinuing() = runTest {
        val events = mutableListOf<String>()
        val cars = RecordingCars(listOf(pending("one"), pending("two"))) { saved ->
            events += saved.map { "${it.carNumber}:${it.uploadStatus}" }
        }
        val site = FakeSite(onUpload = { events += "upload:${it.source.car.carNumber}" })

        val result = job(cars, site).execute()

        assertTrue(result.success)
        assertEquals(2, result.uploaded)
        assertEquals(nextTargetUser.id, result.nextUserId)
        assertEquals(1, site.sessions)
        assertEquals(listOf("one", "two"), site.uploads.map { it.source.car.carNumber })
        assertTrue(site.uploads.all { it.price == 1540 && it.comment == "설명" && it.submit })
        assertEquals(
            listOf("one:UPLOADING", "upload:one", "one:UPLOADED", "two:UPLOADING", "upload:two", "two:UPLOADED"),
            events,
        )
    }

    @Test
    fun individualUploadFailureIsRecordedAndNextCarStillRuns() = runTest {
        val cars = RecordingCars(listOf(pending("bad"), pending("good")))
        val site = FakeSite(onUpload = { if (it.source.car.carNumber == "bad") error("form rejected") })

        val result = job(cars, site).execute()

        assertTrue(result.success)
        assertEquals(1, result.failed)
        assertEquals(1, result.uploaded)
        val failed = cars.saved.last { it.carNumber == "bad" }
        assertEquals(UploadStatus.FAILED, failed.uploadStatus)
        assertEquals(1, failed.uploadAttempts)
        assertEquals("form rejected", failed.uploadError)
        assertEquals(UploadStatus.UPLOADED, cars.saved.last().uploadStatus)
    }

    @Test
    fun exhaustedQuotaRestoresPendingWithoutSpendingAttemptAndStopsRemainingCars() = runTest {
        val first = pending("one").copy(uploadStatus = UploadStatus.FAILED, uploadAttempts = 2)
        val cars = RecordingCars(listOf(first, pending("two")))
        val site = FakeSite(onUpload = { throw UploadQuotaExhausted("no free slots") })

        val result = job(cars, site).execute()

        assertFalse(result.success)
        assertEquals(nextTargetUser.id, result.nextUserId)
        assertEquals(listOf("one"), site.uploads.map { it.source.car.carNumber })
        assertEquals(listOf(UploadStatus.UPLOADING, UploadStatus.PENDING), cars.saved.map { it.uploadStatus })
        assertEquals(2, cars.saved.last().uploadAttempts)
        assertEquals(0, result.failed)
        assertEquals(0, result.uploaded)
    }

    @Test
    fun cancellationPropagatesAndNeverBecomesAnUploadFailure() = runTest {
        val cancelled = CancellationException("shutdown")
        val cars = RecordingCars(listOf(pending("one"), pending("two")))
        val site = FakeSite(onUpload = { throw cancelled })

        val thrown = assertFailsWith<CancellationException> { job(cars, site).execute() }

        assertEquals(cancelled.message, thrown.message)
        assertEquals(listOf(UploadStatus.UPLOADING), cars.saved.map { it.uploadStatus })
        assertEquals(1, site.uploads.size)
    }

    @Test
    fun previewForwardsFlagsAndLimitWithoutPersistingUploadTransitions() = runTest {
        val cars = RecordingCars(listOf(pending("one"), pending("two")))
        val site = FakeSite()

        val result = job(cars, site, SyncUploadOptions(targetUser.id, delete = false, submit = false, limit = 1)).execute()

        assertTrue(result.success)
        assertEquals(false, site.delete)
        assertEquals(1, site.uploads.size)
        assertFalse(site.uploads.single().submit)
        assertTrue(cars.saved.isEmpty())
    }

    @Test
    fun syncReleasesRemovedAssignmentsAndKeepsPaidListingsOutOfUploads() = runTest {
        val cars = RecordingCars(
            listOf(
                pending("existing"),
                pending("remove").copy(uploadStatus = UploadStatus.NEEDS_REMOVAL),
                pending("paid"),
            ),
        )
        val site = FakeSite(listings = SiteListings(setOf("existing"), listOf("remove"), setOf("paid")))

        val result = job(cars, site).execute()

        assertEquals(setOf("existing", "paid"), site.expected)
        assertTrue(site.uploads.isEmpty())
        assertEquals(1, result.released)
        assertEquals(1, result.removed)
        assertEquals(1, result.onSite)
        assertEquals(null, cars.saved.single { it.carNumber == "remove" }.assignedUserId)
        assertTrue(cars.saved.single { it.carNumber == "paid" }.paid)
    }

    @Test
    fun unknownUserFailsBeforeOpeningSiteSession() = runTest {
        val site = FakeSite()

        assertFailsWith<IllegalStateException> {
            job(RecordingCars(emptyList()), site, SyncUploadOptions("missing")).execute()
        }

        assertEquals(0, site.sessions)
    }

    @Test
    fun siteFailureReturnsFailedResultWithNextUserIntact() = runTest {
        val site = FakeSite(onSync = { error("site unavailable") })
        val cars = RecordingCars(listOf(pending("one")))

        val result = job(cars, site).execute()

        assertFalse(result.success)
        assertEquals(nextTargetUser.id, result.nextUserId)
        assertTrue(cars.saved.isEmpty())
    }

    private data class UploadCall(
        val source: UploadSource,
        val price: Int,
        val comment: String,
        val submit: Boolean,
    )

    private class FakeSite(
        private val listings: SiteListings = SiteListings(emptySet(), emptyList()),
        private val onUpload: (UploadCall) -> Unit = {},
        private val onSync: () -> Unit = {},
    ) : TargetCarSite {
        var sessions = 0
        var expected: Set<String>? = null
        var delete: Boolean? = null
        val uploads = mutableListOf<UploadCall>()

        override suspend fun <T> withSession(user: TargetAdminUser, block: suspend (TargetCarSession) -> T): T {
            assertEquals(targetUser, user)
            sessions++
            return block(object : TargetCarSession {
                override suspend fun synchronize(expected: Set<String>, delete: Boolean): SiteListings {
                    this@FakeSite.expected = expected
                    this@FakeSite.delete = delete
                    onSync()
                    return listings
                }

                override suspend fun upload(source: UploadSource, price: Int, comment: String, submit: Boolean) {
                    val call = UploadCall(source, price, comment, submit)
                    uploads += call
                    onUpload(call)
                }
            })
        }
    }
}
