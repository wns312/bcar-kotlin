package jyk.bcar.domain

import java.time.Instant

enum class UploadStatus {
    NONE,
    PENDING,

    /** 업로드 잡이 올리는 중. assign이 건드리면 올리던 매물이 목록에서 사라진다 */
    UPLOADING,
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
    /** 상세 파싱 실패 사유. 다음 수집이 성공하면 비워진다 */
    val detailError: String? = null,
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

    /**
     * 대상 사이트를 훑은 결과를 반영한다. 사이트가 원천이라 관리자가 손으로 올리거나 내린 것도 여기서 들어온다.
     * 바뀐 게 없으면 null.
     */
    fun syncedWith(onSite: Boolean, now: Instant): Car? =
        when {
            onSite && uploadStatus == UploadStatus.UPLOADED -> null
            onSite -> copy(uploadStatus = UploadStatus.UPLOADED, uploadedAt = now, uploadError = null)
            uploadStatus == UploadStatus.NEEDS_REMOVAL -> release()
            // 실패 이력은 그대로 둔다 — PENDING으로 되돌리면 재시도 가드가 무의미해진다
            uploadStatus == UploadStatus.FAILED || uploadStatus == UploadStatus.PENDING -> null
            else -> copy(uploadStatus = UploadStatus.PENDING, uploadedAt = null)
        }

    /** 소스에서 사라진 차. 올라가 있으면 내려야 하고, 아직 안 올라갔으면 할당을 비워 쿼터를 돌려준다 */
    fun deactivate(): Car =
        when (uploadStatus) {
            UploadStatus.UPLOADED -> copy(isActive = false, uploadStatus = UploadStatus.NEEDS_REMOVAL)
            // 업로드 중이거나 이미 내리기로 한 차는 업로드 잡이 정리한다
            UploadStatus.UPLOADING, UploadStatus.NEEDS_REMOVAL -> copy(isActive = false)
            else -> release().copy(isActive = false)
        }

    /** 올라가 있는 차는 내릴 때까지 할당 정보를 유지한다 */
    fun release(): Car =
        if (uploadStatus == UploadStatus.UPLOADED) {
            copy(uploadStatus = UploadStatus.NEEDS_REMOVAL)
        } else {
            copy(assignedUserId = null, assignedAt = null, targetSite = null, uploadStatus = UploadStatus.NONE)
        }

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
         * 스냅샷에 없는 활성 차량은 비활성화(할당은 deactivate 규칙에 따라 정리), 있는 차량은 draft 필드를 갱신하고 기존 detail·할당 상태는 유지.
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
                .map { it.deactivate() }

            return upserts + deactivated
        }
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
