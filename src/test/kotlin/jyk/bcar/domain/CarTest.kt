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
    fun reconcileKeepsAssignmentAndMarksUploadedForRemoval() {
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
        assertEquals(UploadStatus.PENDING, changes.getValue("gonePending").uploadStatus)
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
