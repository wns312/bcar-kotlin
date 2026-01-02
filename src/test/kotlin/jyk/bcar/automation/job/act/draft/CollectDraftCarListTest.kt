package jyk.bcar.automation.job.act.draft

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class CollectDraftCarListTest {
    @Test
    fun parseDraftCarListHtml() = runTest {
        val stream = javaClass.classLoader.getResourceAsStream("draft_car_list_minimal.html")
            ?: fail("draft_car_list.html not found in test resources")
        val request = DraftExtractorRequest(
            htmlBytes = stream.readAllBytes(),
            charSet = DraftExtractor.CharSet.UTF_8,
            baseUri = "baseUri",
        )
        val parsed = DraftExtractor().doAct(request)

        assertEquals(12, parsed.size)

        val first = parsed.firstOrNull() ?: fail("no car rows parsed")
        assertEquals("현대 e-카운티", first.title)
        assertEquals("현대", first.company)
        assertEquals("76러4867", first.carNumber)
        assertEquals("(주)이룸모터스", first.agency)
        assertEquals("신도훈", first.seller)
        assertEquals("010-3777-6023", first.sellerPhone)
        assertEquals("53686981", first.detailPageNum)
        assertEquals(1090, first.price)
    }
}
