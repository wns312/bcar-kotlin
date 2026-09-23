package jyk.bcar.domain

data class TargetAdminUser(
    val id: String,
    val password: String,
    val targetSite: String,
    val quota: Int,
    val baseUrl: String,
) {
    val manageUrl: String get() = "https://car.$baseUrl/my/car"

    /** products는 등록 상품 선택 단계를 건너뛴다. car-normal-60 = 기본등록 60일 */
    val registerUrl: String get() = "https://car.$baseUrl/my/car_post/new?car_idx=&state=0&products=car-normal-60"

    /** 로그인 폼은 url 파라미터로 받은 주소로 되돌려준다 */
    fun loginUrlRedirecting(to: String): String = "https://ssl.$baseUrl/membership/login?url=$to"
}
