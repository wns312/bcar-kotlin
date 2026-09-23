package jyk.bcar.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarCategoryTest {
    private fun car(company: String, title: String, price: Int) = Car("1", title, company, "1", "a", "s", "p", price)

    @Test
    fun classifiesByPriority() {
        assertEquals(CarCategory.IMPORTED, CarCategory.of(car("벤츠", "벤츠 E클래스", 900)))
        assertEquals(CarCategory.CARGO, CarCategory.of(car("기아", "기아 봉고III 1톤", 900)))
        assertEquals(CarCategory.TRUCK, CarCategory.of(car("현대", "현대 마이티 3.5톤", 900)))
        assertEquals(CarCategory.DOMESTIC_UNDER_1300, CarCategory.of(car("현대", "현대 아반떼", 1300)))
        assertEquals(CarCategory.DOMESTIC_OVER_1300, CarCategory.of(car("현대", "현대 그랜저", 1301)))
        assertEquals(CarCategory.DOMESTIC_UNDER_1300, CarCategory.of(car("한국특장차", "한국특장차 캠핑카", 1000)))
    }

    @Test
    fun unknownCompanyIsUnclassified() {
        assertEquals(null, CarCategory.of(car("듣보모터스", "듣보모터스 신차", 1000)))
        assertEquals(CarCategory.IMPORTED, CarCategory.of(car("BMW", "BMW 520d", 1000)))
    }
}
