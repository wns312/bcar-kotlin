package jyk.bcar.domain

data class TargetAdminUser(
    val id: String,
    val password: String,
    val targetSite: String,
    val quota: Int,
    val baseUrl: String,
) {
    val manageUrl: String get() = "https://car.$baseUrl/my/car"

    val registerUrl: String get() = "https://car.$baseUrl/my/car_post/new?car_idx=&state=0"

    /** 로그인 폼은 url 파라미터로 받은 주소로 되돌려준다 */
    fun loginUrlRedirecting(to: String): String = "https://ssl.$baseUrl/membership/login?url=$to"
}
