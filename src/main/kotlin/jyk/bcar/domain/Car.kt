package jyk.bcar.domain

import java.time.Instant

enum class UploadStatus {
    NONE,
    PENDING,
    UPLOADED,
    FAILED,

    /** 대상 사이트에 올라가 있는데 소스에서 사라짐. 업로드 잡이 내려야 한다 */
    NEEDS_REMOVAL,
}

data class Car(
    val carNumber: String,
    val title: String,
    val company: String,
    val detailPageNum: String,
    val agency: String,
    val seller: String,
    val sellerPhone: String,
    val price: Int,
    val isActive: Boolean = true,
    val detail: CarDetail? = null,
    val assignedUserId: String? = null,
    val assignedAt: Instant? = null,
    val targetSite: String? = null,
    val uploadStatus: UploadStatus = UploadStatus.NONE,
    val uploadedAt: Instant? = null,
    val uploadError: String? = null,
    /** 대상 사이트 매물 ID */
    val externalId: String? = null,
) {
    fun assignTo(user: TargetAdminUser, now: Instant): Car =
        copy(assignedUserId = user.id, assignedAt = now, targetSite = user.targetSite, uploadStatus = UploadStatus.PENDING)

    private fun withDraftOf(fresh: Car): Car =
        copy(
            title = fresh.title,
            company = fresh.company,
            detailPageNum = fresh.detailPageNum,
            agency = fresh.agency,
            seller = fresh.seller,
            sellerPhone = fresh.sellerPhone,
            price = fresh.price,
        )

    companion object {
        /**
         * 수집 스냅샷과 저장 상태를 비교해 실제로 바뀐 차량만 돌려준다.
         * 스냅샷에 없는 활성 차량은 비활성화, 있는 차량은 draft 필드를 갱신하고 기존 detail·할당 상태는 유지.
         * 비활성이었다가 다시 나타난 차량은 detail을 비워 재수집 대상으로 만든다.
         */
        fun reconcile(existing: List<Car>, collected: List<Car>): List<Car> {
            val existingByNumber = existing.associateBy { it.carNumber }
            val collectedByNumber = collected.associateBy { it.carNumber }

            val upserts = collectedByNumber.values.mapNotNull { fresh ->
                val old = existingByNumber[fresh.carNumber]
                val keptDetail = old?.detail?.takeIf { old.isActive }
                (old?.withDraftOf(fresh) ?: fresh).copy(isActive = true, detail = keptDetail).takeIf { it != old }
            }
            val deactivated = existingByNumber.values
                .filter { it.isActive && it.carNumber !in collectedByNumber }
                .map {
                    val status = if (it.uploadStatus == UploadStatus.UPLOADED) UploadStatus.NEEDS_REMOVAL else it.uploadStatus
                    it.copy(isActive = false, uploadStatus = status)
                }

            return upserts + deactivated
        }

        /**
         * 유저별로 활성·미할당 차량을 quota까지 채운다. 이미 할당된 건 유지하고 새로 할당된 차량만 돌려준다.
         * 어떤 차량을 고를지는 [pick]이 정한다 — 기본은 pool 앞에서 부족분만큼.
         */
        fun assign(
            cars: List<Car>,
            users: List<TargetAdminUser>,
            now: Instant,
            pick: (user: TargetAdminUser, held: List<Car>, pool: List<Car>) -> List<Car> = ::pickByQuota,
        ): List<Car> {
            val active = cars.filter { it.isActive }
            val heldByUser = active.filter { it.assignedUserId != null }.groupBy { it.assignedUserId!! }
            val pool = active.filter { it.assignedUserId == null }.toMutableList()

            return users.flatMap { user ->
                val picked = pick(user, heldByUser[user.id].orEmpty(), pool).toSet()
                pool.removeAll(picked)
                picked.map { it.assignTo(user, now) }
            }
        }

        fun pickByQuota(user: TargetAdminUser, held: List<Car>, pool: List<Car>): List<Car> =
            pool.take((user.quota - held.size).coerceAtLeast(0))
    }
}

data class CarDetail(
    val category: String,
    val displacement: Int,
    val modelYear: String,
    val mileage: Int,
    val color: String,
    val gearBox: String,
    val fuelType: String,
    val presentationNumber: String,
    val hasAccident: String,
    val registerNumber: String,
    val presentationsDate: String,
    val hasSeizure: Boolean,
    val hasMortgage: Boolean,
    val carCheckSrc: String,
    val images: List<String>,
)
