package jyk.bcar

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import com.google.api.services.sheets.v4.Sheets
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class BcarApplicationTests {
    @MockitoBean
    lateinit var sheets: Sheets

    @Test
    fun contextLoads() {}
}
