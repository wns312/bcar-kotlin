package jyk.bcar.dispatcher

import jyk.bcar.dispatcher.exception.IllegalRunnerException
import jyk.bcar.runner.Runner
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class RunnerDispatcher(
    private val runners: Map<String, Runner>,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val runnerName = getRunnerName(*args)
        val runner = findRunner(runnerName)

        runner.run()
    }

    private fun getRunnerName(vararg args: String?): String {
        if (args.isEmpty()) throw IllegalRunnerException()

        return args[0]!!
    }

    private fun findRunner(runnerName: String): Runner = runners[runnerName] ?: throw IllegalArgumentException("Unknown runner $runnerName")
}
