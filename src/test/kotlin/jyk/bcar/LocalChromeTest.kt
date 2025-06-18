package jyk.bcar

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

@Tag("local")
class LocalChromeTest {
    @Test
    @DisplayName("로컬 chromium 실행 테스트")
    fun testChromium() {
        val driver =
            ChromeDriver(
                ChromeOptions().apply {
                    addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage")
                },
            )

        driver.get("https://google.com")
        val wait = WebDriverWait(driver, Duration.ofSeconds(5))
        wait.until {
            val readyState = driver.executeScript("return document.readyState")
            readyState == "complete"
        }
        driver.close()
    }
}
