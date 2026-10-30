package jyk.bcar.domain

enum class CarCategory {
    IMPORTED,
    CARGO,
    TRUCK,
    DOMESTIC_UNDER_1300,
    DOMESTIC_OVER_1300,
    ;

    companion object {
        private val domesticCompanies = setOf(
            "기아",
            "현대",
            "쌍용",
            "KG모빌리티(쌍용)",
            "삼성",
            "르노(삼성)",
            "쉐보레",
            "쉐보레(대우)",
            "대창모터스",
            "대우버스",
            "세보모빌리티(캠시스)",
            "한국상용트럭",
            "한국쓰리축",
            "한국특장기술",
            "한국특장차",
            "한국메리트",
        )
        private val importedCompanies = setOf(
            "렉서스",
            "벤츠",
            "아우디",
            "미니",
            "테슬라",
            "포드",
            "캐딜락",
            "푸조",
            "지프",
            "포르쉐",
            "혼다",
            "링컨",
            "도요타",
            "토요타",
            "벤틀리",
            "BMW",
            "크라이슬러",
            "랜드로버",
            "로버",
            "닛산",
            "볼보",
            "폭스바겐",
            "인피니티",
        )
        private val cargo = Regex("봉고|포터")
        private val truck = Regex("톤|덤프|마이티|메가트럭|에어로|카운티|그랜버드|라이노|복사|세레스|콤보|타이탄|트레이드|파맥스|르노마스터")

        /** 모르는 제조사는 null — 어느 쿼터에도 넣을 수 없으니 할당 대상에서 뺀다 */
        fun of(car: Car): CarCategory? =
            when {
                car.company in importedCompanies -> IMPORTED
                car.company !in domesticCompanies -> null
                cargo.containsMatchIn(car.title) -> CARGO
                truck.containsMatchIn(car.title) -> TRUCK
                car.price <= 1300 -> DOMESTIC_UNDER_1300
                else -> DOMESTIC_OVER_1300
            }
    }
}
