package jyk.bcar.domain

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
) {
    companion object {
        /**
         * 수집 스냅샷과 저장 상태를 비교해 실제로 바뀐 차량만 돌려준다.
         * 스냅샷에 없는 활성 차량은 비활성화, 있는 차량은 draft 필드를 갱신하고 기존 detail은 유지.
         * 비활성이었다가 다시 나타난 차량은 detail을 비워 재수집 대상으로 만든다.
         */
        fun reconcile(existing: List<Car>, collected: List<Car>): List<Car> {
            val existingByNumber = existing.associateBy { it.carNumber }
            val collectedByNumber = collected.associateBy { it.carNumber }

            val upserts = collectedByNumber.values.mapNotNull { fresh ->
                val old = existingByNumber[fresh.carNumber]
                val keptDetail = old?.detail?.takeIf { old.isActive }
                fresh.copy(isActive = true, detail = keptDetail).takeIf { it != old }
            }
            val deactivated = existingByNumber.values
                .filter { it.isActive && it.carNumber !in collectedByNumber }
                .map { it.copy(isActive = false) }

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
