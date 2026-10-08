package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.retryOnFailure
import jyk.bcar.automation.job.act.sources.SourceSite
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.reactive.function.client.WebClient
import kotlin.time.Duration.Companion.seconds

class CollectDetailPageBytes(
    private val webClient: WebClient,
) : JobAct<CollectDetailPageBytesRequest, ByteArray> {
    // IP 차단이면 같은 IP로 오래 기다려도 안 풀림. 짧게만 재시도하고 죽어서 Batch retryStrategy(새 컨테이너=새 IP)에 맡긴다
    override suspend fun doAct(input: CollectDetailPageBytesRequest): ByteArray = retryOnFailure(maxAttempts = 2, pause = 10.seconds) {
        webClient
            .get()
            .uri("${SourceSite.CAR_VIEW_URL}${input.detailPageNum}")
            .headers(SourceSite.browserHeaders(DetailUserAgents.values.random(), SourceSite.MY_CAR_URL))
            .retrieve()
            .toEntity(ByteArray::class.java)
            .awaitSingle()
            .let { entity ->
                // 차단 시 200에 빈 body를 주기도 한다. 호출자가 fetch 실패로 취급하도록 예외
                entity.body?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("empty body, status=${entity.statusCode}")
            }
    }
}

data class CollectDetailPageBytesRequest(
    val detailPageNum: String,
)
