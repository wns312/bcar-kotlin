package jyk.bcar.automation.job

import org.springframework.boot.ApplicationArguments

fun ApplicationArguments.option(name: String): String? = getOptionValues(name)?.firstOrNull()

fun ApplicationArguments.intOption(name: String): Int? = option(name)?.toInt()

fun ApplicationArguments.boolOption(name: String, default: Boolean): Boolean =
    when (val raw = option(name)?.lowercase()) {
        null -> default
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("Invalid --$name value: '$raw'. Use true or false.")
    }
