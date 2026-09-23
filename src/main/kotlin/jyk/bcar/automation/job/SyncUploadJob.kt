package jyk.bcar.automation.job

import jyk.bcar.automation.job.act.target.SyncUploadedCars
import jyk.bcar.automation.job.act.target.SyncUploadedCarsRequest
import jyk.bcar.automation.job.act.target.TargetAdminLogin
import jyk.bcar.automation.job.result.SyncUploadResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.domain.UploadStatus
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 유저 한 명의 매물을 사이트와 맞춘다. 사이트 부하 때문에 한 번에 한 명씩만 돌고,
 * 다음 유저는 JobChainDecider가 이 결과를 보고 제출한다.
 */
@Component
class SyncUploadJob(
    private val runner: PlaywrightSessionRunner,
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val args: ApplicationArguments,
) : AutomationJob<SyncUploadResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "sync-upload"

    override suspend fun execute(): SyncUploadResult = withContext(Dispatchers.IO) {
        val users = userRepository.findAllTargetAdminUsers()
        val userId = args.getOptionValues("user")?.firstOrNull()
        val index = users.indexOfFirst { it.id == userId }
        // 시트에 없는 유저를 동기화하면 그 계정 매물을 통째로 지우게 된다
        check(index >= 0) { "Unknown --user '$userId'. Sheet has ${users.size} users." }

        val user = users[index]
        val delete = boolArg("delete", default = true)
        val cars = carRepository.findByAssignedUser(user.id)
        val expected = cars.filter { it.uploadStatus != UploadStatus.NEEDS_REMOVAL }
        logger.info(
            "Syncing ${user.id} (${user.targetSite}): 할당 ${cars.size}대, 사이트에 있어야 할 ${expected.size}대, delete=$delete",
        )

        val synced = runner.withSession { session ->
            session.usePage { page ->
                TargetAdminLogin(page).doAct(user)
                SyncUploadedCars(page).doAct(
                    SyncUploadedCarsRequest(
                        manageUrl = user.manageUrl,
                        expected = expected.mapTo(HashSet()) { it.carNumber },
                        delete = delete,
                    ),
                )
            }
        }

        val now = Instant.now()
        val updates = cars.mapNotNull { it.syncedWith(onSite = it.carNumber in synced.found, now = now) }
        carRepository.saveAll(updates)

        val released = updates.count { it.assignedUserId == null }
        SyncUploadResult(
            userId = user.id,
            nextUserId = users.getOrNull(index + 1)?.id,
            onSite = synced.found.size,
            removed = synced.deleted.size,
            released = released,
            message = "user=${user.id} onSite=${synced.found.size} removed=${synced.deleted.size} " +
                "released=$released changed=${updates.size}",
        )
    }

    private fun boolArg(name: String, default: Boolean): Boolean =
        args.getOptionValues(name)?.firstOrNull()?.let {
            when (it.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Invalid --$name value: '$it'. Use true or false.")
            }
        } ?: default
}
