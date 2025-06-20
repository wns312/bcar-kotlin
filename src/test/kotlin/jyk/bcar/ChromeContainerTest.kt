package jyk.bcar

import jyk.bcar.chrome.provider.TestContainerWebDriverProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class ChromeContainerTest(
    @Autowired
    private val webDriverProvider: TestContainerWebDriverProvider,
) {
    companion object {
        private const val GOOGLE_URL = "https://google.com"
    }

    @Test
    @DisplayName("chromium 테스트 컨테이너 실행 테스트")
    fun testChromium() =
        runTest {
            val driver = webDriverProvider.getChromeDriver()

            driver.get(GOOGLE_URL)

            val wait = WebDriverWait(driver, Duration.ofSeconds(5))

            wait.until {
                val readyState = driver.executeScript("return document.readyState")
                readyState == "complete"
            }

            driver.close()
        }
}
