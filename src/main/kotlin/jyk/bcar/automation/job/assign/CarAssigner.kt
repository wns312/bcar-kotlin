package jyk.bcar.automation.job.assign

import jyk.bcar.domain.Car
import jyk.bcar.domain.TargetAdminUser
import java.time.Instant

data class UserAssignPlan(
    val user: TargetAdminUser,
    val cars: List<Car>,
    /** quota까지 채우지 못한 대수 */
    val shortfall: Int,
)

object CarAssigner {
    /** 저장 없이 유저별로 무엇을 줄지와 부족분을 계산한다. 이미 할당된 활성 차량은 보유로 치고 유지 */
    fun plan(cars: List<Car>, users: List<TargetAdminUser>, strategy: AssignStrategy): List<UserAssignPlan> {
        val active = cars.filter { it.isActive }
        val heldByUser = active.filter { it.assignedUserId != null }.groupBy { it.assignedUserId!! }
        val pool = active.filter { it.assignedUserId == null }.toMutableList()

        return users.map { user ->
            val held = heldByUser[user.id].orEmpty()
            val need = (user.quota - held.size).coerceAtLeast(0)
            val picked = strategy.pick(user, held, pool, need).distinct().take(need)
            pool.removeAll(picked.toSet())
            UserAssignPlan(user, picked, shortfall = need - picked.size)
        }
    }

    fun apply(plans: List<UserAssignPlan>, now: Instant): List<Car> =
        plans.flatMap { plan -> plan.cars.map { it.assignTo(plan.user, now) } }
}
