package jyk.bcar.automation.job.decider

import jyk.bcar.automation.job.result.JobResult

interface JobChainDecider {
    fun decide(result: JobResult): List<NextJobRequest>
}
