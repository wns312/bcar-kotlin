package jyk.bcar.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarClassifierTest {
    private val tree = CategoryTree(
        segments = listOf(
            CategoryTree.Segment("준중형", "2", 2),
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
                    // 사이트 트리에서 덤프가 포터보다 앞에 있다 — 제목 순서로 고르지 않으면 "포터II 덤프"가 덤프로 간다
                    CategoryTree.Model("덤프", "m-dp", 3, "화물/버스"),
                    CategoryTree.Model("포터", "m-pt", 4, "화물/버스"),
                    CategoryTree.Model("아이오닉", "m-io", 5, "준중형"),
                    CategoryTree.Model("아이오닉5", "m-io5", 6, "준중형"),
                    CategoryTree.Model(
                        "제네시스",
                        "m-gn",
                        7,
                        "중대형",
                        detailModels = listOf(
                            CategoryTree.DetailModel("제네시스GV80", "d-gv80", 1),
                            CategoryTree.DetailModel("제네시스", "d-gn", 2),
                        ),
                    ),
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
        // 사이트에 없는 국산 제조사는 기타로. 기타에는 모델이 없고, 모델 없이 올라가는 것도 확인됐다(2026-09-24 prod 4건)
        val source = classifier.classify(car("대우버스 BS090", "대우버스", "버스"))!!
        assertEquals("기타", source.company.name)
        assertNull(source.model)
    }

    @Test
    fun matchesSiteNamesWrittenWithoutSpaces() {
        // 사이트는 "아이오닉5", 제목은 "아이오닉 5"
        val source = classifier.classify(car("현대 아이오닉 5 EV 2WD 롱레인지", "현대", "준중형차"))!!

        assertEquals("아이오닉5", source.model?.name)
    }

    @Test
    fun ignoresGenerationWordsWedgedIntoTitle() {
        // "제네시스 뉴 GV80"에 "제네시스GV80"이 없어 구형 세단 세부모델로 붙던 것
        val source = classifier.classify(car("제네시스 뉴 GV80 2.5 가솔린 AWD 기본형", "현대", "SUV"))!!

        assertEquals("제네시스", source.model?.name)
        assertEquals("제네시스GV80", source.detailModel?.name)
    }

    @Test
    fun prefersTheNameThatComesFirstInTitle() {
        // 뒤에 붙는 트림 문구에 남의 모델명이 섞여 든다
        assertEquals("포터", classifier.classify(car("현대 포터II 덤프 4WD", "현대", "화물차"))?.model?.name)
    }

    @Test
    fun fallsBackToOtherSegmentsWhenSourceCategoryDisagrees() {
        // 포터는 사이트에서 화물/버스인데 소스가 SUV로 내려보냈다. 세그먼트로 거르면 모델이 비어
        // 등록 폼의 필수 항목을 못 채운다 — 2026-09-24 prod 업로드 실패 11건이 전부 이 모양이었다
        val source = classifier.classify(car("현대 포터II", "현대", "SUV"))!!

        assertEquals("포터", source.model?.name)
        // 폼은 세그먼트를 고르면 모델 목록을 다시 그린다 — 모델의 세그먼트로 채워야 고를 수 있다
        assertEquals("화물/버스", source.segment.name)
    }

    @Test
    fun prefersModelOfTheSourceSegmentWhenBothMatch() {
        // 세그먼트가 맞는 모델이 있으면 그걸 쓴다 — 폴백이 기존 분류를 흔들지 않아야 한다
        val source = classifier.classify(car("현대 그랜저", "현대", "대형차"))!!

        assertEquals("그랜저", source.model?.name)
        assertEquals("중대형", source.segment.name)
    }

    @Test
    fun refusesCarsItCannotPlace() {
        assertNull(classifier.classify(car("듣보 차", "듣보모터스", "대형차")))
        assertNull(classifier.classify(car("현대 그랜저", "현대", "알수없는분류")))
        assertNull(classifier.classify(car("현대 그랜저", "현대", "대형차").copy(detail = null)))
    }

    @Test
    fun leavesModelEmptyWhenTreeHasNoMatch() {
        // 폼이 제조사의 "기타" 모델 + 직접입력으로 받는다 — 2026-09-26 prod 분류 불가 3건(익스프레스밴·포니II)
        val source = classifier.classify(car("현대 유니버스 디젤 노블", "현대", "버스"))!!

        assertEquals("현대", source.company.name)
        assertNull(source.model)
        assertEquals("화물/버스", source.segment.name)
    }
}
