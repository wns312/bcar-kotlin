package jyk.bcar.dispatcher

import jyk.bcar.dispatcher.exception.IllegalRunnerException
import jyk.bcar.runner.Runner
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.DefaultApplicationArguments
import java.lang.IllegalArgumentException

class RunnerDispatcherTest {
    @Test
    fun `적절한 runner를 찾지 못하면 IllegalRunnerException 발생`() {
        // given
        val dispatcher = RunnerDispatcher(runners = emptyMap())

        // when & then
        Assertions
            .assertThatThrownBy {
                dispatcher.run(DefaultApplicationArguments())
            }.isInstanceOf(IllegalRunnerException::class.java)
    }

    @Test
    fun `argument가 들어오지 않으면 IllegalRunnerException 발생`() {
        // given
        val dispatcher = RunnerDispatcher(runners = emptyMap())

        // when & then
        Assertions
            .assertThatThrownBy {
                dispatcher.run(null)
            }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `인자로 들어온 noArgs에 맞는 Runner의 run을 실행`() {
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
        val applicationArguments = DefaultApplicationArguments(mockRunnerName)

        // when
        dispatcher.run(applicationArguments)

        // then
        verify(mockRunner, times(1)).run()
    }
}
