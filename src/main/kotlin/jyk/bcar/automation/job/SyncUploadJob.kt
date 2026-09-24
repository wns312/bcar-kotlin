package jyk.bcar.automation.job

import com.microsoft.playwright.Page
import jyk.bcar.automation.job.act.target.SyncUploadedCars
import jyk.bcar.automation.job.act.target.SyncUploadedCarsRequest
import jyk.bcar.automation.job.act.target.TargetAdminLogin
import jyk.bcar.automation.job.act.target.UploadCar
import jyk.bcar.automation.job.act.target.UploadCarRequest
import jyk.bcar.automation.job.act.target.UploadQuotaExhausted
import jyk.bcar.automation.job.result.SyncUploadResult
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.configuration.UploadProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarCategory
import jyk.bcar.domain.CarClassifier
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadStatus
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
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
    private val uploadProperties: UploadProperties,
    private val webClient: WebClient,
    private val args: ApplicationArguments,
) : AutomationJob<SyncUploadResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val uploadable = setOf(UploadStatus.PENDING, UploadStatus.FAILED)

    override val name: String = "sync-upload"

    private data class Outcome(
        val onSite: Int,
        val removed: Int,
        val released: Int,
        val upload: UploadOutcome,
    )

    private data class UploadOutcome(
        val uploaded: Int = 0,
        val failed: Int = 0,
        /** 계정의 무료 등록 한도가 끝났다. 남은 차량은 건드리지 않고 잡을 실패로 끝낸다 */
        val exhausted: Boolean = false,
    )

    override suspend fun execute(): SyncUploadResult = withContext(Dispatchers.IO) {
        val users = userRepository.findAllTargetAdminUsers()
        val userId = args.getOptionValues("user")?.firstOrNull()
        val index = users.indexOfFirst { it.id == userId }
        // 시트에 없는 유저를 동기화하면 그 계정 매물을 통째로 지우게 된다
        check(index >= 0) { "Unknown --user '$userId'. Sheet has ${users.size} users." }

        val user = users[index]
        val delete = boolArg("delete", default = true)
        val nextUserId = users.getOrNull(index + 1)?.id
        val cars = carRepository.findByAssignedUser(user.id)
        val expected = cars.filter { it.uploadStatus != UploadStatus.NEEDS_REMOVAL }
        logger.info(
            "Syncing ${user.id} (${user.targetSite}): 할당 ${cars.size}대, 사이트에 있어야 할 ${expected.size}대, delete=$delete",
        )

        val submit = boolArg("submit", default = true)
        val limit = args.getOptionValues("limit")?.firstOrNull()?.toInt() ?: Int.MAX_VALUE
        val now = Instant.now()

        val outcome = try {
            runner.withSession { session ->
                session.usePage { page ->
                    TargetAdminLogin(page).doAct(user)
                    val synced = SyncUploadedCars(page).doAct(
                        SyncUploadedCarsRequest(
                            manageUrl = user.manageUrl,
                            expected = expected.mapTo(HashSet()) { it.carNumber },
                            delete = delete,
                        ),
                    )
                    val updates = cars.mapNotNull { it.syncedWith(onSite = it.carNumber in synced.found, now = now) }
                    carRepository.saveAll(updates)

                    val byNumber = updates.associateBy { it.carNumber }
                    val pending = cars
                        .map { byNumber[it.carNumber] ?: it }
                        .filter { it.isActive && it.uploadStatus in uploadable && it.uploadAttempts < uploadProperties.maxAttempts }
                        .take(limit)
                    val uploaded = upload(page, user, pending, submit)
                    Outcome(synced.found.size, synced.deleted.size, updates.count { it.assignedUserId == null }, uploaded)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 체인은 decider가 이어준다. 이 잡은 실패로 끝나 Batch 상태에 남는다
            logger.error("Sync failed for ${user.id}", e)
            return@withContext SyncUploadResult(
                userId = user.id,
                nextUserId = nextUserId,
                success = false,
                message = "user=${user.id} failed: ${e.message}",
            )
        }

        SyncUploadResult(
            userId = user.id,
            nextUserId = nextUserId,
            onSite = outcome.onSite,
            removed = outcome.removed,
            released = outcome.released,
            uploaded = outcome.upload.uploaded,
            failed = outcome.upload.failed,
            success = !outcome.upload.exhausted,
            message = "user=${user.id} onSite=${outcome.onSite} removed=${outcome.removed} released=${outcome.released} " +
                "uploaded=${outcome.upload.uploaded} failed=${outcome.upload.failed}" +
                if (outcome.upload.exhausted) " — 무료 등록 한도 소진" else "",
        )
    }

    /** 한 대씩 올리고 결과를 바로 저장한다 — 중간에 죽어도 올린 것만큼은 남는다 */
    private suspend fun upload(
        page: Page,
        user: TargetAdminUser,
        cars: List<Car>,
        submit: Boolean,
    ): UploadOutcome {
        if (cars.isEmpty()) return UploadOutcome()
        val tree = carRepository.findCategoryTree()
        if (tree == null) {
            logger.error("No category tree. collect-category를 먼저 돌려야 한다")
            return UploadOutcome(failed = cars.size)
        }
        val classifier = CarClassifier(tree)

        var uploaded = 0
        var failed = 0
        for (car in cars) {
            val source = classifier.classify(car)
            if (source == null) {
                logger.warn("분류 불가: ${car.carNumber} ${car.company} ${car.title}")
                if (submit) carRepository.saveAll(listOf(car.markFailed("분류 불가")))
                failed++
                continue
            }
            try {
                // 올리는 동안만 UPLOADING — 중간에 멈춰도 남은 차량이 이 상태로 묶이지 않는다
                if (submit) carRepository.saveAll(listOf(car.copy(uploadStatus = UploadStatus.UPLOADING)))
                UploadCar(page, webClient).doAct(
                    UploadCarRequest(
                        source = source,
                        registerUrl = user.registerUrl,
                        price = car.price + uploadProperties.marginFor(car.price, CarCategory.of(car)),
                        comment = uploadProperties.comment,
                        submit = submit,
                    ),
                )
                if (submit) carRepository.saveAll(listOf(car.markUploaded(Instant.now())))
                uploaded++
                logger.info("올림 $uploaded/${cars.size}: ${car.carNumber} ${car.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: UploadQuotaExhausted) {
                // 계정이 막힌 것이므로 남은 차량은 PENDING 그대로 두고 다음 launch를 기다린다
                logger.error("${user.id}: ${e.message}. 남은 ${cars.size - uploaded - failed}대는 건너뛴다")
                if (submit) carRepository.saveAll(listOf(car.copy(uploadStatus = UploadStatus.PENDING)))
                return UploadOutcome(uploaded, failed, exhausted = true)
            } catch (e: Exception) {
                logger.warn("업로드 실패: ${car.carNumber} ${car.title} — ${e.message}")
                if (submit) carRepository.saveAll(listOf(car.markFailed(e.message ?: e::class.simpleName.orEmpty())))
                failed++
            }
        }
        return UploadOutcome(uploaded, failed)
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
