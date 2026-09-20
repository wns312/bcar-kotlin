package jyk.bcar.automation.job.assign

import jyk.bcar.domain.Car
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class CarAssignerTest {
    private fun car(number: String, price: Int = 1000, isActive: Boolean = true, assignedUserId: String? = null) =
        Car(number, "t", "c", "1", "a", "s", "p", price, isActive = isActive, assignedUserId = assignedUserId)

    private fun user(id: String, quota: Int) = TargetAdminUser(id = id, password = "pw", targetSite = "kcr", quota = quota)

    private val now = Instant.parse("2026-09-20T00:00:00Z")
    private val anyCar = AnyCarAssignStrategy()

    @Test
    fun planFillsQuotaFromUnassignedActiveCarsAndReportsShortfall() {
        val cars = listOf(
            car("held1", assignedUserId = "u1"),
            car("heldInactive", isActive = false, assignedUserId = "u1"),
            car("inactive", isActive = false),
            car("other", assignedUserId = "u2"),
            car("a"),
            car("b"),
            car("c"),
        )

        val plans = CarAssigner.plan(cars, listOf(user("u1", 2), user("u2", 4)), anyCar)

        assertEquals(listOf("a"), plans[0].cars.map { it.carNumber })
        assertEquals(0, plans[0].shortfall)
        assertEquals(listOf("b", "c"), plans[1].cars.map { it.carNumber })
        assertEquals(1, plans[1].shortfall)
    }

    @Test
    fun planIsEmptyWhenQuotaAlreadyMet() {
        val plans = CarAssigner.plan(listOf(car("held", assignedUserId = "u1"), car("free")), listOf(user("u1", 1)), anyCar)

        assertEquals(emptyList<Car>(), plans.single().cars)
        assertEquals(0, plans.single().shortfall)
    }

    @Test
    fun planCapsOverPickingStrategy() {
        val greedy = AssignStrategy { _, _, pool, _ -> pool + pool }

        val plans = CarAssigner.plan(listOf(car("a"), car("b"), car("c")), listOf(user("u1", 2)), greedy)

        assertEquals(listOf("a", "b"), plans.single().cars.map { it.carNumber })
    }

    @Test
    fun applyStampsAssignment() {
        val plans = CarAssigner.plan(listOf(car("a")), listOf(user("u1", 1)), anyCar)

        val assigned = CarAssigner.apply(plans, now)

        assertTrue(
            assigned.all {
                it.assignedUserId == "u1" && it.targetSite == "kcr" && it.assignedAt == now && it.uploadStatus == UploadStatus.PENDING
            },
        )
    }

    @Test
    fun fallbackHandsShortfallToNextStrategy() {
        val pricey = AssignStrategy { _, _, pool, need -> pool.filter { it.price > 500 }.take(need) }
        val strategy = FallbackAssignStrategy(listOf(pricey, anyCar))
        val pool = listOf(car("cheap1", 100), car("pricey", 900), car("cheap2", 200))

        assertEquals(listOf("pricey", "cheap1"), strategy.pick(user("u1", 2), emptyList(), pool, 2).map { it.carNumber })
        assertEquals(listOf("pricey"), strategy.pick(user("u1", 1), emptyList(), pool, 1).map { it.carNumber })
    }
}
