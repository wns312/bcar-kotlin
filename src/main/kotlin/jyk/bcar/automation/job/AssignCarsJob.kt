package jyk.bcar.automation.job

import jyk.bcar.automation.job.result.AssignCarsResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AssignCarsJob : AutomationJob<AssignCarsResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val name: String = "assign-cars"

    override suspend fun execute(): AssignCarsResult {
        logger.info("Assigning cars.")
        return AssignCarsResult(
            carIds = listOf("car-1", "car-2", "car-3"),
            message = "cars assigned",
        )
    }
}
