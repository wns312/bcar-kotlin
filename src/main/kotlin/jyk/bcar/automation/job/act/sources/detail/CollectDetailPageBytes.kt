package jyk.bcar.automation.job.act.sources.detail

import io.netty.handler.timeout.ReadTimeoutException
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.client.RotatingWebClient
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException

class CollectDetailPageBytes(
    private val webClient: WebClient,
    private val rotatingWebClient: RotatingWebClient,
) : JobAct<CollectDetailPageBytesRequest, ByteArray> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val BASE_URI = "http://thebestcar.kr/car/carView.html?m_no="
        private const val REFERER_URI = "http://thebestcar.kr/mypage/mycar.html"
    }

    override suspend fun doAct(input: CollectDetailPageBytesRequest): ByteArray {
        logger.info("CollectDetailPageBytes start: ${input.detailPageNum}")

        val response = try {
            rotatingWebClient.get().request(input)
        } catch (e: WebClientResponseException) {
            if (e.cause is ReadTimeoutException) {
                logger.info("fail and sleep: WebClientResponseException")
                Thread.sleep(60000)
                rotatingWebClient.forceGet().request(input)
            } else {
                throw e
            }
        } catch (e: WebClientRequestException) {
            logger.info("fail and sleep: WebClientRequestException")
            Thread.sleep(60000)
            rotatingWebClient.forceGet().request(input)
        }

        logger.info("CollectDetailPageBytes done: ${input.detailPageNum}")

        return response
    }

    private suspend fun WebClient.request(input: CollectDetailPageBytesRequest): ByteArray {
        return get()
            .uri("${BASE_URI}${input.detailPageNum}")
            .header(
                HttpHeaders.ACCEPT,
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            ).header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            // needed for authenticated response
//            .header(HttpHeaders.COOKIE, input.cookieHeader)
            .header(HttpHeaders.HOST, "thebestcar.kr")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.USER_AGENT, DetailUserAgents.values.random())
            .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
            // needed for authenticated response
            .header(HttpHeaders.REFERER, REFERER_URI)
            .header("Upgrade-Insecure-Requests", "1")
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .awaitSingle()
    }
}

data class CollectDetailPageBytesRequest(
    val detailPageNum: String,
//    val cookieHeader: String,
)
