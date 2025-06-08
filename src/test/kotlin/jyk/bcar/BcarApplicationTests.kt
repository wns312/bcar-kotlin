package jyk.bcar

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@SpringBootTest(args = ["helloWorldRunner"])
class BcarApplicationTests {
    @Test
    fun contextLoads() {
    }

    @Test
    @DisplayName("chromium 테스트 컨테이너 실행 테스트")
    fun testChromium() {
        val chromeOptions = ChromeOptions()
        val chromeContainer =
            BrowserWebDriverContainer(
                DockerImageName
                    .parse("selenium/standalone-chromium:137.0")
                    .asCompatibleSubstituteFor("selenium/standalone-chrome"),
            ).withStartupTimeout(Duration.ofSeconds(10))
                .withCapabilities(chromeOptions)

        chromeContainer.start()

        val driver =
            RemoteWebDriver(
                chromeContainer.seleniumAddress,
                chromeOptions,
            )

        driver.get("https://google.com")
        val wait = WebDriverWait(driver, Duration.ofSeconds(5))
        wait.until {
            val readyState = driver.executeScript("return document.readyState")
            println(readyState)
            readyState == "complete"
        }
        driver.close()
        chromeContainer.stop()
        chromeContainer.close()
    }
}
