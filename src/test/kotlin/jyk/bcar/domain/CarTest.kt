package jyk.bcar.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarTest {
    private fun car(
        number: String,
        price: Int = 1000,
        isActive: Boolean = true,
        detail: CarDetail? = null,
        assignedUserId: String? = null,
        uploadStatus: UploadStatus = UploadStatus.NONE,
    ) = Car(
        carNumber = number,
        title = "현대 유니버스",
        company = "현대",
        detailPageNum = "1",
        agency = "상사",
        seller = "홍길동",
        sellerPhone = "010-0000-0000",
        price = price,
        isActive = isActive,
        detail = detail,
        assignedUserId = assignedUserId,
        uploadStatus = uploadStatus,
    )

    private val detail = CarDetail(
        category = "대형",
        displacement = 2970,
        modelYear = "2019-03",
        mileage = 1,
        color = "검정",
        gearBox = "오토",
        fuelType = "경유",
        presentationNumber = "p",
        hasAccident = "무사고",
        registerNumber = "r",
        presentationsDate = "2025-02-01",
        hasSeizure = false,
        hasMortgage = false,
        carCheckSrc = "",
        images = emptyList(),
    )

    @Test
    fun reconcileReturnsOnlyChangedCars() {
        val existing = listOf(
            car("same", detail = detail),
            car("priceChanged", price = 1000, detail = detail),
            car("gone"),
            car("alreadyInactive", isActive = false),
            car("relisted", isActive = false, detail = detail),
        )
        val collected = listOf(
            car("same"),
            car("priceChanged", price = 900),
            car("new"),
            car("relisted"),
        )

        val changes = Car.reconcile(existing, collected).associateBy { it.carNumber }

        assertEquals(setOf("priceChanged", "new", "gone", "relisted"), changes.keys)
        assertEquals(detail, changes.getValue("priceChanged").detail)
        assertEquals(900, changes.getValue("priceChanged").price)
        assertEquals(false, changes.getValue("gone").isActive)
        assertEquals(true, changes.getValue("relisted").isActive)
        assertEquals(null, changes.getValue("relisted").detail)
    }

    @Test
    fun reconcileMarksUploadedForRemovalAndReleasesTheRest() {
        val existing = listOf(
            car("kept", assignedUserId = "u1", uploadStatus = UploadStatus.PENDING),
            car("goneUploaded", assignedUserId = "u1", uploadStatus = UploadStatus.UPLOADED),
            car("gonePending", assignedUserId = "u1", uploadStatus = UploadStatus.PENDING),
        )
        val collected = listOf(car("kept", price = 900))

        val changes = Car.reconcile(existing, collected).associateBy { it.carNumber }

        assertEquals("u1", changes.getValue("kept").assignedUserId)
        assertEquals(UploadStatus.PENDING, changes.getValue("kept").uploadStatus)
        assertEquals(UploadStatus.NEEDS_REMOVAL, changes.getValue("goneUploaded").uploadStatus)
        assertEquals("u1", changes.getValue("goneUploaded").assignedUserId)
        // 안 올라간 차는 소스에서 사라진 순간 쿼터를 돌려준다
        assertEquals(UploadStatus.NONE, changes.getValue("gonePending").uploadStatus)
        assertEquals(null, changes.getValue("gonePending").assignedUserId)
    }

    @Test
    fun syncTakesSiteAsSourceOfTruth() {
        val now = java.time.Instant.EPOCH

        fun sync(status: UploadStatus, onSite: Boolean) =
            car("x", assignedUserId = "u1", uploadStatus = status).syncedWith(onSite, now)

        assertEquals(UploadStatus.UPLOADED, sync(UploadStatus.PENDING, onSite = true)?.uploadStatus)
        assertEquals(UploadStatus.UPLOADED, sync(UploadStatus.FAILED, onSite = true)?.uploadStatus)
        assertEquals(null, sync(UploadStatus.UPLOADED, onSite = true))

        // 관리자가 손으로 내렸거나 죽은 잡이 남긴 UPLOADING 유령은 다시 올릴 대상이 된다
        assertEquals(UploadStatus.PENDING, sync(UploadStatus.UPLOADED, onSite = false)?.uploadStatus)
        assertEquals(UploadStatus.PENDING, sync(UploadStatus.UPLOADING, onSite = false)?.uploadStatus)
        assertEquals(null, sync(UploadStatus.PENDING, onSite = false))
        assertEquals(null, sync(UploadStatus.FAILED, onSite = false))

        // 내리기로 한 차는 사이트에서 사라진 게 확인되면 할당을 비운다
        val removed = sync(UploadStatus.NEEDS_REMOVAL, onSite = false)
        assertEquals(UploadStatus.NONE, removed?.uploadStatus)
        assertEquals(null, removed?.assignedUserId)
    }

    @Test
    fun releaseKeepsAssignmentOnlyWhenUploaded() {
        val uploaded = car("x", assignedUserId = "u1", uploadStatus = UploadStatus.UPLOADED).release()
        val pending = car("y", assignedUserId = "u1", uploadStatus = UploadStatus.PENDING).release()

        assertEquals("u1", uploaded.assignedUserId)
        assertEquals(UploadStatus.NEEDS_REMOVAL, uploaded.uploadStatus)
        assertEquals(null, pending.assignedUserId)
        assertEquals(UploadStatus.NONE, pending.uploadStatus)
    }
}
