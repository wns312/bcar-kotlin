package jyk.bcar.runner

import org.springframework.stereotype.Component

@Component
class FakeRunner : Runner {
    override suspend fun run() {
        println("FakeRunner executed.")
    }
}
