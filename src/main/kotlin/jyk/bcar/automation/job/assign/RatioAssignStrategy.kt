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
 * 3. 비율 맞는 차가 들어와 quota를 넘기는 만큼만 excess를 해제(분류 불가 → 안 올라간 것 → 올라간 것 순, 각 비싼 순).
 *    업로드 중(UPLOADING)인 차는 건드리지 않는다 — 올리던 매물이 목록에서 사라진다.
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
    private val releaseFirst = compareBy<Car>({ CarCategory.of(it) != null }, { it.uploadStatus == UploadStatus.UPLOADED })
        .then(cheapest.reversed())
    private val categories = CarCategory.entries

    override fun plan(users: List<TargetAdminUser>, cars: List<Car>): AssignPlan {
        val active = cars.filter { it.isActive }
        val heldByUser = active
            .filter { it.assignedUserId != null && it.uploadStatus != UploadStatus.NEEDS_REMOVAL }
            .groupBy { it.assignedUserId!! }
        // 분류 불가(모르는 제조사)는 어느 쿼터에도 못 넣으므로 새로 할당하지 않는다
        val unassigned = active.filter { it.assignedUserId == null }.groupBy(CarCategory::of).filterKeys { it != null }

        val held = users.associateWith { heldByUser[it.id].orEmpty().groupBy(CarCategory::of) }

        fun heldSize(user: TargetAdminUser) = held.getValue(user).values.sumOf { it.size }
        val target = users.associateWith { apportion(it.quota, categories.associateWith { c -> properties.ratio[c] ?: 0.0 }) }

        fun heldIn(user: TargetAdminUser, category: CarCategory) = held.getValue(user)[category].orEmpty()

        fun targetOf(user: TargetAdminUser, category: CarCategory) = target.getValue(user).getValue(category)

        fun need(user: TargetAdminUser, category: CarCategory) =
            (targetOf(user, category) - heldIn(user, category).size).coerceAtLeast(0)

        fun excess(user: TargetAdminUser, category: CarCategory) =
            (heldIn(user, category).size - targetOf(user, category)).coerceAtLeast(0)

        // 업로드 중인 차는 비켜주지 않으므로, 비울 수 있는 자리 이상으로 받지 않는다
        fun movable(user: TargetAdminUser, category: CarCategory?) =
            held.getValue(user)[category].orEmpty().filter { it.uploadStatus != UploadStatus.UPLOADING }

        val releasable = users.associateWith { user ->
            // 업로드 중인 차가 초과분 자리를 차지하고 있으면 그만큼은 못 비운다
            (movable(user, null) + categories.flatMap { movable(user, it).sortedWith(releaseFirst).take(excess(user, it)) })
                .sortedWith(releaseFirst)
        }
        val want = users.associateWith { user ->
            val capacity = (user.quota - heldSize(user) + releasable.getValue(user).size).coerceAtLeast(0)
            val raw = categories.associateWith { need(user, it) }
            if (raw.values.sum() <= capacity) raw else apportion(capacity, raw)
        }

        val give = users.associateWith { categories.associateWith { 0 }.toMutableMap() }
        for (category in categories) {
            val needs = users.associateWith { want.getValue(it).getValue(category) }
            val demand = needs.values.sum()
            val available = unassigned[category].orEmpty().size
            val shares = if (available >= demand) needs else apportion(available, needs)
            shares.forEach { (user, n) -> give.getValue(user)[category] = n }
            logger.info("$category: supply=$available demand=$demand")
        }

        val release = users.associateWith { user ->
            val over = heldSize(user) + give.getValue(user).values.sum() - user.quota
            releasable.getValue(user).take(over.coerceAtLeast(0))
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
        // 해제 즉시 다른 유저에게 간 차는 할당본 하나로만 저장돼야 한다 (같은 키 두 버전이면 뒤에 쓴 쪽이 이긴다)
        val reassigned = assign.values.flatten().mapTo(HashSet()) { it.carNumber }
        return AssignPlan(assign, release.values.flatten().filterNot { it.carNumber in reassigned }, shortfall = room.values.sum())
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
