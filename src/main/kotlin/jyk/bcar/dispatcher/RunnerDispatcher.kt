package jyk.bcar.dispatcher

import jyk.bcar.runner.Runner
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RunnerDispatcher(
    private val runners: Map<String, Runner>,
) : ApplicationRunner {
    companion object {
        private const val RUNNER_ARGUMENT_KEY = "runner"
    }

    override fun run(args: ApplicationArguments?) =
        runBlocking {
            val runners = findRunner(args)
            runners.forEach { it.run() }
        }

    private fun findRunner(args: ApplicationArguments?): List<Runner> {
        requireNotNull(args) {
            throw IllegalArgumentException("Runner args can't be null")
        }

        val runnerNames = args.getOptionValues(RUNNER_ARGUMENT_KEY)
        if (runnerNames.isNullOrEmpty()) {
            println("No runner option found. End execution.")
            return emptyList()
        }

        return runnerNames.map {
            requireNotNull(runners[it]) {
                throw IllegalArgumentException("Runner $it does not exist. ")
            }
        }
    }
}
