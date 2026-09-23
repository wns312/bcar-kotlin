package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.target.CollectCategoryTree
import jyk.bcar.automation.job.act.target.TargetAdminLogin
import jyk.bcar.automation.job.result.CollectCategoryResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 대상 사이트의 분류 트리를 훑어 저장한다. 업로드 폼이 제조사·모델을 data-value로 고르기 때문에 필요하다.
 * 파이프라인에 끼우지 않는다 — 트리는 거의 안 바뀌는데 매 launch마다 돌면 업로드만 늦어진다.
 */
@Component
class CollectCategoryJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
) : AutomationJob<CollectCategoryResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "collect-category"

    override suspend fun execute(): CollectCategoryResult = withContext(Dispatchers.IO) {
        // 트리는 계정과 무관하다. 로그인만 되면 되므로 아무 유저나 쓴다
        val user = userRepository.findAllTargetAdminUsers().firstOrNull()
            ?: return@withContext CollectCategoryResult(success = false, message = "no target admin users")

        val tree = runner.withSession { session ->
            session.usePage { page ->
                TargetAdminLogin(page).doAct(user)
                page.navigate(user.registerUrl)
                CollectCategoryTree(page).doAct(Unit)
            }
        }
        carRepository.saveCategoryTree(tree)

        logger.info("Category tree saved from ${user.targetSite}")
        CollectCategoryResult(
            companies = tree.companies.size,
            models = tree.modelCount,
            detailModels = tree.detailModelCount,
            message = "companies=${tree.companies.size} models=${tree.modelCount} detailModels=${tree.detailModelCount}",
        )
    }
}
