package jyk.bcar.client

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import reactor.netty.transport.logging.AdvancedByteBufFormat
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Component
class RotatingWebClient {
    companion object {
        private const val ROTATE_EVERY: Int = 10 // N건마다 교체
        private const val CONNECT_TIMEOUT_MS: Int = 20000
        private val responseTimeout: Duration = Duration.ofSeconds(20)
        private val counter = AtomicInteger(0)
        private val ref = AtomicReference(newHolder())

        private fun newHolder(): Holder {
            val provider = ConnectionProvider
                .builder("crawler-pool")
                .maxConnections(1) // 동시성 1 추천(차단/블랙홀 방어에 가장 안전)
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .maxIdleTime(Duration.ofSeconds(5))
                .maxLifeTime(Duration.ofMinutes(2)) // 일정 시간 지나면 커넥션 교체
                .evictInBackground(Duration.ofSeconds(30))
                .build()

            val httpClient = HttpClient
                .create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(responseTimeout)
                // "반응 없음"에서 영원히 걸리는 걸 막는 타임아웃들
                .wiretap(
                    "reactor.netty.http.client.HttpClient",
                    io.netty.handler.logging.LogLevel.DEBUG,
                    AdvancedByteBufFormat.SIMPLE,
                ).doOnConnected { conn ->
                    conn.addHandlerLast(ReadTimeoutHandler(10))
                    conn.addHandlerLast(WriteTimeoutHandler(10))
                    println("[NET] connected: ${conn.channel().id()}")
                }.doOnRequest { req, _ ->
                    println("[HTTP] request: ${req.method()} ${req.resourceUrl()}")
                }.doOnResponse { _, res ->
                    println("[HTTP] response status $res")
                }

            val webClient = WebClient
                .builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                // 여기서 Connection: close를 강제할 수도 있는데,
                // 너는 close 하면 못받아온다고 했으니 기본은 keep-alive로 두는 게 낫다.
                .build()

            return Holder(provider, webClient)
        }
    }

    private data class Holder(
        val provider: ConnectionProvider,
        val client: WebClient,
    )

    fun forceGet(): WebClient {
        rotate()
        return ref.get().client
    }

    fun get(): WebClient {
        val n = counter.incrementAndGet()
        if (n % ROTATE_EVERY == 0) rotate()
        return ref.get().client
    }

    private fun rotate() {
        val old = ref.getAndSet(newHolder())
        // 기존 풀 정리(비동기지만 즉시 dispose 호출 가능)
        old.provider.dispose()
    }
}
