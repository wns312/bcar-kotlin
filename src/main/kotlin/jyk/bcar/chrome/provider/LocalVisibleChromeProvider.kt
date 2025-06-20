package jyk.bcar.chrome.provider

import jyk.bcar.chrome.WebDriverProviderStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.stereotype.Component

@Component
class LocalVisibleChromeProvider : WebDriverProviderStrategy {
    override suspend fun getChromeDriver(): ChromeDriver {
        val options =
            ChromeOptions().apply {
                addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage")
            }

        return ChromeDriver(options)
    }
}
