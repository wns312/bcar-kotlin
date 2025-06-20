package jyk.bcar.chrome.provider

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jyk.bcar.chrome.WebDriverProviderStrategy
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.springframework.stereotype.Component
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Component
class TestContainerWebDriverProvider : WebDriverProviderStrategy {
    private lateinit var chromeOptions: ChromeOptions
    private lateinit var chromeContainer: BrowserWebDriverContainer<*>

    @PostConstruct
    fun init() {
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

    @PreDestroy
    fun preDestroy() {
        chromeContainer.stop()
        chromeContainer.close()
    }

    override suspend fun getChromeDriver(): RemoteWebDriver =
        RemoteWebDriver(
            chromeContainer.seleniumAddress,
            chromeOptions,
        )
}
