package jyk.bcar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BcarApplication

fun main(args: Array<String>) {
    runApplication<BcarApplication>(*args)
}
