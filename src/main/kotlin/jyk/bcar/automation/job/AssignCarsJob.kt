package jyk.bcar.automation.job

import jyk.bcar.automation.job.assign.AssignStrategy
import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.domain.UploadStatus
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.ExcludedAgencyRepository
import jyk.bcar.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AssignCarsJob(
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val excludedAgencyRepository: ExcludedAgencyRepository,
    private val strategy: AssignStrategy,
) : AutomationJob<AssignCarsResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "assign-cars"

    override suspend fun execute(): AssignCarsResult {
        val users = userRepository.findAllTargetAdminUsers()
        if (users.isEmpty()) return AssignCarsResult(assigned = 0, success = false, message = "no target admin users")
        val cars = carRepository.findAll()
        val excluded = excludedAgencyRepository.findExcludedAgencies()
        excluded.unmatched(cars).forEach { logger.warn("제외상사 규칙에 걸리는 차가 없다: '$it'") }

        // 업로드 중인 차는 다음 실행에서 뺀다. 그때까진 유저 자리로 세야 quota를 넘겨 할당하지 않는다
        val blocked = cars.filter { it.isActive && !it.paid && it.uploadStatus != UploadStatus.UPLOADING && it in excluded }
        val blockedNumbers = blocked.mapTo(HashSet()) { it.carNumber }
        val dropped = blocked.filter { it.assignedUserId != null && it.uploadStatus != UploadStatus.NEEDS_REMOVAL }.map { it.release() }

        val plan = strategy.plan(users, cars.filterNot { it.carNumber in blockedNumbers })
        plan.assign.forEach { (user, picked) -> logger.info("Plan ${user.id}: +${picked.size}") }

        val now = Instant.now()
        val assigned = plan.assign.flatMap { (user, picked) -> picked.map { it.assignTo(user, now) } }
        val released = plan.release.map { it.release() } + dropped
        carRepository.saveAll(assigned + released)

        return AssignCarsResult(
            assigned = assigned.size,
            released = released.size,
            shortfall = plan.shortfall,
            uploadUserIds = users.map { it.id },
            message = "assigned=${assigned.size} released=${released.size} excluded=${blocked.size} " +
                "shortfall=${plan.shortfall} users=${users.size}",
        )
    }
}
