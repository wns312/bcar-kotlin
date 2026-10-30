package jyk.bcar.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Name

@ConfigurationProperties(prefix = "google")
data class GoogleProperties(
    val sa: ServiceAccount = ServiceAccount(),
    val sheets: Sheet = Sheet(),
) {
    data class ServiceAccount(
        @param:Name("json.base64")
        val base64: String = "",
    )

    data class Sheet(
        val id: String = "",
    )
}
