package jyk.bcar.automation.job.act.sources

import org.springframework.http.HttpHeaders
import java.util.function.Consumer

object SourceSite {
    private const val HOST = "thebestcar.kr"
    private const val BASE_URL = "http://$HOST"
    const val LOGIN_URL = "$BASE_URL/mypage/login.html"
    const val LOGIN_OK_URL = "$BASE_URL/mypage/login_ok.html"
    const val MY_CAR_URL = "$BASE_URL/mypage/mycar.html"
    const val CAR_LIST_URL = "$BASE_URL/mypage/_inc_carList.html"
    const val CAR_VIEW_URL = "$BASE_URL/car/carView.html?m_no="
    private const val ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"

    /** 브라우저 요청처럼 보이지 않으면 응답하지 않는다. Referer도 있어야 목록이 온다 */
    fun browserHeaders(userAgent: String, referer: String): Consumer<HttpHeaders> =
        Consumer {
            it.set(HttpHeaders.ACCEPT, ACCEPT)
            it.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            it.set(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9")
            it.set(HttpHeaders.CACHE_CONTROL, "no-cache")
            it.set(HttpHeaders.HOST, HOST)
            it.set(HttpHeaders.PRAGMA, "no-cache")
            it.set("Upgrade-Insecure-Requests", "1")
            it.set(HttpHeaders.USER_AGENT, userAgent)
            it.set(HttpHeaders.REFERER, referer)
        }
}
