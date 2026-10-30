package jyk.bcar.automation.job.act.sources.draft

import jyk.bcar.automation.job.act.sources.CharSet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DraftExtractorTest {
    private val extractor = DraftExtractor()

    @Test
    fun parseDraftCarListHtml() = runTest {
        val request = DraftExtractorRequest(
            htmlBytes = loadResourceBytes("draft_car_list_minimal.html"),
            charSet = CharSet.UTF_8,
            baseUri = "https://example.com",
        )

        val parsed = extractor.doAct(request)
        assertEquals(12, parsed.size)

        val first = parsed.first()
        assertEquals("현대 e-카운티", first.title)
        assertEquals("현대", first.company)
        assertEquals("76러4867", first.carNumber)
        assertEquals("(주)이룸모터스", first.agency)
        assertEquals("신도훈", first.seller)
        assertEquals("010-3777-6023", first.sellerPhone)
        assertEquals("53686981", first.detailPageNum)
        assertEquals(1090, first.price)
    }

    @Test
    fun mapGenesisCompanyToHyundai() = runTest {
        val parsed = extractor.doAct(requestOf(singleRowHtml(title = "제네시스 G80", price = "3,500")))
        val first = parsed.first()

        assertEquals("제네시스 G80", first.title)
        assertEquals("현대", first.company)
    }

    @Test
    fun throwWhenPriceIsInvalidFormat() {
        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                extractor.doAct(requestOf(singleRowHtml(title = "현대 e-카운티", price = "가격문의")))
            }
        }

        assertTrue(exception.message?.contains("invalid price format") == true)
    }

    @Test
    fun skipEmptyTrRow() = runTest {
        val html =
            """
            <html>
            <body>
            <table class="t_list mycar">
            <tbody>
            <tr></tr>
            ${singleRowHtmlRow(title = "현대 뉴카운티", price = "2,100")}
            </tbody>
            </table>
            </body>
            </html>
            """.trimIndent()

        val parsed = extractor.doAct(requestOf(html))
        assertEquals(1, parsed.size)
        assertEquals("현대 뉴카운티", parsed.first().title)
    }

    private fun loadResourceBytes(resourceName: String): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: throw IllegalArgumentException("$resourceName not found in test resources")
        return stream.use { it.readAllBytes() }
    }

    private fun requestOf(html: String): DraftExtractorRequest {
        return DraftExtractorRequest(
            htmlBytes = html.toByteArray(Charsets.UTF_8),
            charSet = CharSet.UTF_8,
            baseUri = "https://example.com",
        )
    }

    private fun singleRowHtml(title: String, price: String): String {
        return """
            <html>
            <body>
            <table class="t_list mycar">
            <tbody>
            ${singleRowHtmlRow(title, price)}
            </tbody>
            </table>
            </body>
            </html>
            """.trimIndent()
    }

    private fun singleRowHtmlRow(title: String, price: String): String {
        return """
            <tr>
              <td class="center"><span class="checkbox"><input type="checkbox" name="chk[]" value="11111111"></span>11가1111</td>
              <td></td>
              <td class="carName">
                <a href="/detail/11111111">
                  <strong>$title</strong>
                  <div class="txt_comment type3">오토|검정|테스트단지|테스트상사|홍길동 (010-1111-2222)</div>
                </a>
              </td>
              <td></td>
              <td></td>
              <td></td>
              <td class="right">$price</td>
            </tr>
            """.trimIndent()
    }
}
