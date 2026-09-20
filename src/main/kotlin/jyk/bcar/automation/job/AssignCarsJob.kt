package jyk.bcar.automation.job

import jyk.bcar.automation.job.result.AssignCarsResult
import jyk.bcar.domain.Car
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AssignCarsJob(
    private val userRepository: UserRepository,
    private val carRepository: CarRepository,
) : AutomationJob<AssignCarsResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "assign-cars"

    override suspend fun execute(): AssignCarsResult {
        val users = userRepository.findAllTargetAdminUsers()
        val cars = carRepository.findAll()
        val assigned = Car.assign(cars, users, Instant.now())
        carRepository.saveAll(assigned)

        val perUser = assigned.groupingBy { it.assignedUserId }.eachCount()
        logger.info("Assigned ${assigned.size} cars to ${users.size} users: $perUser")
        return AssignCarsResult(assigned = assigned.size, message = "assigned=${assigned.size} users=${users.size}")
    }
}
