package jyk.bcar.automation.job.act.draft

import jyk.bcar.automation.job.act.draft.DraftExtractor.CharSet
import jyk.bcar.domain.DraftCar
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

class DraftExtractor : DraftAct<DraftExtractorRequest, List<DraftCar>> {
    enum class CharSet(
        val charsetName: String,
    ) {
        EUC_KR("EUC-KR"),
        UTF_8("UTF-8"),
    }

    /**
     * baseUri: 문서에 상대경로로 들어간 링크를 baseUri를 붙여줌
     * */
    override suspend fun doAct(input: DraftExtractorRequest): List<DraftCar> {
        val (htmlBytes, charSet, baseUri) = input
        val document = Jsoup.parse(ByteArrayInputStream(htmlBytes), charSet.charsetName, baseUri)

        return document
            .select("table.t_list.mycar tbody tr")
            .filter { it.select("td").isNotEmpty() }
            .map { row ->
                val tds = row.select("td")
                val infoArr = tds[2]
                    .selectFirst("a > div.txt_comment.type3")
                    ?.text()
                    ?.trim()
                    ?.split("|")
                    ?: emptyList()
                val agency = infoArr.getOrNull(3)?.trim().orEmpty()
                val rawSeller = infoArr.getOrNull(4)?.split("(") ?: emptyList()
                val seller = rawSeller.getOrNull(0)?.trim().orEmpty()
                val sellerPhone = rawSeller
                    .getOrNull(1)
                    ?.replace(")", "")
                    ?.trim()
                    .orEmpty()
                val title = tds[2]
                    .selectFirst("a > strong")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                val rawCompany = title.split(" ").firstOrNull().orEmpty()
                val company = if (rawCompany != "제네시스") rawCompany else "현대"
                val carNumber = tds[0].ownText().trim()
                val detailPageNum = tds[0]
                    .selectFirst("span.checkbox > input")
                    ?.attr("value")
                    .orEmpty()
                val price = tds[6]
                    .ownText()
                    .replace(",", "")
                    .trim()
                    .toInt()

                DraftCar(
                    title = title,
                    company = company,
                    carNumber = carNumber,
                    agency = agency,
                    seller = seller,
                    sellerPhone = sellerPhone,
                    detailPageNum = detailPageNum,
                    price = price,
                )
            }
    }
}

data class DraftExtractorRequest(
    val htmlBytes: ByteArray,
    val charSet: CharSet,
    val baseUri: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DraftExtractorRequest

        if (!htmlBytes.contentEquals(other.htmlBytes)) return false
        if (charSet != other.charSet) return false
        if (baseUri != other.baseUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = htmlBytes.contentHashCode()
        result = 31 * result + charSet.hashCode()
        result = 31 * result + baseUri.hashCode()
        return result
    }
}
