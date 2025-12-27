package jyk.bcar.automation.playwright

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "automation.playwright")
data class PlaywrightProperties(
    val browser: String = "chromium",
    val headless: Boolean = true,
    val slowMoMs: Double = 0.0,
    val timeoutMs: Double = 30_000.0,
)
