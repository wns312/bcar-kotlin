package jyk.bcar.policy

data class DetailCollectionPolicy(
    val maxHops: Int = 300,
    val maxIdleHops: Int = 5,
)
