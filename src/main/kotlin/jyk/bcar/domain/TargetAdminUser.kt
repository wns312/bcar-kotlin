package jyk.bcar.domain

data class TargetAdminUser(
    val id: String,
    val password: String,
    val targetSite: String,
    val quota: Int,
)
