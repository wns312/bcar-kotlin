package jyk.bcar.domain

data class DraftCar(
    val carNumber: String, // 차량 번호
    val title: String, // 차량 제목
    val company: String, // 차량 제조사
    val detailPageNum: String, // 상세페이지 번호
    val agency: String, // 중고차 사무실
    val seller: String, // 판매자
    val sellerPhone: String, // 판매자 휴대전화
    val price: Int, // 가격
)
