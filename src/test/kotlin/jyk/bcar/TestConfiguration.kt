package jyk.bcar

import com.google.api.services.sheets.v4.Sheets
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.AwsRegionProvider

@TestConfiguration
class TestConfiguration {
    /**
     * 스프레드시트 제공 빈의 크레덴셜 인증을 비롯한 검증과 빈 생성 과정을 생략하기 위한 mock 빈
     * */
    @Bean
    @Primary
    fun sheets(): Sheets = mock(Sheets::class.java)

    /**
     * 테스트를 위한 mock AWS 크레덴셜 제공자
     * */
    @Bean
    @Primary
    fun awsCredentialsProvider(): AwsCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test-access-key", "test-secret-key"),
        )

    /**
     * 테스트를 위한 mock AWS 리전 제공자
     * */
    @Bean
    @Primary
    fun awsRegionProvider(): AwsRegionProvider = AwsRegionProvider { Region.AP_NORTHEAST_2 }
}
