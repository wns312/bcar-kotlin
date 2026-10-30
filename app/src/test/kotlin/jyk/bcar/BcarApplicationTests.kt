package jyk.bcar

import jyk.bcar.automation.job.runner.JobRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestConfiguration::class)
class BcarApplicationTests {
    @Autowired
    private lateinit var registry: JobRegistry

    @Test
    fun contextLoadsWithAllFiveJobsRegistered() {
        assertEquals(
            setOf("collect-draft", "collect-detail", "assign-cars", "sync-upload", "collect-category"),
            registry.names(),
        )
    }
}
