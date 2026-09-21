package jyk.bcar.automation.job.assign

import jyk.bcar.configuration.AssignProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarCategory
import jyk.bcar.domain.CarCategory.DOMESTIC_OVER_1300
import jyk.bcar.domain.CarCategory.DOMESTIC_UNDER_1300
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RatioAssignStrategyTest {
    private val strategy = RatioAssignStrategy(
        AssignProperties(
            ratio = mapOf(DOMESTIC_UNDER_1300 to 0.5, DOMESTIC_OVER_1300 to 0.5),
            fallbackOrder = listOf(DOMESTIC_UNDER_1300, DOMESTIC_OVER_1300),
        ),
    )
    private val a = TargetAdminUser("a", "pw", "kcr", quota = 4)
    private val b = TargetAdminUser("b", "pw", "kcr", quota = 4)

    private fun car(
        number: String,
        price: Int,
        isActive: Boolean = true,
        assignedUserId: String? = null,
        status: UploadStatus = UploadStatus.NONE,
    ) = Car(number, "현대 차", "현대", "1", "ag", "s", "p", price, isActive = isActive, assignedUserId = assignedUserId, uploadStatus = status)

    private fun under(n: Int) = (1..n).map { car("u$it", 1000 + it) }

    private fun over(n: Int) = (1..n).map { car("o$it", 2000 + it) }

    private fun AssignPlan.numbers(user: TargetAdminUser) = assign.getValue(user).map { it.carNumber }

    private fun AssignPlan.categories(user: TargetAdminUser) = assign.getValue(user).groupingBy(CarCategory::of).eachCount()

    @Test
    fun fillsQuotaByRatioAndDealsCheapestRoundRobin() {
        val plan = strategy.plan(listOf(a, b), under(10) + over(10))

        assertEquals(mapOf(DOMESTIC_UNDER_1300 to 2, DOMESTIC_OVER_1300 to 2), plan.categories(a))
        assertEquals(mapOf(DOMESTIC_UNDER_1300 to 2, DOMESTIC_OVER_1300 to 2), plan.categories(b))
        assertEquals(listOf("u1", "u3", "o1", "o3"), plan.numbers(a))
        assertEquals(listOf("u2", "u4", "o2", "o4"), plan.numbers(b))
        assertEquals(0, plan.shortfall)
    }

    @Test
    fun releasesExcessNotUploadedFirstThenUploadedMostExpensiveFirst() {
        val held = listOf(
            car("h1", 1100, assignedUserId = "a", status = UploadStatus.PENDING),
            car("h2", 1200, assignedUserId = "a", status = UploadStatus.UPLOADED),
            car("h3", 1300, assignedUserId = "a", status = UploadStatus.UPLOADED),
            car("h4", 1000, assignedUserId = "a", status = UploadStatus.FAILED),
            car("h5", 1050, assignedUserId = "a", status = UploadStatus.PENDING),
            car("removing", 1000, assignedUserId = "a", status = UploadStatus.NEEDS_REMOVAL),
            car("inactive", 1000, isActive = false, assignedUserId = "a", status = UploadStatus.PENDING),
        )

        val plan = strategy.plan(listOf(a), held + over(10))

        assertEquals(listOf("h1", "h5", "h4"), plan.release.map { it.carNumber })
        assertEquals(mapOf(DOMESTIC_OVER_1300 to 2), plan.categories(a))
    }

    @Test
    fun releasedUploadedCarsStayOutOfPool() {
        val held = (1..4).map { car("h$it", 1000 + it, assignedUserId = "a", status = UploadStatus.UPLOADED) }

        val plan = strategy.plan(listOf(a, b), held + over(4))

        assertEquals(2, plan.release.size)
        assertEquals(mapOf(DOMESTIC_OVER_1300 to 2), plan.categories(b))
        assertEquals(2, plan.shortfall)
    }

    @Test
    fun sharesScarceCategoryProportionallyThenFallsBack() {
        val plan = strategy.plan(listOf(a, b), under(2) + over(10))

        assertEquals(mapOf(DOMESTIC_UNDER_1300 to 1, DOMESTIC_OVER_1300 to 3), plan.categories(a))
        assertEquals(mapOf(DOMESTIC_UNDER_1300 to 1, DOMESTIC_OVER_1300 to 3), plan.categories(b))
        assertEquals(0, plan.shortfall)
    }

    @Test
    fun reportsShortfallWhenPoolExhausted() {
        val plan = strategy.plan(listOf(a, b), under(1) + over(2))

        assertEquals(3, plan.assign.values.sumOf { it.size })
        assertEquals(5, plan.shortfall)
    }

    @Test
    fun releasedCarsGoBackToPoolForOthers() {
        val held = (1..4).map { car("h$it", 1000 + it, assignedUserId = "a", status = UploadStatus.PENDING) }

        val plan = strategy.plan(listOf(a, b), held + over(4))

        assertEquals(2, plan.release.size)
        assertEquals(mapOf(DOMESTIC_UNDER_1300 to 2, DOMESTIC_OVER_1300 to 2), plan.categories(b))
    }

    @Test
    fun keepsFallbackCarsWhenPreferredCategoryStillShort() {
        val first = strategy.plan(listOf(a), under(10) + over(1))
        assertEquals(mapOf(DOMESTIC_UNDER_1300 to 3, DOMESTIC_OVER_1300 to 1), first.categories(a))

        val held = first.assign.getValue(a).map { it.assignTo(a, java.time.Instant.EPOCH) }
        val rest = (under(10) + over(1)).filter { c -> held.none { it.carNumber == c.carNumber } }
        val second = strategy.plan(listOf(a), held + rest)

        assertEquals(emptyList<Car>(), second.assign.getValue(a))
        assertEquals(emptyList<Car>(), second.release)
        assertEquals(0, second.shortfall)
    }

    @Test
    fun swapsFallbackCarsOnceInRatioSupplyAppears() {
        val held =
            listOf(
                "h1" to 1001,
                "h2" to 1002,
                "h3" to 1003,
            ).map { (n, p) -> car(n, p, assignedUserId = "a", status = UploadStatus.PENDING) } +
                car("o0", 2000, assignedUserId = "a", status = UploadStatus.PENDING)

        val plan = strategy.plan(listOf(a), held + over(1))

        assertEquals(listOf("h3"), plan.release.map { it.carNumber })
        assertEquals(listOf("o1"), plan.numbers(a))
    }
}
