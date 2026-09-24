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

        // 제목에 든 이름 중 가장 긴 것이 정답에 가깝다 (그랜저 vs 그랜저HG)
        val byTitle = company.models
            .sortedByDescending { modelKey(it.name).length }
            .filter { car.title.contains(modelKey(it.name)) }

        // 소스 카테고리가 사이트 세그먼트와 어긋나는 모델이 있다 — 소스는 같은 그랜드스타렉스를
        // 화물차·특장차·RV로 제각각 내려보내지만 사이트의 스타렉스는 승합에 있다.
        // 세그먼트로 먼저 좁히고 못 찾으면 제조사 전체에서 찾는다. 모델이 비면 폼의 필수 항목을 못 채워 멈춘다
        val model = byTitle.firstOrNull { it.segment == segment.name }
            ?: byTitle.firstOrNull()
            ?: return source

        // 폼은 세그먼트를 고르면 모델 목록을 다시 그린다 — 어긋나 찾은 모델은 그 모델의 세그먼트로 채워야 고를 수 있다
        val modelSegment = tree.segments.firstOrNull { it.name == model.segment } ?: segment

        val plainTitle = car.title.replace(" ", "")
        val detailModel = model.detailModels
            .sortedByDescending { detailKey(it.name).length }
            .firstOrNull { plainTitle.contains(detailKey(it.name)) }

        return source.copy(segment = modelSegment, model = model, detailModel = detailModel)
    }

    private fun modelKey(name: String) = MODEL_ALIASES[name] ?: name

    private fun detailKey(name: String) = (DETAIL_ALIASES[name] ?: name).replace(" ", "")
}
