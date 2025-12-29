package jyk.bcar

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestConfiguration::class)
class BcarApplicationTests {
    @Test
    fun contextLoads() {}
}
