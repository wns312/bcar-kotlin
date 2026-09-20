package jyk.bcar.automation.job.assign

import jyk.bcar.configuration.AssignProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarCategory
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 1. 유저별 목표 = quota × 비율. 목표 초과 카테고리는 PENDING을 비싼 순으로 해제, 미달은 need
 * 2. 카테고리별 공급(미할당 + 해제분, 싼 순) vs 수요. 모자라면 need 비례로 나눔
 * 3. 그래도 모자란 유저 몫은 fallbackOrder 카테고리의 남은 공급으로
 * 4. 대수가 정해진 뒤 실제 차량은 싼 순으로 돌아가며 나눠준다
 */
@Component
class RatioAssignStrategy(
    private val properties: AssignProperties,
) : AssignStrategy {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val cheapest = compareBy<Car>({ it.price }, { it.carNumber })

    override fun plan(users: List<TargetAdminUser>, cars: List<Car>): AssignPlan {
        val active = cars.filter { it.isActive }
        val heldByUser = active.filter { it.assignedUserId != null }.groupBy { it.assignedUserId!! }

        val release = mutableListOf<Car>()
        val need = users.associateWith { user ->
            val held = heldByUser[user.id].orEmpty().groupBy(CarCategory::of)
            val target = apportion(user.quota, CarCategory.entries.associateWith { properties.ratio[it] ?: 0.0 })
            CarCategory.entries.associateWith { category ->
                val diff = target.getValue(category) - held[category].orEmpty().size
                if (diff < 0) {
                    release += held
                        .getValue(category)
                        .filter { it.uploadStatus == UploadStatus.PENDING }
                        .sortedWith(cheapest.reversed())
                        .take(-diff)
                }
                diff.coerceAtLeast(0)
            }
        }

        val supply = (active.filter { it.assignedUserId == null } + release)
            .groupBy(CarCategory::of)
            .mapValues { it.value.sortedWith(cheapest) }
        val give = users.associateWith { CarCategory.entries.associateWith { 0 }.toMutableMap() }

        for (category in CarCategory.entries) {
            val needs = users.associateWith { need.getValue(it).getValue(category) }
            val demand = needs.values.sum()
            val available = supply[category].orEmpty().size
            val shares = if (available >= demand) needs else apportion(available, needs)
            shares.forEach { (user, n) -> give.getValue(user)[category] = n }
            logger.info("$category: supply=$available demand=$demand")
        }

        val shortage = users
            .associateWith { user ->
                CarCategory.entries.sumOf { need.getValue(user).getValue(it) - give.getValue(user).getValue(it) }
            }.toMutableMap()
        for (category in properties.fallbackOrder) {
            val totalShortage = shortage.values.sum()
            if (totalShortage == 0) break
            val remaining = supply[category].orEmpty().size - give.values.sumOf { it.getValue(category) }
            val shares = if (remaining >= totalShortage) shortage.toMap() else apportion(remaining, shortage)
            shares.forEach { (user, n) ->
                give.getValue(user)[category] = give.getValue(user).getValue(category) + n
                shortage[user] = shortage.getValue(user) - n
            }
            if (shares.values.sum() > 0) logger.info("fallback $category: +${shares.values.sum()}")
        }

        val assign = users.associateWith { mutableListOf<Car>() }
        for (category in CarCategory.entries) {
            deal(supply[category].orEmpty(), give.mapValues { it.value.getValue(category) }).forEach { (user, dealt) ->
                assign.getValue(user) += dealt
            }
        }
        return AssignPlan(assign, release, shortfall = shortage.values.sum())
    }

    /** total을 weight 비례로 정수 분배(최대 잔여). weight 합 0이면 전부 0 */
    private fun <K> apportion(total: Int, weights: Map<K, Number>): Map<K, Int> {
        val sum = weights.values.sumOf { it.toDouble() }
        if (sum == 0.0) return weights.mapValues { 0 }
        val exact = weights.mapValues { total * it.value.toDouble() / sum }
        val floors = exact.mapValues { it.value.toInt() }
        val extra = exact.keys.sortedByDescending { exact.getValue(it) - floors.getValue(it) }.take(total - floors.values.sum())
        return floors.mapValues { (k, v) -> if (k in extra) v + 1 else v }
    }

    /** 싼 차가 한 유저에게 몰리지 않게 돌아가며 한 대씩 */
    private fun <K> deal(cars: List<Car>, counts: Map<K, Int>): Map<K, List<Car>> {
        val left = counts.toMutableMap()
        val out = counts.keys.associateWith { mutableListOf<Car>() }
        val next = cars.iterator()
        while (left.values.any { it > 0 } && next.hasNext()) {
            for ((k, n) in left) {
                if (n == 0 || !next.hasNext()) continue
                out.getValue(k) += next.next()
                left[k] = n - 1
            }
        }
        return out
    }
}
