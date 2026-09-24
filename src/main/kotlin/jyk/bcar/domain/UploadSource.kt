package jyk.bcar.domain

/** 등록 폼이 요구하는 분류까지 채워진 차량. 모델·세부모델은 국산차만 찾는다 */
data class UploadSource(
    val car: Car,
    val origin: CategoryTree.Origin,
    val segment: CategoryTree.Segment,
    val company: CategoryTree.Company,
    val model: CategoryTree.Model? = null,
    val detailModel: CategoryTree.DetailModel? = null,
)
