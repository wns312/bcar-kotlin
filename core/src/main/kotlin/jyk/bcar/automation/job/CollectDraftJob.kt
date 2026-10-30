package jyk.bcar.automation.job

import jyk.bcar.automation.job.result.CollectDraftResult
import jyk.bcar.domain.Car
import jyk.bcar.port.DraftCarSource
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.PipelineControlRepository
import org.slf4j.LoggerFactory

class CollectDraftJob(
    private val source: DraftCarSource,
    private val carRepository: CarRepository,
    private val pipelineControl: PipelineControlRepository,
) : AutomationJob<CollectDraftResult> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name: String = "collect-draft"

    override suspend fun execute(): CollectDraftResult {
        logger.info("Collecting draft ids.")
        val collected = source.collect()
        val changes = Car.reconcile(existing = carRepository.findAll(), collected = collected)
        carRepository.saveAll(changes)
        pipelineControl.resetDetailChains()
        logger.info("Drafts reconciled: collected=${collected.size}, changed=${changes.size}")
        return CollectDraftResult(message = "drafts collected")
    }
}
