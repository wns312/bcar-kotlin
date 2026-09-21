package jyk.bcar.automation.job.assign

import jyk.bcar.domain.Car
import jyk.bcar.domain.TargetAdminUser

data class AssignPlan(
    val assign: Map<TargetAdminUser, List<Car>>,
    /** 할당에서 빠지는 차량. assign 쪽으로 옮겨 간 차량은 포함하지 않는다 */
    val release: List<Car>,
    /** 유저 quota 합 대비 못 채운 대수. 폴백까지 다 쓴 뒤의 값 */
    val shortfall: Int,
)

/** 저장 없이 누구에게 무엇을 주고 무엇을 뺄지 계산한다. 실패하지 않는다 — 못 채우면 shortfall로만 보고 */
fun interface AssignStrategy {
    fun plan(users: List<TargetAdminUser>, cars: List<Car>): AssignPlan
}
