package jyk.bcar.automation.job.assign

import jyk.bcar.domain.Car
import jyk.bcar.domain.TargetAdminUser

/**
 * 유저 한 명에게 줄 차량을 고르는 규칙. 조건에 맞는 게 모자라면 모자란 채로 돌려준다 — 남은 몫은 다음 전략이 채운다.
 * 빈 순서(@Order)가 곧 폴백 순서.
 */
fun interface AssignStrategy {
    fun pick(user: TargetAdminUser, held: List<Car>, pool: List<Car>, need: Int): List<Car>
}
