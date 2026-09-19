package jyk.bcar.automation.job.act

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("jyk.bcar.automation.job.act.Retry")

// 소스 서버가 크롤링 도중 불규칙하게 connection reset을 낸다. 한 번 쉬면 대개 복구됨
suspend fun <T> retryOnFailure(
    maxAttempts: Int = 3,
    pause: Duration = 60.seconds,
    block: suspend () -> T,
): T {
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: WebClientException) {
            logger.warn("fetch failed (attempt ${attempt + 1}/$maxAttempts), pausing $pause: ${e.message}")
            delay(pause)
        }
    }
    return block()
}
