package jyk.bcar.runner

import org.springframework.stereotype.Component

@Component
class HelloWorldRunner : Runner {
    override suspend fun run() {
        println("Hello World!")
    }
}
