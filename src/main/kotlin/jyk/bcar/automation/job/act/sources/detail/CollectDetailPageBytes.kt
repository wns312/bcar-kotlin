package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.retryOnFailure
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient

class CollectDetailPageBytes(
    private val webClient: WebClient,
) : JobAct<CollectDetailPageBytesRequest, ByteArray> {
    companion object {
        private const val BASE_URI = "http://thebestcar.kr/car/carView.html?m_no="
        private const val REFERER_URI = "http://thebestcar.kr/mypage/mycar.html"
    }

    override suspend fun doAct(input: CollectDetailPageBytesRequest): ByteArray = retryOnFailure {
        webClient
            .get()
            .uri("${BASE_URI}${input.detailPageNum}")
            .header(
                HttpHeaders.ACCEPT,
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            ).header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
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
)
