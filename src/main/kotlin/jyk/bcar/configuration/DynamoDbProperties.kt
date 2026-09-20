package jyk.bcar.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dynamodb")
data class DynamoDbProperties(
    val carsTable: String,
)
