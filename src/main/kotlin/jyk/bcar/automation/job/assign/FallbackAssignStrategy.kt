package jyk.bcar.automation.job.assign

import jyk.bcar.domain.Car
import jyk.bcar.domain.TargetAdminUser

/** 앞 전략이 못 채운 몫을 다음 전략에 넘긴다 */
class FallbackAssignStrategy(
    private val strategies: List<AssignStrategy>,
) : AssignStrategy {
    override fun pick(user: TargetAdminUser, held: List<Car>, pool: List<Car>, need: Int): List<Car> {
        val picked = mutableListOf<Car>()
        for (strategy in strategies) {
            val remaining = need - picked.size
            if (remaining <= 0) break
            picked += strategy.pick(user, held + picked, pool - picked.toSet(), remaining).distinct().take(remaining)
        }
        return picked
    }
}
