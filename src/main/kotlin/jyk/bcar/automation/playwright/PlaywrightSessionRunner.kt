package jyk.bcar.automation.playwright

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlaywrightSessionRunner(
    private val properties: PlaywrightProperties,
) {
    private val logger = LoggerFactory.getLogger(PlaywrightSessionRunner::class.java)

    suspend fun <T> withSession(block: suspend (PlaywrightSession) -> T): T =
        withContext(Dispatchers.IO) {
            val playwright = Playwright.create()
            val options =
                BrowserType
                    .LaunchOptions()
                    .setHeadless(properties.headless)
                    .setSlowMo(properties.slowMoMs)

            val browser =
                when (properties.browser.lowercase()) {
                    "chromium" -> playwright.chromium().launch(options)
                    "firefox" -> playwright.firefox().launch(options)
                    "webkit" -> playwright.webkit().launch(options)
                    else -> throw IllegalArgumentException("Unsupported Playwright browser: '${properties.browser}'")
                }

            try {
                block(PlaywrightSession(browser, properties))
            } finally {
                try {
                    browser.close()
                } catch (ex: Exception) {
                    logger.warn("Failed to close Playwright browser cleanly.", ex)
                } finally {
                    try {
                        playwright.close()
                    } catch (ex: Exception) {
                        logger.warn("Failed to close Playwright cleanly.", ex)
                    }
                }
            }
        }
}

class PlaywrightSession internal constructor(
    private val browser: com.microsoft.playwright.Browser,
    private val properties: PlaywrightProperties,
) {
    suspend fun <T> usePage(block: suspend (Page) -> T): T {
        val context = browser.newContext()
        context.setDefaultTimeout(properties.timeoutMs)
        try {
            val page = context.newPage()
            try {
                return block(page)
            } finally {
                page.close()
            }
        } finally {
            context.close()
        }
    }
}
