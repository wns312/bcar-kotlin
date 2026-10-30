package jyk.bcar.configuration

import jyk.bcar.domain.CarCategory
import jyk.bcar.policy.UploadPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource

@ConfigurationProperties(prefix = "upload")
data class UploadProperties(
    val commentFile: Resource,
    val margins: List<Margin>,
    /** 이만큼 실패한 차량은 더 시도하지 않는다. 폼이 받아주지 않는 차가 매 실행 자리를 먹는 걸 막는다 */
    val maxAttempts: Int = 3,
) {
    val comment: String by lazy { commentFile.getContentAsString(Charsets.UTF_8).trimEnd() }

    data class Margin(
        val maxPrice: Int,
        val domestic: Int,
        val imported: Int,
    )

    fun toPolicy(): UploadPolicy = UploadPolicy(
        comment = comment,
        margins = margins.map { UploadPolicy.Margin(it.maxPrice, it.domestic, it.imported) },
        maxAttempts = maxAttempts,
    )

    fun marginFor(price: Int, category: CarCategory?): Int = toPolicy().marginFor(price, category)
}
