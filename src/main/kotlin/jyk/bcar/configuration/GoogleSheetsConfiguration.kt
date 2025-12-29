package jyk.bcar.configuration

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64

@Configuration
@EnableConfigurationProperties(GoogleProperties::class)
class GoogleSheetsConfiguration(
    private val props: GoogleProperties,
) {
    @Bean
    fun sheets(): Sheets {
        require(props.sa.base64.isNotBlank())
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        val jsonBytes = Base64.getDecoder().decode(props.sa.base64)

        val credential =
            GoogleCredentials
                .fromStream(jsonBytes.inputStream())
                .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))

        return Sheets
            .Builder(httpTransport, jsonFactory) { request ->
                credential.refreshIfExpired()
                request.headers.authorization = "Bearer ${credential.accessToken.tokenValue}"
            }.setApplicationName("spring-kotlin-sheets")
            .build()
    }
}
