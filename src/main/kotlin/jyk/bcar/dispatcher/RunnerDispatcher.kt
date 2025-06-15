package jyk.bcar.dispatcher

import jyk.bcar.dispatcher.exception.IllegalRunnerException
import jyk.bcar.runner.Runner
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RunnerDispatcher(
    private val runners: Map<String, Runner>,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) =
        runBlocking {
            val runner = findRunner(args)
            runner.run()
        }

    private fun findRunner(args: ApplicationArguments?): Runner {
        if (args == null) throw IllegalArgumentException("Runner args can't be null")

        return args.nonOptionArgs
            .map { runners[it] }
            .firstOrNull() ?: throw IllegalRunnerException("Unknown runner(nonOptionArgs): ${args.nonOptionArgs}")
    }
}
