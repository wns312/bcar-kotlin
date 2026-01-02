package jyk.bcar.automation.job.act

interface JobAct<T, R> {
    suspend fun doAct(input: T): R
}
