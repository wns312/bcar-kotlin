package jyk.bcar.automation.job.assign

import jyk.bcar.configuration.AssignProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarCategory
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 1. 유저별 목표 = quota × 비율. 미달 카테고리는 need, 초과 카테고리는 excess
 * 2. 카테고리별 미할당 공급(싼 순) vs need 수요. 모자라면 need 비례로 나눔
 * 3. 비율 맞는 차가 들어와 quota를 넘기는 만큼만 excess를 해제(안 올라간 것부터, 비싼 순).
 *    폴백으로 채운 차를 매 실행 갈아끼우지 않으려면 해제는 교체 가능한 만큼만이어야 한다
 * 4. 남은 자리는 fallbackOrder 카테고리의 남은 공급(해제분 포함)으로
 * 5. 대수가 정해진 뒤 실제 차량은 싼 순으로 돌아가며 나눠준다
 */
@Component
class RatioAssignStrategy(
    private val properties: AssignProperties,
) : AssignStrategy {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val cheapest = compareBy<Car>({ it.price }, { it.carNumber })
    private val releaseFirst = compareBy<Car> { it.uploadStatus == UploadStatus.UPLOADED }.then(cheapest.reversed())
    private val categories = CarCategory.entries

    override fun plan(users: List<TargetAdminUser>, cars: List<Car>): AssignPlan {
        val active = cars.filter { it.isActive }
        val heldByUser = active
            .filter { it.assignedUserId != null && it.uploadStatus != UploadStatus.NEEDS_REMOVAL }
            .groupBy { it.assignedUserId!! }
        val unassigned = active.filter { it.assignedUserId == null }.groupBy(CarCategory::of)

        val held = users.associateWith { heldByUser[it.id].orEmpty().groupBy(CarCategory::of) }
        val target = users.associateWith { apportion(it.quota, categories.associateWith { c -> properties.ratio[c] ?: 0.0 }) }

        fun heldIn(user: TargetAdminUser, category: CarCategory) = held.getValue(user)[category].orEmpty()

        fun need(user: TargetAdminUser, category: CarCategory) = (
            target
                .getValue(
                    user,
                ).getValue(category) - heldIn(user, category).size
        ).coerceAtLeast(0)

        fun excess(user: TargetAdminUser, category: CarCategory) = (
            heldIn(
                user,
                category,
            ).size - target.getValue(user).getValue(category)
        ).coerceAtLeast(0)

        val give = users.associateWith { categories.associateWith { 0 }.toMutableMap() }
        for (category in categories) {
            val needs = users.associateWith { need(it, category) }
            val demand = needs.values.sum()
            val available = unassigned[category].orEmpty().size
            val shares = if (available >= demand) needs else apportion(available, needs)
            shares.forEach { (user, n) -> give.getValue(user)[category] = n }
            logger.info("$category: supply=$available demand=$demand")
        }

        val release = users.associateWith { user ->
            val over = held.getValue(user).values.sumOf { it.size } + give.getValue(user).values.sum() - user.quota
            if (over <= 0) {
                emptyList()
            } else {
                categories
                    .flatMap { c -> heldIn(user, c).sortedWith(releaseFirst).take(excess(user, c)) }
                    .sortedWith(releaseFirst)
                    .take(over)
            }
        }

        val pool = categories.associateWith { category ->
            val backToPool = release.values.flatten().filter { it.uploadStatus != UploadStatus.UPLOADED && CarCategory.of(it) == category }
            (unassigned[category].orEmpty() + backToPool).sortedWith(cheapest)
        }
        val room = users
            .associateWith { user ->
                user.quota - held.getValue(user).values.sumOf { it.size } + release.getValue(user).size - give.getValue(user).values.sum()
            }.toMutableMap()
        for (category in properties.fallbackOrder) {
            val totalRoom = room.values.sum()
            if (totalRoom == 0) break
            val remaining = pool.getValue(category).size - give.values.sumOf { it.getValue(category) }
            val shares = if (remaining >= totalRoom) room.toMap() else apportion(remaining, room)
            shares.forEach { (user, n) ->
                give.getValue(user)[category] = give.getValue(user).getValue(category) + n
                room[user] = room.getValue(user) - n
            }
            if (shares.values.sum() > 0) logger.info("fallback $category: +${shares.values.sum()}")
        }

        val assign = users.associateWith { mutableListOf<Car>() }
        for (category in categories) {
            deal(pool.getValue(category), give.mapValues { it.value.getValue(category) }).forEach { (user, dealt) ->
                assign.getValue(user) += dealt
            }
        }
        return AssignPlan(assign, release.values.flatten(), shortfall = room.values.sum())
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
