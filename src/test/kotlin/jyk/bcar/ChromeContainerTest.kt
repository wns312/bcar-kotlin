package jyk.bcar

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

class ChromeContainerTest {
    companion object {
        private const val GOOGLE_URL = "https://google.com"
        private lateinit var chromeOptions: ChromeOptions
        private lateinit var chromeContainer: BrowserWebDriverContainer<*>

        @BeforeAll
        @JvmStatic
        fun setUp() {
            chromeOptions = ChromeOptions()
            chromeContainer =
                BrowserWebDriverContainer(
                    DockerImageName
                        .parse("selenium/standalone-chromium:latest")
                        .asCompatibleSubstituteFor("selenium/standalone-chrome"),
                ).withStartupTimeout(Duration.ofSeconds(10))
                    .withCapabilities(chromeOptions)
            chromeContainer.start()
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            chromeContainer.stop()
            chromeContainer.close()
        }
    }

    @Test
    @DisplayName("chromium 테스트 컨테이너 실행 테스트")
    fun testChromium() {
        val driver =
            RemoteWebDriver(
                chromeContainer.seleniumAddress,
                chromeOptions,
            )

        driver.get(GOOGLE_URL)
        val wait = WebDriverWait(driver, Duration.ofSeconds(5))
        wait.until {
            val readyState = driver.executeScript("return document.readyState")
            readyState == "complete"
        }
        driver.close()
    }
}
