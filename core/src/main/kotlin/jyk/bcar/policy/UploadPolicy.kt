package jyk.bcar.policy

import jyk.bcar.domain.CarCategory

data class UploadPolicy(
    val comment: String,
    val margins: List<Margin>,
    val maxAttempts: Int = 3,
) {
    data class Margin(
        val maxPrice: Int,
        val domestic: Int,
        val imported: Int,
    )

    fun marginFor(price: Int, category: CarCategory?): Int =
        margins
            .firstOrNull { price <= it.maxPrice }
            ?.let { if (category == CarCategory.IMPORTED) it.imported else it.domestic }
            ?: 0
}
