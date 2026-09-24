package jyk.bcar.automation.job.act.target

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.domain.CategoryTree
import jyk.bcar.domain.UploadSource
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import java.nio.file.Path
import java.util.Base64

/** 등록 폼 한 장을 채운다. submit=false면 결제 직전에 멈춘다 */
class UploadCar(
    private val page: Page,
    private val webClient: WebClient,
) : JobAct<UploadCarRequest, Unit> {
    companion object {
        private const val FORM = "#post-form"
        private const val CATEGORY = "#categoryId"
        private const val COMPANY_LIST = "$CATEGORY > dl.ct_a > dd > ul"
        private const val MODEL_LIST = "$CATEGORY > dl.ct_b > dd > ul"
        private const val DETAIL_LIST = "$CATEGORY > dl.ct_c > dd > ul"
        private const val IMAGE_INPUT = "#file_image"
        private const val IMAGE_PREVIEW = "#post-form img.preview, #post-form .photo_view li img"
        private const val SUBMIT = "$FORM input.submit-btn"
        private const val REDRAW_MS = 300.0

        private val FUEL = mapOf(
            "휘발유" to "gasoline",
            "경유" to "diesel",
            "LPG" to "lpg",
            "CNG" to "cng",
            "전기" to "electric",
            "수소" to "hydrogen",
            "하이브리드" to "hybrid_gasoline",
            "겸용" to "gasoline_lpg",
        )
        private val GEAR = mapOf("오토" to "auto", "수동" to "manual", "CVT" to "cvt")
        private val COLOR = mapOf(
            "검정색" to "c0",
            "검정" to "c0",
            "검정투톤" to "c0",
            "쥐색" to "c2",
            "은색" to "c3",
            "은색투톤" to "c3",
            "은회색" to "c4",
            "회색" to "c4",
            "회색투톤" to "c4",
            "진회색" to "c4",
            "검정쥐색" to "c4",
            "흰색" to "c6",
            "흰색투톤" to "c6",
            "진주색" to "c8",
            "진주투톤" to "c8",
            "베이지" to "c8",
            "은하색" to "c10",
            "갈대색" to "c12",
            "갈색" to "c14",
            "밤색" to "c14",
            "갈색(밤색)" to "c14",
            "금색" to "c16",
            "청색" to "c18",
            "남색" to "c18",
            "군청색" to "c18",
            "진청색" to "c18",
            "청색투톤" to "c18",
            "파랑(남색,곤색)" to "c18",
            "하늘색" to "c19",
            "담녹색" to "c20",
            "녹색" to "c21",
            "녹색투톤" to "c21",
            "초록(연두)" to "c21",
            "연두색" to "c22",
            "청옥색" to "c23",
            "빨강색" to "c24",
            "빨강(주홍)" to "c24",
            "빨강투톤" to "c24",
            "흑장미색" to "c24",
            "주황색" to "c25",
            "자주색" to "c26",
            "자주(보라)" to "c26",
            "보라색" to "c27",
            "분홍색" to "c28",
            "노랑" to "c29",
            "노란색" to "c29",
            "겨자색" to "c29",
        )
        private const val COLOR_ETC = "c99"

        /** 사이트가 받아 주는 사진 수 */
        private const val MAX_IMAGES = 16
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun doAct(input: UploadCarRequest) {
        val source = input.source
        val car = source.car
        val detail = checkNotNull(car.detail) { "no detail for ${car.carNumber}" }

        page.navigate(input.registerUrl, Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE))
        page.waitForSelector(FORM)

        radio("car_type", source.segment.dataValue)
        radio("region", if (source.origin == CategoryTree.Origin.IMPORTED) "0001" else "0000")
        selectCategory(source)

        fill("car_reg_num", car.carNumber)
        val (year, month) = detail.modelYear.split("-")
        select("manufacture_date_year", year)
        select("manufacture_date_month", month)
        fill("mileage", detail.mileage.toString())
        // 소스에 배기량이 없으면 0으로 오는데, 0cc로 올리면 틀린 정보가 된다
        if (detail.displacement > 0) fill("cc", detail.displacement.toString())
        select("fuel_type", FUEL[detail.fuelType] ?: "gasoline")
        radio("gear_type", GEAR[detail.gearBox] ?: "auto")
        selectColor(detail.color)

        val hasAccident = detail.hasAccident == "유사고"
        radio("accident_yn", if (hasAccident) "y" else "n")
        if (hasAccident) fill("accident_description", "-")
        radio("repo", if (detail.hasSeizure) "y" else "n")
        radio("collateral", if (detail.hasMortgage) "y" else "n")

        fill("presented_repo_num", detail.presentationNumber)
        fill("sale_price", input.price.toString())
        fill("description", input.comment)

        attachImages(detail.images)
        usePoints()

        if (!input.submit) {
            // 눈으로 확인할 거리를 남긴다 — 제출 없이는 폼 상태가 사라지므로
            val shot = Path.of(System.getProperty("java.io.tmpdir"), "upload-${car.carNumber}.png")
            page.screenshot(Page.ScreenshotOptions().setPath(shot).setFullPage(true))
            logger.info("폼 작성 완료(제출 안 함): ${car.carNumber} ${car.title} → $shot")
            return
        }

        page.waitForNavigation(Page.WaitForNavigationOptions().setWaitUntil(WaitUntilState.NETWORKIDLE)) {
            page.locator(SUBMIT).click()
        }
    }

    /** 제조사 → 모델 → 세부모델 순서로 눌러야 다음 목록이 채워진다 */
    private fun selectCategory(source: UploadSource) {
        page.waitForSelector(COMPANY_LIST)
        page.locator("$COMPANY_LIST > li.cateid-${source.company.dataValue}").click()
        page.waitForTimeout(REDRAW_MS)

        source.model?.let {
            page.locator("$MODEL_LIST > li.cateid-${it.dataValue}").click()
            page.waitForTimeout(REDRAW_MS)
        }
        source.detailModel?.let {
            page.locator("$DETAIL_LIST > li.cateid-${it.dataValue}").click()
            page.waitForTimeout(REDRAW_MS)
        }
        // 트리에서 못 찾은 만큼은 제목을 그대로 적어 준다
        if (source.model == null || source.detailModel == null) {
            fill("model_name", source.car.title)
        }
    }

    private fun selectColor(color: String) {
        val code = COLOR[color] ?: COLOR_ETC
        select("color", code)
        if (code == COLOR_ETC) fill("color_str", color.ifBlank { "-" })
    }

    /** 소스 이미지는 다른 도메인이라 페이지 안에서 fetch하면 CORS에 막힌다. 앱이 받아서 base64로 넘긴다 */
    private suspend fun attachImages(images: List<String>) {
        val encoded = images.take(MAX_IMAGES).mapNotNull { url ->
            val bytes = webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .awaitFirstOrNull()
                ?: return@mapNotNull null
            mapOf(
                "base64" to Base64.getEncoder().encodeToString(bytes),
                "ext" to url.substringAfterLast('.', "jpg").take(4),
            )
        }
        if (encoded.isEmpty()) {
            logger.warn("이미지를 하나도 받지 못했다 (${images.size}건 시도)")
            return
        }

        page.evaluate(
            """
            ([selector, files]) => {
              const transfer = new DataTransfer()
              files.forEach(({ base64, ext }, i) => {
                const binary = atob(base64)
                const bytes = new Uint8Array(binary.length).map((_, j) => binary.charCodeAt(j))
                transfer.items.add(new File([bytes], i + '.' + ext, { type: 'image/' + ext }))
              })
              const input = document.querySelector(selector)
              input.files = transfer.files
              input.dispatchEvent(new Event('change', { bubbles: true }))
            }
            """.trimIndent(),
            listOf(IMAGE_INPUT, encoded),
        )
        page.waitForSelector(IMAGE_PREVIEW)
    }

    private fun usePoints() {
        val usePoint = page.locator("""$FORM input[name="usepoint"]""")
        if (usePoint.count() > 0) usePoint.check()
    }

    // 값이 숫자로 시작하면 따옴표 없이는 CSS 선택자가 아니다 (value=0000)
    private fun radio(name: String, value: String) = page.locator("""$FORM input[name="$name"][value="$value"]""").check()

    private fun select(name: String, value: String) = page.locator("""$FORM [name="$name"]""").selectOption(value)

    private fun fill(name: String, value: String) = page.locator("""$FORM [name="$name"]""").fill(value)
}

data class UploadCarRequest(
    val source: UploadSource,
    val registerUrl: String,
    /** 마진까지 얹은 판매가(만원) */
    val price: Int,
    val comment: String,
    /** false면 결제 직전까지만 */
    val submit: Boolean = true,
)
