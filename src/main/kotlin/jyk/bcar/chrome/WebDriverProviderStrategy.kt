package jyk.bcar.chrome

import org.openqa.selenium.remote.RemoteWebDriver

interface WebDriverProviderStrategy {
    suspend fun getChromeDriver(): RemoteWebDriver
}
