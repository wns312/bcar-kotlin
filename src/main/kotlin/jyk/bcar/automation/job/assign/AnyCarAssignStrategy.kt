package jyk.bcar.automation.job.assign

import jyk.bcar.domain.Car
import jyk.bcar.domain.TargetAdminUser
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** 최후 폴백. 조건 없이 pool 앞에서 채운다 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class AnyCarAssignStrategy : AssignStrategy {
    override fun pick(user: TargetAdminUser, held: List<Car>, pool: List<Car>, need: Int): List<Car> = pool.take(need)
}
