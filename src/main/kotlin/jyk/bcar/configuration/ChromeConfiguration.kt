package jyk.bcar.configuration

import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChromeConfiguration {
    @Bean
    fun defaultChromeOptions(): ChromeOptions = ChromeOptions()

    @Bean
    fun headlessChromeOptions(): ChromeOptions =
        ChromeOptions().apply {
            addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage")
        }
}
