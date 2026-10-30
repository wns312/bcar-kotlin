package jyk.bcar.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarLeasePriceTest {
    private fun car(title: String, company: String, price: Int) = Car(
        carNumber = "00가0000",
        title = title,
        company = company,
        detailPageNum = "1",
        agency = "상사",
        seller = "홍길동",
        sellerPhone = "010-0000-0000",
        price = price,
    )

    private fun detail(modelYear: String) = CarDetail(
        category = "대형차",
        displacement = 2000,
        modelYear = modelYear,
        mileage = 10000,
        color = "검정",
        gearBox = "오토",
        fuelType = "휘발유",
        presentationNumber = "제시1",
        hasAccident = "무사고",
        registerNumber = "등록1",
        presentationsDate = "2026-01-01",
        hasSeizure = false,
        hasMortgage = false,
        carCheckSrc = "https://check.example.com/1",
        images = emptyList(),
    )

    /** 2026-09-25 prod에서 실제로 걸러야 했던 매물과 남겨야 했던 매물 */
    @Test
    fun tellLeaseMonthlyPriceFromGenuinelyCheapCar() {
        val cases = listOf(
            Triple(car("지프 랭글러(JL) 2.0 스포츠 4DR S", "지프", 111), "2024-03", true),
            Triple(car("벤츠 GLE클래스(4세대) GLE 400e 4매틱 쿠페", "벤츠", 135), "2023-11", true),
            Triple(car("링컨 에비에이터(2세대) 3.0 V6 AWD 블랙 라벨", "링컨", 123), "2021-08", true),
            // 제네시스는 company가 "현대"로 들어온다 — 제목으로만 가려낼 수 있다
            Triple(car("제네시스 뉴 G80 가솔린 2.5 AWD 스포츠팩", "현대", 118), "2024-04", true),
            Triple(car("제네시스 신형 GV70 2.5T AWD 스포츠", "현대", 105), "2024-10", true),
            // 진짜 싼 최근 매물 — 전부 국산이라 걸리지 않는다
            Triple(car("세보모빌리티(캠시스) 쎄보C EV", "세보모빌리티(캠시스)", 300), "2020-10", false),
            Triple(car("캠프마스터 캠핑트레일러 기타", "캠프마스터", 390), "2021-09", false),
            // 연식이 오래된 싼 수입차는 정상 시세다
            Triple(car("벤츠 C클래스(2세대) C320", "벤츠", 250), "2007-11", false),
            // 최근 수입차라도 제값이면 남긴다
            Triple(car("벤츠 CLE CLE 200 쿠페", "벤츠", 4500), "2024-06", false),
        )

        cases.forEach { (car, modelYear, expected) ->
            assertEquals(expected, car.hasLeasePrice(detail(modelYear), thisYear = 2026), "${car.title} ${car.price}만 $modelYear")
        }
    }

    @Test
    fun keepCarWhenModelYearUnparseable() {
        val car = car("벤츠 GLE클래스 GLE 400e", "벤츠", 135)

        assertEquals(false, car.hasLeasePrice(detail(""), thisYear = 2026))
    }
}
