package jyk.bcar.domain

/**
 * 소스 차량을 대상 사이트 분류에 맞춘다. 이름이 사이트마다 조금씩 달라 변환표가 필요하다.
 * 수입차는 세그먼트·제조사까지만 고르면 되고, 모델·세부모델은 국산차에만 있다.
 */
class CarClassifier(
    private val tree: CategoryTree,
) {
    companion object {
        private const val ETC = "기타"

        /** 소스 카테고리 → 사이트 세그먼트 */
        private val SEGMENTS = mapOf(
            "경차" to "경소형",
            "소형차" to "경소형",
            "준중형차" to "준중형",
            "중형차" to "중대형",
            "대형차" to "중대형",
            "" to "중대형",
            "스포츠카" to "스포츠카",
            "SUV" to "SUV/RV",
            "RV" to "SUV/RV",
            // 렉스턴·코란도 스포츠가 사이트에서는 SUV/RV에 있다
            "픽업" to "SUV/RV",
            "승합차" to "승합",
            "화물차" to "화물/버스",
            "버스" to "화물/버스",
            "특장차" to "화물/버스",
            "캠핑트레일러" to "화물/버스",
        )

        /** 소스 제조사 → 사이트 제조사. 이름이 같은 것은 적지 않는다 */
        private val COMPANIES = mapOf(
            "르노(삼성)" to "르노코리아(삼성)",
            "삼성" to "르노코리아(삼성)",
            "쌍용" to "KG모빌리티(쌍용)",
            "쉐보레" to "쉐보레(대우)",
            "토요타" to "도요타",
            "로버" to "랜드로버",
        )

        /** 사이트 모델명 → 매물 제목에 쓰이는 표기 */
        private val MODEL_ALIASES = mapOf(
            "봉고화물" to "봉고",
            "봉고승합" to "봉고",
            "e-마이티" to "마이티",
            "캡처" to "캡쳐",
        )

        /** 제목에만 붙는 세대 수식어. 사이트 이름에는 없거나 붙여 쓴다 */
        private val GENERATIONS = Regex("뉴|신형")

        /** 사이트 세부모델명 → 매물 제목에 쓰이는 표기 */
        private val DETAIL_ALIASES = mapOf(
            "봉고III" to "봉고Ⅲ",
            "더뉴봉고III" to "더 뉴봉고Ⅲ",
            "봉고IIIEV" to "봉고ⅢEV",
            "올뉴모닝JA" to "올뉴모닝(JA)",
            "캡처" to "캡쳐",
        )
    }

    /** 분류가 안 되면 null — 올릴 수 없는 차다 */
    fun classify(car: Car): UploadSource? {
        val detail = car.detail ?: return null
        val category = CarCategory.of(car) ?: return null
        val origin = if (category == CarCategory.IMPORTED) CategoryTree.Origin.IMPORTED else CategoryTree.Origin.DOMESTIC

        val segmentName = SEGMENTS[detail.category] ?: return null
        val segment = tree.segments.firstOrNull { it.name == segmentName } ?: return null

        val companies = tree.companies.filter { it.origin == origin }
        val companyName = COMPANIES[car.company] ?: car.company
        val company = companies.firstOrNull { it.name == companyName }
            ?: companies.firstOrNull { it.name == ETC }
            ?: return null

        val source = UploadSource(car = car, origin = origin, segment = segment, company = company)
        if (origin == CategoryTree.Origin.IMPORTED) return source

        // 사이트는 이름을 붙여 쓰는데("아이오닉5") 제목은 띄우고 세대 수식어까지 끼워 넣는다("제네시스 뉴 GV80")
        val plainTitle = car.title.replace(" ", "")
        val titles = listOf(plainTitle, plainTitle.replace(GENERATIONS, ""))

        // 제목 앞쪽에서 맞은 이름이 그 차다 — 뒤에 붙는 트림 문구에 남의 모델명이 섞여 든다("포터II 덤프 4WD"의 덤프).
        // 같은 자리면 긴 쪽(그랜저 vs 그랜저HG), 그래도 같으면 소스 세그먼트와 맞는 쪽.
        // 세그먼트로 거르지는 않는다 — 소스는 같은 그랜드스타렉스를 화물차·특장차·RV로 제각각 내려보내지만
        // 사이트의 스타렉스는 승합에 있다
        val model = company.models
            .mapNotNull { candidate -> firstHit(titles, modelKey(candidate.name))?.let { candidate to it } }
            .minWithOrNull(
                compareBy({ it.second }, { -modelKey(it.first.name).length }, { it.first.segment != segment.name }),
            )?.first
            // 모델이 비면 폼의 필수 항목을 못 채워 멈춘다. 모델 자체가 없는 제조사(기타)는 비운 채로 올라간다
            ?: return if (company.models.isEmpty()) source else null

        // 폼은 세그먼트를 고르면 모델 목록을 다시 그린다 — 어긋나 찾은 모델은 그 모델의 세그먼트로 채워야 고를 수 있다
        val modelSegment = tree.segments.firstOrNull { it.name == model.segment } ?: segment

        val detailModel = model.detailModels
            .sortedByDescending { detailKey(it.name).length }
            .firstOrNull { candidate -> titles.any { it.contains(detailKey(candidate.name)) } }

        return source.copy(segment = modelSegment, model = model, detailModel = detailModel)
    }

    /** 어느 표기에서든 가장 앞선 자리. 어디에도 없으면 null */
    private fun firstHit(titles: List<String>, key: String) =
        titles.mapNotNull { title -> title.indexOf(key).takeIf { it >= 0 } }.minOrNull()

    private fun modelKey(name: String) = (MODEL_ALIASES[name] ?: name).replace(" ", "")

    private fun detailKey(name: String) = (DETAIL_ALIASES[name] ?: name).replace(" ", "")
}
