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
        private val cargo = Regex("봉고|포터")
        private val truck = Regex("톤|덤프|마이티|메가트럭|에어로|카운티|그랜버드|라이노|복사|세레스|콤보|타이탄|트레이드|파맥스|르노마스터")

        // ponytail: 모르는 제조사는 국산으로 본다. 수입 브랜드가 새로 나오면 domesticCompanies 대신 수입 목록으로 뒤집는다
        fun of(car: Car): CarCategory =
            when {
                car.company !in domesticCompanies -> IMPORTED
                cargo.containsMatchIn(car.title) -> CARGO
                truck.containsMatchIn(car.title) -> TRUCK
                car.price <= 1300 -> DOMESTIC_UNDER_1300
                else -> DOMESTIC_OVER_1300
            }
    }
}
