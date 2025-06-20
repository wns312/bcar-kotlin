package jyk.bcar.chrome.provider

import jyk.bcar.chrome.WebDriverProviderStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.springframework.stereotype.Component

@Component
class LocalHeadlessChromeProvider : WebDriverProviderStrategy {
    override suspend fun getChromeDriver(): ChromeDriver = ChromeDriver()
}
