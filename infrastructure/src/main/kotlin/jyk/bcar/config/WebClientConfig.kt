package jyk.bcar.config

import io.netty.channel.ChannelOption
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient {
        val strategies = ExchangeStrategies
            .builder()
            .codecs { config ->
                config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)
            }.build()

        // 소스 서버가 차단 시 응답 없이 커넥션을 잡고 있는다. 타임아웃 없으면 잡이 영원히 매달림
        val httpClient = HttpClient
            .create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(20))

        return WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .build()
    }
}
