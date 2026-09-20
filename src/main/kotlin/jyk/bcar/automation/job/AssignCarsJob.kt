package jyk.bcar.automation.job

import jyk.bcar.automation.job.assign.AssignStrategy
import jyk.bcar.automation.job.assign.CarAssigner
import jyk.bcar.automation.job.assign.FallbackAssignStrategy
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
    strategies: List<AssignStrategy>,
) : AutomationJob<AssignCarsResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val strategy = FallbackAssignStrategy(strategies)

    override val name: String = "assign-cars"

    override suspend fun execute(): AssignCarsResult {
        val users = userRepository.findAllTargetAdminUsers()
        val cars = carRepository.findAll()

        val plans = CarAssigner.plan(cars, users, strategy)
        plans.forEach { logger.info("Plan ${it.user.id}: +${it.cars.size} shortfall=${it.shortfall}") }

        val assigned = CarAssigner.apply(plans, Instant.now())
        carRepository.saveAll(assigned)

        val shortfall = plans.sumOf { it.shortfall }
        return AssignCarsResult(
            assigned = assigned.size,
            shortfall = shortfall,
            message = "assigned=${assigned.size} shortfall=$shortfall users=${users.size}",
        )
    }
}
