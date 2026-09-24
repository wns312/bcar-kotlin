package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.sources.CharSet
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailExtractorTest {
    private val extractor = DetailExtractor()

    @Test
    fun parseDetailBox() = runTest {
        val detail = extractor.doAct(
            DetailExtractorRequest(
                htmlBytes = detailHtml().toByteArray(Charsets.UTF_8),
                charSet = CharSet.UTF_8,
                baseUri = "http://thebestcar.kr",
            ),
        )

        assertEquals("대형", detail.category)
        assertEquals(2970, detail.displacement)
        assertEquals("2019-03", detail.modelYear)
        assertEquals(123456, detail.mileage)
        assertEquals("검정", detail.color)
        assertEquals("오토", detail.gearBox)
        assertEquals("경유", detail.fuelType)
        assertEquals("제시123", detail.presentationNumber)
        assertEquals("무사고", detail.hasAccident)
        assertEquals("등록-77", detail.registerNumber)
        assertEquals("2025-02-01", detail.presentationsDate)
        assertTrue(detail.hasSeizure)
        assertFalse(detail.hasMortgage)
        assertEquals("https://check.example.com/report/123", detail.carCheckSrc)
        assertEquals(listOf("/upload/car1.jpg", "https://cdn.example.com/car2.jpg", "/upload/car3.jpg", "/upload/car4.jpg"), detail.images)
    }

    @Test
    fun rejectTakeoverListing() {
        // 소스가 제목에 "리스/렌트 승계차량"이라 적어 준다. 표시가는 차값이 아니라 승계 조건이다
        val html = detailHtml().replace(
            "<div id=\"detail_box\">",
            "<h2>2023 BMW i4 eDrive40 리스/렌트 승계차량, 조건은 엔카에서 확인</h2><div id=\"detail_box\">",
        )
        assertThrows(TakeoverListing::class.java) {
            runTest {
                extractor.doAct(
                    DetailExtractorRequest(html.toByteArray(Charsets.UTF_8), CharSet.UTF_8, "http://thebestcar.kr"),
                )
            }
        }
    }

    @Test
    fun throwWhenDetailBoxMissing() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runTest {
                extractor.doAct(
                    DetailExtractorRequest(
                        htmlBytes = "<html><body>no detail box</body></html>".toByteArray(Charsets.UTF_8),
                        charSet = CharSet.UTF_8,
                        baseUri = "http://thebestcar.kr",
                    ),
                )
            }
        }

        assertTrue(exception.message?.contains("missing #detail_box") == true)
    }

    private fun detailHtml(): String {
        val keys = listOf(
            "차종",
            "배기량",
            "차량번호",
            "연식",
            "주행거리",
            "색상",
            "변속기",
            "연료",
            "제시번호",
            "사고유무",
            "등록번호",
            "제시일",
            "압류 / 저당",
        )

        val values = listOf(
            "대형",
            "2,970cc",
            "99가1234",
            "2019년 03월",
            "123,456Km",
            "검정",
            "오토",
            "경유",
            "제시123",
            "무사고",
            "등록-77",
            "2025-02-01",
            "있음 / 없음",
        )

        val topRows = keys.zip(values).joinToString(separator = "") { (key, value) ->
            "<li><span class=\"tit\">$key</span><span class=\"txt\">$value</span></li>"
        }

        val filler2To15 = (2..15).joinToString(separator = "") { "<div>f$it</div>" }
        val filler17To20 = (17..20).joinToString(separator = "") { "<div>f$it</div>" }

        return """
            <html>
            <body>
              <div id="detail_box">
                <div class="right">
                  <div>
                    <div class="carContTop">
                      <ul>$topRows</ul>
                    </div>
                  </div>
                </div>
                $filler2To15
                <div>
                  <a><img src="/images/nophoto.jpg"></a>
                  <a><img src="/upload/car1.jpg"></a>
                  <a><img src="https://cdn.example.com/car2.jpg"></a>
                  <a><img src="/upload/car3.jpg"></a>
                  <a><img src="/upload/car4.jpg"></a>
                </div>
                $filler17To20
                <div>
                  <iframe src="https://check.example.com/report/123"></iframe>
                </div>
              </div>
            </body>
            </html>
            """.trimIndent()
    }
}
