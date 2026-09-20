package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.retryOnFailure
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import kotlin.time.Duration.Companion.seconds

class CollectDetailPageBytes(
    private val webClient: WebClient,
) : JobAct<CollectDetailPageBytesRequest, ByteArray> {
    companion object {
        private const val BASE_URI = "http://thebestcar.kr/car/carView.html?m_no="
        private const val REFERER_URI = "http://thebestcar.kr/mypage/mycar.html"
    }

    // IP 차단이면 같은 IP로 오래 기다려도 안 풀림. 짧게만 재시도하고 죽어서 Batch retryStrategy(새 컨테이너=새 IP)에 맡긴다
    override suspend fun doAct(input: CollectDetailPageBytesRequest): ByteArray = retryOnFailure(maxAttempts = 2, pause = 10.seconds) {
        webClient
            .get()
            .uri("${BASE_URI}${input.detailPageNum}")
            .header(
                HttpHeaders.ACCEPT,
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            ).header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .header(HttpHeaders.COOKIE, input.cookieHeader)
            .header(HttpHeaders.HOST, "thebestcar.kr")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.USER_AGENT, DetailUserAgents.values.random())
            .header(HttpHeaders.REFERER, REFERER_URI)
            .header("Upgrade-Insecure-Requests", "1")
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .awaitSingle()
    }
}

data class CollectDetailPageBytesRequest(
    val detailPageNum: String,
    val cookieHeader: String,
)
