package jyk.bcar.repository

import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.domain.UploadStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DynamoDbCarRepositoryTest {
    @Test
    fun itemRoundTrip() {
        val withoutDetail = Car(
            carNumber = "11가1111",
            title = "현대 유니버스",
            company = "현대",
            detailPageNum = "12345678",
            agency = "상사",
            seller = "홍길동",
            sellerPhone = "010-1111-2222",
            price = 4200,
            isActive = false,
        )
        val withDetail = withoutDetail.copy(
            isActive = true,
            detail = CarDetail(
                category = "대형",
                displacement = 2970,
                modelYear = "2019-03",
                mileage = 123456,
                color = "검정",
                gearBox = "오토",
                fuelType = "경유",
                presentationNumber = "제시123",
                hasAccident = "무사고",
                registerNumber = "등록-77",
                presentationsDate = "2025-02-01",
                hasSeizure = true,
                hasMortgage = false,
                carCheckSrc = "https://check.example.com/report/123",
                images = listOf("/a.jpg", "/b.jpg"),
            ),
        )

        val assigned = withDetail.copy(
            assignedUserId = "user-1",
            assignedAt = Instant.parse("2026-09-20T01:02:03Z"),
            targetSite = "kcr",
            uploadStatus = UploadStatus.UPLOADED,
            uploadedAt = Instant.parse("2026-09-20T02:00:00Z"),
            uploadError = "boom",
            externalId = "ext-9",
            detailError = "no category",
        )

        assertEquals(withoutDetail, itemToCar(carToItem(withoutDetail)))
        assertEquals(withDetail, itemToCar(carToItem(withDetail)))
        assertEquals(assigned, itemToCar(carToItem(assigned)))
    }

    @Test
    fun legacyItemWithoutUploadFieldsReadsAsNone() {
        val legacy = carToItem(
            Car("1", "t", "c", "1", "a", "s", "p", 1),
        ).filterKeys { it != "uploadStatus" }

        assertEquals(UploadStatus.NONE, itemToCar(legacy).uploadStatus)
    }
}
