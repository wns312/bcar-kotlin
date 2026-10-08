package jyk.bcar.automation.job.act.sources.draft

import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.retryOnFailure
import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.SourceSite
import jyk.bcar.domain.Car
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient

class CollectDraftCarList(
    private val webClient: WebClient,
    private val cookieHeader: String,
) : JobAct<CollectCarListRequest, List<Car>> {
    companion object {
        private const val DEFAULT_PARAMS = "searchChecker=1&listView=y&pageSize=100"
        private const val SOURCE_SEARCH_BASE = "${SourceSite.CAR_LIST_URL}?$DEFAULT_PARAMS"
        private const val SOURCE_REFERER_BASE = "${SourceSite.MY_CAR_URL}?$DEFAULT_PARAMS"

        // 소스 서버가 크롤링 도중 불규칙하게 connection reset. 처리량은 동시성과 무관(~75p/min, 서버 병목)
        private const val CONCURRENCY = 3
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun doAct(input: CollectCarListRequest): List<Car> = coroutineScope {
        val semaphore = Semaphore(CONCURRENCY)
        input.pageRange.map { pageNum ->
            async {
                semaphore.withPermit {
                    logger.info("$pageNum start")
                    val url = getSourceSearchUrl(SOURCE_SEARCH_BASE, input.filter, pageNum)
                    val refererUrl = getSourceSearchUrl(SOURCE_REFERER_BASE, input.filter, pageNum)
                    val drafts = fetchList(url, refererUrl)
                    logger.info("$pageNum end")
                    drafts
                }
            }
        }
    }.awaitAll().flatten()

    private fun getSourceSearchUrl(baseUrl: String, filter: DraftFilter, page: Int): String =
        "$baseUrl&c_price1=${filter.minPrice}&c_price2=${filter.maxPrice}&c_cho=${filter.carType.searchNum}&page=$page"

    private suspend fun fetchList(url: String, refererUrl: String): List<Car> {
        val bytes = retryOnFailure { fetchBytes(url, refererUrl) }

        val request = DraftExtractorRequest(
            htmlBytes = bytes,
            charSet = CharSet.EUC_KR,
            baseUri = url,
        )

        return DraftExtractor().doAct(request)
    }

    // 세마포어 permit을 쥔 채 재시도 대기하므로 그동안 전체 크롤링이 같이 느려진다 — 의도한 백오프
    private suspend fun fetchBytes(url: String, refererUrl: String): ByteArray =
        webClient
            .get()
            .uri(url)
            .headers(SourceSite.browserHeaders(DEFAULT_USER_AGENT, refererUrl))
            .header(HttpHeaders.CONNECTION, "keep-alive")
            .header(HttpHeaders.COOKIE, cookieHeader)
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .awaitSingle()
}

data class CollectCarListRequest(
    val filter: DraftFilter,
    val pageRange: IntRange,
)
