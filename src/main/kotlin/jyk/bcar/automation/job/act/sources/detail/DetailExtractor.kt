package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.domain.CarDetail
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class DetailExtractor(
    private val detailDocumentParser: DetailDocumentParser = DetailDocumentParser(),
) : JobAct<DetailExtractorRequest, CarDetail> {
    companion object {
        private const val DETAIL_BOX_SELECTOR = "#detail_box"
        private const val TOP_KEY_SELECTOR = "div.right div div.carContTop ul li span.tit"
        private const val TOP_VALUE_SELECTOR = "div.right div div.carContTop ul li span.txt"
        private const val PRIMARY_IMAGE_SELECTOR = "div:nth-child(16) a img[src]"
        private const val FALLBACK_IMAGE_SELECTOR = "a img[src]"
        private const val PRIMARY_CAR_CHECK_SELECTOR = "div:nth-child(21) iframe[src]"
        private const val FALLBACK_CAR_CHECK_SELECTOR = "iframe[src]"
        private val DETAIL_KEYS = listOf(
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
    }

    override suspend fun doAct(input: DetailExtractorRequest): CarDetail {
        val document = detailDocumentParser.doAct(input)
        val detailBox = requireNotNull(document.selectFirst(DETAIL_BOX_SELECTOR)) {
            "DetailExtractor: missing #detail_box"
        }

        val topValues = parseTopValues(detailBox)
        val images = extractImages(detailBox)
        val carCheckSrc = extractCarCheckSrc(detailBox)

        return CarDetail(
            category = topValues.category,
            displacement = parseNumeric(topValues.displacement, "cc"),
            modelYear = normalizeModelYear(topValues.modelYear),
            mileage = parseNumeric(topValues.mileage, "km"),
            color = topValues.color,
            gearBox = topValues.gearBox,
            fuelType = topValues.fuelType,
            presentationNumber = topValues.presentationNumber,
            hasAccident = topValues.hasAccident,
            registerNumber = topValues.registerNumber,
            presentationsDate = topValues.presentationsDate,
            hasSeizure = topValues.hasSeizure,
            hasMortgage = topValues.hasMortgage,
            carCheckSrc = carCheckSrc,
            images = images,
        )
    }

    private fun parseTopValues(detailBox: Element): ParsedTopValues {
        val keys = extractTextList(detailBox.select(TOP_KEY_SELECTOR))
        validateTopKeys(keys)

        val values = extractTextList(detailBox.select(TOP_VALUE_SELECTOR))
        val seizureAndMortgage = values.getOrNull(12) ?: throw IllegalStateException("no seizureAndMortgage")
        val (hasSeizure, hasMortgage) = parseSeizureAndMortgage(seizureAndMortgage)

        return ParsedTopValues(
            category = values.getOrNull(0) ?: throw IllegalStateException("no category"),
            displacement = values.getOrNull(1) ?: throw IllegalStateException("no displacement"),
            modelYear = values.getOrNull(3) ?: throw IllegalStateException("no modelYear"),
            mileage = values.getOrNull(4) ?: throw IllegalStateException("no mileage"),
            color = values.getOrNull(5) ?: throw IllegalStateException("no color"),
            gearBox = values.getOrNull(6) ?: throw IllegalStateException("no gearBox"),
            fuelType = values.getOrNull(7) ?: throw IllegalStateException("no fuelType"),
            presentationNumber = values.getOrNull(8) ?: throw IllegalStateException("no presentationNumber"),
            hasAccident = values.getOrNull(9) ?: throw IllegalStateException("no hasAccident"),
            registerNumber = values.getOrNull(10) ?: throw IllegalStateException("no registerNumber"),
            presentationsDate = values.getOrNull(11) ?: throw IllegalStateException("no presentationsDate"),
            hasSeizure = hasSeizure,
            hasMortgage = hasMortgage,
        )
    }

    private fun validateTopKeys(keys: List<String>) {
        if (keys.size < DETAIL_KEYS.size) return

        val keyMismatch = DETAIL_KEYS.indices.any { idx -> DETAIL_KEYS[idx] != keys[idx] }
        require(!keyMismatch) {
            "DetailExtractor: detail key info is not correct. expected=$DETAIL_KEYS, actual=$keys"
        }
    }

    private fun extractTextList(elements: Elements): List<String> {
        return elements.map { it.text().trim() }
    }

    private fun parseSeizureAndMortgage(value: String): Pair<Boolean, Boolean> {
        val parsed = value.split(" / ").map { it.trim() }
        val hasSeizure = parsed.getOrNull(0) == "있음"
        val hasMortgage = parsed.getOrNull(1) == "있음"
        return hasSeizure to hasMortgage
    }

    private fun extractImages(detailBox: Element): List<String> {
        val images = extractImageSources(detailBox, PRIMARY_IMAGE_SELECTOR).ifEmpty {
            extractImageSources(detailBox, FALLBACK_IMAGE_SELECTOR)
        }

        return images.filter { image ->
            // Match the legacy TS filtering behavior.
            !image.startsWith("/") || !image.contains("nophoto")
        }
    }

    private fun extractImageSources(
        detailBox: Element,
        selector: String,
    ): List<String> {
        return detailBox
            .select(selector)
            .mapNotNull { element -> element.attr("src").trim().takeIf { it.isNotEmpty() } }
    }

    private fun extractCarCheckSrc(detailBox: Element): String {
        val primary = detailBox
            .select(PRIMARY_CAR_CHECK_SELECTOR)
            .firstOrNull()
            ?.attr("src")
            .orEmpty()
            .trim()
        if (primary.isNotEmpty()) return primary
        return detailBox
            .select(FALLBACK_CAR_CHECK_SELECTOR)
            .firstOrNull()
            ?.attr("src")
            .orEmpty()
            .trim()
    }

    private fun normalizeModelYear(value: String): String {
        return value
            .trim()
            .replace("년", "")
            .replace("월", "")
            .replace(" ", "-")
    }

    private fun parseNumeric(value: String, unit: String): Int {
        val normalized = value
            .replace(",", "")
            .replace(unit, "", ignoreCase = true)
            .replace("-", "0")
            .trim()

        return normalized.toIntOrNull() ?: 0
    }

    private data class ParsedTopValues(
        val category: String,
        val displacement: String,
        val modelYear: String,
        val mileage: String,
        val color: String,
        val gearBox: String,
        val fuelType: String,
        val presentationNumber: String,
        val hasAccident: String,
        val registerNumber: String,
        val presentationsDate: String,
        val hasSeizure: Boolean,
        val hasMortgage: Boolean,
    )
}

class DetailExtractorRequest(
    val htmlBytes: ByteArray,
    val charSet: CharSet,
    val baseUri: String,
)
