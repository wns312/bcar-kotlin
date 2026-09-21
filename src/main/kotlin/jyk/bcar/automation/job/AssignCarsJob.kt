package jyk.bcar.automation.job

import jyk.bcar.automation.job.assign.AssignStrategy
import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AssignCarsJob(
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
    private val strategy: AssignStrategy,
) : AutomationJob<AssignCarsResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "assign-cars"

    override suspend fun execute(): AssignCarsResult {
        val users = userRepository.findAllTargetAdminUsers()
        if (users.isEmpty()) return AssignCarsResult(assigned = 0, success = false, message = "no target admin users")
        val cars = carRepository.findAll()

        val plan = strategy.plan(users, cars)
        plan.assign.forEach { (user, picked) -> logger.info("Plan ${user.id}: +${picked.size}") }

        val now = Instant.now()
        val assigned = plan.assign.flatMap { (user, picked) -> picked.map { it.assignTo(user, now) } }
        val released = plan.release.map { it.release() }
        carRepository.saveAll(assigned + released)

        return AssignCarsResult(
            assigned = assigned.size,
            released = released.size,
            shortfall = plan.shortfall,
            message = "assigned=${assigned.size} released=${released.size} shortfall=${plan.shortfall} users=${users.size}",
        )
    }
}
