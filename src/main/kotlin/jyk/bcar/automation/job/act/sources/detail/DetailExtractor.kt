package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.domain.CarDetail
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.slf4j.LoggerFactory

class DetailExtractor(
    private val detailDocumentParser: DetailDocumentParser = DetailDocumentParser(),
) : JobAct<DetailExtractorRequest, CarDetail> {
    companion object {
        private val logger = LoggerFactory.getLogger(DetailExtractor::class.java)

        /**
         * 소스가 리스·렌트 매물임을 적어 주는 표현. 2026-09-24 prod 원문 4종:
         * "리스/렌트 승계차량", "신한카드 리스차량입니다", "운용리스보증금...", "신한카드 장기렌트 차량입니다".
         * `승계`를 요구하면 뒤 셋을 놓친다. 네비게이션의 "상담리스트"는 `리스` 뒤가 `트`라 걸리지 않는다
         */
        private val TAKEOVER = Regex("(리스|렌트)\\s*(승계|차량)|운용\\s?리스|장기\\s?렌트")

        /** TAKEOVER가 놓친 매물을 소스 재요청 없이 판정하려면 원문 표현이 로그에 남아 있어야 한다 */
        private val TAKEOVER_HINT = Regex(".{0,25}(리스(?!트)|렌트|승계|인수|월\\s?납).{0,25}")

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
        val text = document.text()
        // 리스·렌트 매물은 표시가가 차값이 아니라 월 납입금이라(2024년식 마이바흐 S680이 259만원) 그대로 올리면 허위 시세가 된다
        if (TAKEOVER.containsMatchIn(text)) throw TakeoverListing()
        val hints = TAKEOVER_HINT
            .findAll(text)
            .map { it.value.trim() }
            .take(5)
            .toList()
        if (hints.isNotEmpty()) logger.info("상세 통과, 리스 단서 남음: {}", hints)
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

/** 리스·렌트 승계 매물. 페이지가 깨진 게 아니라 우리가 다루지 않는 차다 */
class TakeoverListing : IllegalStateException("리스·렌트 승계 매물")

class DetailExtractorRequest(
    val htmlBytes: ByteArray,
    val charSet: CharSet,
    val baseUri: String,
)
