package jyk.bcar.runner

import org.springframework.stereotype.Component

@Component
class HelloWorldRunner : Runner {
    override fun run() {
        print("Hello World!")
    }
}
