package jyk.bcar.port

import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadSource

interface TargetCarSite {
    /** 동기화와 차량별 업로드가 같은 로그인 세션을 공유한다. */
    suspend fun <T> withSession(user: TargetAdminUser, block: suspend (TargetCarSession) -> T): T
}

interface TargetCarSession {
    suspend fun synchronize(expected: Set<String>, delete: Boolean): SiteListings

    suspend fun upload(source: UploadSource, price: Int, comment: String, submit: Boolean)
}

data class SiteListings(
    val found: Set<String>,
    val deleted: List<String>,
    val paid: Set<String> = emptySet(),
)

class UploadQuotaExhausted(
    message: String,
) : RuntimeException(message)
