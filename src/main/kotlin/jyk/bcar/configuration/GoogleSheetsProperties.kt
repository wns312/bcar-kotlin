package jyk.bcar.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "google.sa.json")
data class GoogleSheetsProperties(
    val b64: String = "",
)
