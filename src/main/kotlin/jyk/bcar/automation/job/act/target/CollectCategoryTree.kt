package jyk.bcar.automation.job.act.target

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import jyk.bcar.automation.job.act.JobAct
import jyk.bcar.domain.CategoryTree
import org.slf4j.LoggerFactory

/**
 * 등록 폼의 분류 목록을 훑는다. 세그먼트를 고르면 제조사가, 제조사를 고르면 모델이, 모델을 고르면 세부모델이 채워진다.
 * 수입차는 제조사까지만 필요해서 모델은 국산만 판다.
 */
class CollectCategoryTree(
    private val page: Page,
) : JobAct<Unit, CategoryTree> {
    companion object {
        private const val SEGMENT_LABELS = "#post-form > table:nth-child(10) > tbody > tr:nth-child(1) > td > label"
        private const val ORIGIN_LABELS = "#post-form > table:nth-child(10) > tbody > tr:nth-child(2) > td > p > label"
        private const val COMPANIES = "#categoryId > dl.ct_a > dd > ul > li"
        private const val MODELS = "#categoryId > dl.ct_b > dd > ul > li"
        private const val DETAIL_MODELS = "#categoryId > dl.ct_c > dd > ul > li"

        /** 목록이 JS로 다시 그려지는 동안 */
        private const val REDRAW_MS = 300.0
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun doAct(input: Unit): CategoryTree {
        page.waitForSelector(SEGMENT_LABELS)
        val segments = readSegments()
        logger.info("Segments: ${segments.map { it.name }}")

        val companies = linkedMapOf<String, CategoryTree.Company>()
        for (segment in segments) {
            selectSegment(segment.index)
            for (company in readCompanies(CategoryTree.Origin.DOMESTIC)) {
                // "기타"는 모델 목록이 없다
                if (company.name == "기타") {
                    companies.putIfAbsent(company.name, company)
                    continue
                }
                val models = readModels(company, segment.name)
                val merged = companies[company.name] ?: company
                companies[company.name] = merged.copy(models = merged.models + models)
            }
            logger.info("${segment.name}: 제조사 ${companies.size}, 모델 ${companies.values.sumOf { it.models.size }}")
        }

        selectOrigin(imported = true)
        selectSegment(segments.first().index)
        readCompanies(CategoryTree.Origin.IMPORTED).forEach { companies.putIfAbsent(it.name, it) }

        return CategoryTree(segments = segments, companies = companies.values.toList())
    }

    private fun readSegments(): List<CategoryTree.Segment> {
        val labels = page.locator(SEGMENT_LABELS)
        // 마지막 라벨은 분류가 아니다
        return (0 until labels.count() - 1).map { i ->
            val label = labels.nth(i)
            CategoryTree.Segment(
                name = label.textContent().trim(),
                dataValue = label
                    .locator("input")
                    .first()
                    .getAttribute("value")
                    .orEmpty(),
                index = i + 1,
            )
        }
    }

    private fun selectSegment(index: Int) {
        page.locator("$SEGMENT_LABELS:nth-child($index) > input").click()
        page.waitForSelector(COMPANIES)
    }

    private fun selectOrigin(imported: Boolean) {
        page.locator("$ORIGIN_LABELS:nth-child(${if (imported) 2 else 1})").click()
        page.waitForTimeout(REDRAW_MS)
    }

    private fun readCompanies(origin: CategoryTree.Origin): List<CategoryTree.Company> =
        page.locator(COMPANIES).entries().map { (i, li) ->
            CategoryTree.Company(
                name = li.textContent().trim(),
                dataValue = li.getAttribute("data-value").orEmpty(),
                index = i + 1,
                origin = origin,
            )
        }

    private fun readModels(company: CategoryTree.Company, segment: String): List<CategoryTree.Model> {
        page.locator("$COMPANIES:nth-child(${company.index})").click()
        page.waitForTimeout(REDRAW_MS)

        val models = page.locator(MODELS)
        // 마지막은 "기타" — 세부모델이 없다
        return (0 until (models.count() - 1).coerceAtLeast(0)).map { i ->
            val li = models.nth(i)
            val model = CategoryTree.Model(
                name = li.textContent().trim(),
                dataValue = li.getAttribute("data-value").orEmpty(),
                index = i + 1,
                segment = segment,
            )
            li.click()
            page.waitForTimeout(REDRAW_MS)
            model.copy(detailModels = readDetailModels())
        }
    }

    private fun readDetailModels(): List<CategoryTree.DetailModel> =
        page.locator(DETAIL_MODELS).entries().map { (i, li) ->
            CategoryTree.DetailModel(
                // "쏘나타 (2015~)" 처럼 괄호 안내가 붙는다
                name = li.textContent().trim().substringBefore(" ("),
                dataValue = li.getAttribute("data-value").orEmpty(),
                index = i + 1,
            )
        }

    private fun Locator.entries(): List<Pair<Int, Locator>> = (0 until count()).map { it to nth(it) }
}
