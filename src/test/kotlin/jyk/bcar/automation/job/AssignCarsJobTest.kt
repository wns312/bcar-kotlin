package jyk.bcar.automation.job

import jyk.bcar.automation.job.assign.AssignPlan
import jyk.bcar.domain.Car
import jyk.bcar.domain.ExcludedAgencies
import jyk.bcar.domain.SourceAdminUser
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadStatus
import jyk.bcar.domain.UploadStatus.NEEDS_REMOVAL
import jyk.bcar.domain.UploadStatus.NONE
import jyk.bcar.domain.UploadStatus.PENDING
import jyk.bcar.domain.UploadStatus.UPLOADED
import jyk.bcar.domain.UploadStatus.UPLOADING
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.ExcludedAgencyRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssignCarsJobTest {
    private val user = TargetAdminUser("a", "pw", "kcr", quota = 10, baseUrl = "kcr.test")

    private fun car(number: String, agency: String, status: UploadStatus = NONE, paid: Boolean = false) =
        Car(
            number,
            "현대 차",
            "현대",
            "1",
            agency,
            "s",
            "p",
            1000,
            assignedUserId = "a".takeIf { status != NONE },
            uploadStatus = status,
            paid = paid,
        )

    @Test
    fun releasesExcludedAgencyCarsAndHidesThemFromStrategy() = runTest {
        val cars = listOf(
            car("keep", "코리아모터스", PENDING),
            car("free", "(주)뉴카카"),
            car("pending", "(주)뉴카카", PENDING),
            car("uploaded", "블루오토", UPLOADED),
            car("removing", "블루오토", NEEDS_REMOVAL),
            car("uploading", "블루오토", UPLOADING),
            car("paid", "블루오토", UPLOADED, paid = true),
        )
        var planned: List<String> = emptyList()
        val saved = mutableListOf<Car>()
        val job = AssignCarsJob(
            object : UserRepository {
                override suspend fun findSourceAdminUser() = SourceAdminUser("s", "pw")

                override suspend fun findAllTargetAdminUsers() = listOf(user)
            },
            object : CarRepository {
                override suspend fun findAll(segment: Int, totalSegments: Int) = cars

                override suspend fun findByAssignedUser(userId: String) = cars.filter { it.assignedUserId == userId }

                override suspend fun saveAll(cars: List<Car>) {
                    saved += cars
                }
            },
            object : ExcludedAgencyRepository {
                override suspend fun findExcludedAgencies() = ExcludedAgencies(listOf("(주)뉴카", "/^블루/", "없는상사"))
            },
        ) { _, cars ->
            planned = cars.map { it.carNumber }
            AssignPlan(emptyMap(), emptyList(), 0)
        }

        job.execute()

        assertEquals(listOf("keep", "uploading", "paid"), planned)
        assertEquals(mapOf("pending" to NONE, "uploaded" to NEEDS_REMOVAL), saved.associate { it.carNumber to it.uploadStatus })
        assertEquals(null, saved.first { it.carNumber == "pending" }.assignedUserId)
    }

    @Test
    fun plainRuleIsLiteralContainsAndSlashedRuleIsRegex() {
        val excluded = ExcludedAgencies(listOf(" 뉴카 ", "/모터스$/", "(주)", ""))

        assertEquals(true, car("1", "(주)뉴카카") in excluded)
        assertEquals(true, car("2", "코리아모터스") in excluded)
        assertEquals(false, car("3", "모터스코리아") in excluded)
        assertEquals(true, car("4", "(주)왕카") in excluded)
        assertEquals(false, car("5", "주왕카") in excluded)
        assertEquals(setOf("/모터스$/"), excluded.unmatched(listOf(car("6", "(주)뉴카카"))))
    }
}
