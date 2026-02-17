package jyk.bcar.automation.job.act.draft

import jyk.bcar.automation.job.act.sources.CarType
import jyk.bcar.automation.job.act.sources.draft.CollectCarListRequest
import jyk.bcar.automation.job.act.sources.draft.CollectDraftCarList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentLinkedQueue

class CollectDraftCarListTest {
    @Test
    fun fetchEveryPageAndMergeResults() = runTest {
        val requests = ConcurrentLinkedQueue<ClientRequest>()
        val webClient = WebClient
            .builder()
            .exchangeFunction { request ->
                requests.add(request)

                val response = ClientResponse
                    .create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=US-ASCII")
                    .body(
                        Flux.just(
                            DefaultDataBufferFactory.sharedInstance.wrap(
                                singleRowHtml().toByteArray(Charsets.US_ASCII),
                            ),
                        ),
                    ).build()

                Mono.just(response)
            }.build()

        val sut = CollectDraftCarList(
            webClient = webClient,
            cookieHeader = "SESSION=unit-test",
        )
        val parsed = sut.doAct(
            CollectCarListRequest(
                carType = CarType.BUS,
                minPrice = 1000,
                maxPrice = 5000,
                pageRange = 1..3,
            ),
        )

        assertEquals(3, parsed.size)
        assertEquals(3, requests.size)

        val requestedUrls = requests.map { it.url().toString() }
        (1..3).forEach { page ->
            assertTrue(
                requestedUrls.any { url ->
                    url.contains("/mypage/_inc_carList.html?searchChecker=1&listView=y&pageSize=100") &&
                        url.contains("&c_price1=1000") &&
                        url.contains("&c_price2=5000") &&
                        url.contains("&c_cho=4&page=$page")
                },
            )
        }

        requests.forEach { request ->
            assertEquals("SESSION=unit-test", request.headers().getFirst("Cookie"))
            val referer = request.headers().getFirst("Referer")
            assertNotNull(referer)
            assertTrue(referer!!.contains("/mypage/mycar.html?searchChecker=1&listView=y&pageSize=100"))
            assertTrue(referer.contains("&c_cho=4"))
        }
    }

    private fun singleRowHtml(): String {
        return """
            <html>
            <body>
            <table class="t_list mycar">
            <tbody>
              <tr>
                <td class="center"><span class="checkbox"><input type="checkbox" name="chk[]" value="99999999"></span>11A1111</td>
                <td></td>
                <td class="carName"><a href="javascript:;"><strong>HYUNDAI TEST</strong><div class="txt_comment type3">AUTO|BLACK|YARD|AGENCY|SELLER (010-0000-0000)</div></a></td>
                <td></td>
                <td></td>
                <td></td>
                <td class="right">1,000</td>
              </tr>
            </tbody>
            </table>
            </body>
            </html>
            """.trimIndent()
    }
}
