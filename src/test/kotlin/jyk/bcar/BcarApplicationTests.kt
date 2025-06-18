package jyk.bcar

import jyk.bcar.runner.FakeRunner
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [FakeRunner::class],
    args = ["fakeRunner"],
)
class BcarApplicationTests {
    @Test
    fun contextLoads() {}
}
