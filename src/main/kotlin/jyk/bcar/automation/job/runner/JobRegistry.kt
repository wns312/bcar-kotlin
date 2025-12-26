package jyk.bcar.automation.job.runner

import jyk.bcar.automation.job.AutomationJob
import jyk.bcar.automation.job.result.JobResult
import org.springframework.stereotype.Component

@Component
class JobRegistry(
    jobs: List<AutomationJob<JobResult>>,
) {
    private val jobsByName: Map<String, AutomationJob<JobResult>> = jobs.associateBy { it.name }

    fun get(name: String): AutomationJob<JobResult>? = jobsByName[name]

    fun names(): Set<String> = jobsByName.keys
}
