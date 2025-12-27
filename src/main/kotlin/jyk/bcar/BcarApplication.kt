package jyk.bcar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class BcarApplication

fun main(args: Array<String>) {
    runApplication<BcarApplication>(*args)
}
