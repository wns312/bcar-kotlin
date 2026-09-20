package jyk.bcar.repository

import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

        assertEquals(withoutDetail, itemToCar(carToItem(withoutDetail)))
        assertEquals(withDetail, itemToCar(carToItem(withDetail)))
    }
}
