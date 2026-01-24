package jyk.bcar.automation.job.act.sources.draft

import jyk.bcar.automation.job.act.sources.CarType
import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.draft.DraftAct.Companion.COLLECT_ADMIN_URL
import jyk.bcar.domain.DraftCar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient

class CollectDraftCarList(
    private val webClient: WebClient,
    private val cookieHeader: String,
) : DraftAct<CollectCarListRequest, List<DraftCar>> {
    companion object {
        private const val SOURCE_SEARCH_PAGE = "http://thebestcar.kr/mypage/_inc_carList.html"
        private const val DEFAULT_PARAMS = "searchChecker=1&listView=y&pageSize=100"
        private const val SOURCE_SEARCH_BASE = "$SOURCE_SEARCH_PAGE?$DEFAULT_PARAMS"
        private const val SOURCE_REFERER_BASE = "$COLLECT_ADMIN_URL?$DEFAULT_PARAMS"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun doAct(input: CollectCarListRequest): List<DraftCar> = coroutineScope {
        val semaphore = Semaphore(10)
        input.pageRange.map { pageNum ->
            async {
                semaphore.withPermit {
                    logger.info("$pageNum start")
                    val url = getSourceSearchUrl(
                        CollectCarSearchPageRequest(
                            carType = input.carType,
                            minPrice = input.minPrice,
                            maxPrice = input.maxPrice,
                            page = pageNum,
                        ),
                        baseUrl = SOURCE_SEARCH_BASE,
                    )

                    val refererUrl = getSourceSearchUrl(
                        CollectCarSearchPageRequest(
                            carType = input.carType,
                            minPrice = input.minPrice,
                            maxPrice = input.maxPrice,
                            page = pageNum,
                        ),
                        baseUrl = SOURCE_REFERER_BASE,
                    )

                    val drafts = fetchList(url, refererUrl)
                    logger.info("$pageNum end")
                    drafts
                }
            }
        }
    }.awaitAll().flatten()

    private fun getSourceSearchUrl(request: CollectCarSearchPageRequest, baseUrl: String): String = buildString {
        append(baseUrl)
        append("&c_price1=${request.minPrice}")
        append("&c_price2=${request.maxPrice}")
        append("&c_cho=${request.carType.searchNum}&page=${request.page}")
    }

    private suspend fun fetchList(url: String, refererUrl: String): List<DraftCar> {
        val bytes = webClient
            .get()
            .uri(url)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            ).header("Accept-Encoding", "gzip, deflate")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .header("Cookie", cookieHeader)
            .header("Host", "thebestcar.kr")
            .header("Pragma", "no-cache")
            .header("Upgrade-Insecure-Requests", "1")
            .header("User-Agent", DEFAULT_USER_AGENT)
            // 응답이 정상적으로 오기 위해 요청 헤더로 필요 (요청자 URL 정보)
            .header("Referer", refererUrl)
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .awaitSingle()

        val request = DraftExtractorRequest(
            htmlBytes = bytes,
            charSet = CharSet.EUC_KR,
            baseUri = url,
        )

        return DraftExtractor().doAct(request)
    }
}

data class CollectCarSearchPageRequest(
    val carType: CarType = CarType.ALL,
    val minPrice: Int,
    val maxPrice: Int,
    val page: Int,
)

data class CollectCarListRequest(
    val carType: CarType = CarType.ALL,
    val minPrice: Int,
    val maxPrice: Int,
    val pageRange: IntRange,
)
