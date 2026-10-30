package jyk.bcar.automation.job

import jyk.bcar.automation.job.result.JobResult

interface AutomationJob<out R : JobResult> {
    val name: String

    suspend fun execute(): R
}
