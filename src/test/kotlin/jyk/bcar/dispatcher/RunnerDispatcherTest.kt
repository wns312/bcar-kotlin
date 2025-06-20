package jyk.bcar.dispatcher

import jyk.bcar.runner.Runner
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.DefaultApplicationArguments
import java.lang.IllegalArgumentException

class RunnerDispatcherTest {
    companion object {
        private const val RUNNER_ARGUMENT_COMMAND_KEY = "--runner"
    }

    @Test
    @DisplayName("runner 인자가 전달되지 않으면 실행을 종료한다")
    fun test1() {
        // given
        val dispatcher = RunnerDispatcher(runners = emptyMap())

        // when & then
        dispatcher.run(DefaultApplicationArguments())
    }

    @Test
    @DisplayName("runner 인자의 키만 전달되지 않으면 실행을 종료한다")
    fun test2() {
        // given
        val dispatcher = RunnerDispatcher(runners = emptyMap())

        // when & then
        val args =
            DefaultApplicationArguments(
                RUNNER_ARGUMENT_COMMAND_KEY,
            )

        dispatcher.run(args)
    }

    @Test
    @DisplayName("argument가 들어오지 않으면 IllegalRunnerException 발생")
    fun test3() {
        // given
        val dispatcher = RunnerDispatcher(runners = emptyMap())

        // when & then
        Assertions
            .assertThatThrownBy {
                dispatcher.run(null)
            }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("인자로 들어온 optionArgs에 맞는 Runner의 run을 실행")
    fun test4() =
        runTest {
            // given
            val mockRunnerName = "mockRunner"
            val mockRunner = mock<Runner>()
            val dispatcher =
                RunnerDispatcher(
                    runners =
                        mapOf<String, Runner>(
                            mockRunnerName to mockRunner,
                        ),
                )
            val applicationArguments =
                DefaultApplicationArguments(
                    "$RUNNER_ARGUMENT_COMMAND_KEY=$mockRunnerName",
                )

            // when
            dispatcher.run(applicationArguments)

            // then
            verify(mockRunner, times(1)).run()
        }
}
