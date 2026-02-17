package jyk.bcar.automation.job.act.sources.draft

import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.domain.DraftCar
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.ByteArrayInputStream

class DraftExtractor : DraftAct<DraftExtractorRequest, List<DraftCar>> {
    private data class ExtractedInfo(
        val agency: String,
        val seller: String,
        val sellerPhone: String,
    )

    /**
     * baseUri: 문서에 상대경로로 들어간 링크를 baseUri를 붙여줌
     * */
    override suspend fun doAct(input: DraftExtractorRequest): List<DraftCar> {
        val document = Jsoup.parse(
            // in =
            ByteArrayInputStream(input.htmlBytes),
            // charsetName =
            input.charSet.charsetName,
            // baseUri =
            input.baseUri,
        )

        return document
            .select("table.t_list.mycar tbody tr")
            .mapNotNull { row ->
                val tds = row.select("td")
                if (tds.isEmpty()) return@mapNotNull null
                extractDraftCar(tds)
            }
    }

    private fun extractDraftCar(tds: Elements): DraftCar {
        val td0 = requireTd(tds, 0)
        val td2 = requireTd(tds, 2)
        val td6 = requireTd(tds, 6)

        val info = extractInfo(td2)
        val title = extractTitle(td2)
        val company = extractCompany(title)

        return DraftCar(
            title = title,
            company = company,
            carNumber = extractCarNumber(td0),
            agency = info.agency,
            seller = info.seller,
            sellerPhone = info.sellerPhone,
            detailPageNum = extractDetailPageNum(td0),
            price = extractPrice(td6),
        )
    }

    private fun extractInfo(td2: Element): ExtractedInfo {
        val infoText = requireText(
            requireElement(
                td2.selectFirst("a > div.txt_comment.type3"),
                "info text element",
            ).text(),
            "info text",
        )
        val infoArr = infoText.split("|")
        val agency = requireListItem(infoArr, 3, "agency")
        val rawSeller = requireListItem(infoArr, 4, "seller raw").split("(")
        val seller = requireListItem(rawSeller, 0, "seller")
        val sellerPhone = requireText(
            rawSeller.getOrNull(1)?.replace(")", ""),
            "sellerPhone",
        )

        return ExtractedInfo(
            agency = agency,
            seller = seller,
            sellerPhone = sellerPhone,
        )
    }

    private fun extractTitle(td2: Element): String {
        return requireText(
            requireElement(
                td2.selectFirst("a > strong"),
                "title element",
            ).text(),
            "title",
        )
    }

    private fun extractCompany(title: String): String {
        val rawCompany = requireText(title.split(" ").firstOrNull(), "company")
        return if (rawCompany != "제네시스") rawCompany else "현대"
    }

    private fun extractCarNumber(td0: Element): String {
        return requireText(td0.ownText(), "carNumber")
    }

    private fun extractDetailPageNum(td0: Element): String {
        return requireText(
            requireElement(
                td0.selectFirst("span.checkbox > input"),
                "detailPageNum element",
            ).attr("value"),
            "detailPageNum",
        )
    }

    private fun extractPrice(td6: Element): Int {
        val rawPrice = requireText(td6.ownText(), "price")
        return requireNotNull(rawPrice.replace(",", "").toIntOrNull()) {
            "DraftExtractor: invalid price format"
        }
    }

    private fun requireTd(tds: Elements, index: Int): Element {
        return requireNotNull(tds.getOrNull(index)) {
            "DraftExtractor: missing td[$index]"
        }
    }

    private fun requireElement(element: Element?, field: String): Element {
        return requireNotNull(element) {
            "DraftExtractor: missing $field"
        }
    }

    private fun requireText(value: String?, field: String): String {
        val text = value?.trim()
        require(text.isNullOrEmpty().not()) { "DraftExtractor: missing $field" }
        return text
    }

    private fun requireListItem(list: List<String>, index: Int, field: String): String {
        return requireText(list.getOrNull(index), field)
    }
}

class DraftExtractorRequest(
    val htmlBytes: ByteArray,
    val charSet: CharSet,
    val baseUri: String,
)
