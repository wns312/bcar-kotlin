package jyk.bcar.domain

/**
 * 대상 사이트 등록 폼의 분류 트리. 폼은 이름이 아니라 data-value로 고르므로 수집해 둬야 한다.
 * 수입차는 세그먼트·제조사까지만 고르면 되고, 모델·세부모델은 국산차에만 쓴다.
 */
data class CategoryTree(
    val segments: List<Segment>,
    val companies: List<Company>,
) {
    data class Segment(
        val name: String,
        val dataValue: String,
        val index: Int,
    )

    data class Company(
        val name: String,
        val dataValue: String,
        val index: Int,
        val origin: Origin,
        val models: List<Model> = emptyList(),
    )

    data class Model(
        val name: String,
        val dataValue: String,
        val index: Int,
        val segment: String,
        val detailModels: List<DetailModel> = emptyList(),
    )

    data class DetailModel(
        val name: String,
        val dataValue: String,
        val index: Int,
    )

    enum class Origin {
        DOMESTIC,
        IMPORTED,
    }

    val modelCount: Int get() = companies.sumOf { it.models.size }

    val detailModelCount: Int get() = companies.sumOf { company -> company.models.sumOf { it.detailModels.size } }
}
