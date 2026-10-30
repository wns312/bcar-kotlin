package jyk.bcar.port

import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.domain.CategoryTree
import jyk.bcar.domain.TargetAdminUser

fun interface DraftCarSource {
    suspend fun collect(): List<Car>
}

interface DetailCarSource {
    suspend fun fetch(detailPageNumber: String): ByteArray

    suspend fun parse(bytes: ByteArray): CarDetail
}

fun interface CategoryTreeSource {
    suspend fun collect(user: TargetAdminUser): CategoryTree
}

/** 정상 페이지지만 취급 대상이 아닌 승계 매물. */
class TakeoverListing : RuntimeException("리스·렌트 승계 매물")
