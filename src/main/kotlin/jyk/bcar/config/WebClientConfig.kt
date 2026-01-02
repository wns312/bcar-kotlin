package jyk.bcar.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient {
        val strategies = ExchangeStrategies
            .builder()
            .codecs { config ->
                config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)
            }.build()

        return WebClient
            .builder()
            .exchangeStrategies(strategies)
            .build()
    }
}
