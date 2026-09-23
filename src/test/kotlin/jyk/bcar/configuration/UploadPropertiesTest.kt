package jyk.bcar.configuration

import jyk.bcar.domain.CarCategory.DOMESTIC_OVER_1300
import jyk.bcar.domain.CarCategory.DOMESTIC_UNDER_1300
import jyk.bcar.domain.CarCategory.IMPORTED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class UploadPropertiesTest {
    private val properties = UploadProperties(
        commentFile = ClassPathResource("upload-comment.txt"),
        margins = listOf(
            UploadProperties.Margin(maxPrice = 2000, domestic = 40, imported = 60),
            UploadProperties.Margin(maxPrice = 5000, domestic = 60, imported = 60),
        ),
    )

    @Test
    fun takesMarginOfFirstMatchingBandByOrigin() {
        assertEquals(40, properties.marginFor(1500, DOMESTIC_UNDER_1300))
        assertEquals(60, properties.marginFor(1500, IMPORTED))
        assertEquals(60, properties.marginFor(4000, DOMESTIC_OVER_1300))
        assertEquals(0, properties.marginFor(9000, DOMESTIC_OVER_1300))
    }

    @Test
    fun readsCommentFromClasspath() {
        assertTrue(properties.comment.startsWith("★☆ 안녕하세요"))
        assertTrue(properties.comment.endsWith("http://bestcha.kr"))
    }
}
