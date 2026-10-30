package jyk.bcar.source

import jyk.bcar.automation.job.act.target.CollectCategoryTree
import jyk.bcar.automation.job.act.target.TargetAdminLogin
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.domain.CategoryTree
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.port.CategoryTreeSource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlaywrightCategoryTreeSource(
    private val runner: PlaywrightSessionRunner,
) : CategoryTreeSource {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val FALLBACK_PRODUCT = "car-normal-60"
    }

    override suspend fun collect(user: TargetAdminUser): CategoryTree {
        return runner.withSession { session ->
            session.usePage { page ->
                TargetAdminLogin(page).doAct(user)
                page.navigate(user.registerUrl)
                // 한도를 다 쓴 계정이면 상품 선택 페이지로 튕긴다. 트리만 읽을 거라 유료 상품 폼을 열어도 결제되지 않는다
                if (page.url().contains("car_product")) {
                    logger.info("등록 한도 소진 계정 — 상품 지정으로 폼만 연다")
                    page.navigate("${user.registerUrl}&products=$FALLBACK_PRODUCT")
                }
                CollectCategoryTree(page).doAct(Unit)
            }
        }
    }
}
