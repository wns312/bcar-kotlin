package jyk.bcar.runner

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class TestRunner : Runner {
    override fun run() {
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
            println(readyState)
            readyState == "complete"
        }
        driver.close()
    }
}
