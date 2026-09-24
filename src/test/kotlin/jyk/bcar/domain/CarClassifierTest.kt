package jyk.bcar.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarClassifierTest {
    private val tree = CategoryTree(
        segments = listOf(
            CategoryTree.Segment("중대형", "3", 3),
            CategoryTree.Segment("SUV/RV", "5", 5),
            CategoryTree.Segment("화물/버스", "7", 7),
        ),
        companies = listOf(
            CategoryTree.Company(
                "현대",
                "c-hd",
                1,
                CategoryTree.Origin.DOMESTIC,
                models = listOf(
                    CategoryTree.Model(
                        "그랜저",
                        "m-gr",
                        1,
                        "중대형",
                        detailModels = listOf(
                            CategoryTree.DetailModel("그랜저IG", "d-ig", 1),
                            CategoryTree.DetailModel("그랜저", "d-gr", 2),
                        ),
                    ),
                    CategoryTree.Model("그랜저HG", "m-hg", 2, "중대형"),
                    CategoryTree.Model("포터", "m-pt", 3, "화물/버스"),
                ),
            ),
            CategoryTree.Company("르노코리아(삼성)", "c-rn", 2, CategoryTree.Origin.DOMESTIC),
            CategoryTree.Company("기타", "c-etc", 3, CategoryTree.Origin.DOMESTIC),
            CategoryTree.Company("벤츠", "c-bz", 4, CategoryTree.Origin.IMPORTED),
        ),
    )
    private val classifier = CarClassifier(tree)

    private fun car(title: String, company: String, category: String, price: Int = 2000) = Car(
        "12가3456",
        title,
        company,
        "1",
        "ag",
        "s",
        "p",
        price,
        detail = CarDetail(
            category,
            2000,
            "2020-01",
            10000,
            "흰색",
            "오토",
            "휘발유",
            "1",
            "무사고",
            "1",
            "2020-01-01",
            false,
            false,
            "src",
            emptyList(),
        ),
    )

    @Test
    fun picksLongestModelNameInTitle() {
        val source = classifier.classify(car("현대 그랜저HG 럭셔리", "현대", "대형차"))!!

        assertEquals("중대형", source.segment.name)
        assertEquals("현대", source.company.name)
        assertEquals("그랜저HG", source.model?.name)
        assertNull(source.detailModel)
    }

    @Test
    fun matchesDetailModelByTitle() {
        val source = classifier.classify(car("현대 그랜저 IG 2.4", "현대", "대형차"))!!

        assertEquals("그랜저", source.model?.name)
        assertEquals("그랜저IG", source.detailModel?.name)
    }

    @Test
    fun stopsAtCompanyForImportedCars() {
        val source = classifier.classify(car("벤츠 E클래스", "벤츠", "대형차"))!!

        assertEquals(CategoryTree.Origin.IMPORTED, source.origin)
        assertEquals("벤츠", source.company.name)
        assertNull(source.model)
    }

    @Test
    fun mapsRenamedCompanies() {
        assertEquals("르노코리아(삼성)", classifier.classify(car("삼성 SM6", "르노(삼성)", "대형차"))?.company?.name)
        // 사이트에 없는 국산 제조사는 기타로
        assertEquals("기타", classifier.classify(car("대우버스 BS090", "대우버스", "버스"))?.company?.name)
    }

    @Test
    fun onlyLooksAtModelsOfTheChosenSegment() {
        // 포터는 화물/버스에만 있다 — SUV/RV로 분류되면 모델 없이 제조사까지만
        val source = classifier.classify(car("현대 포터II", "현대", "SUV"))!!

        assertEquals("SUV/RV", source.segment.name)
        assertNull(source.model)
        assertEquals("포터", classifier.classify(car("현대 포터II", "현대", "화물차"))?.model?.name)
    }

    @Test
    fun refusesCarsItCannotPlace() {
        assertNull(classifier.classify(car("듣보 차", "듣보모터스", "대형차")))
        assertNull(classifier.classify(car("현대 그랜저", "현대", "알수없는분류")))
        assertNull(classifier.classify(car("현대 그랜저", "현대", "대형차").copy(detail = null)))
    }
}
