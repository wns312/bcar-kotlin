package jyk.bcar.automation.job

import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.domain.CategoryTree
import jyk.bcar.domain.SourceAdminUser
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.CategoryTreeRepository
import jyk.bcar.repository.PipelineControlRepository
import jyk.bcar.repository.UserRepository

internal fun draftCar(number: String) = Car(
    carNumber = number,
    title = "현대 쏘나타",
    company = "현대",
    detailPageNum = number,
    agency = "상사",
    seller = "판매자",
    sellerPhone = "010-0000-0000",
    price = 1500,
)

internal fun carDetail() = CarDetail(
    category = "중형차",
    displacement = 1999,
    modelYear = "2020-01",
    mileage = 50000,
    color = "검정",
    gearBox = "오토",
    fuelType = "휘발유",
    presentationNumber = "1",
    hasAccident = "무사고",
    registerNumber = "1",
    presentationsDate = "2020-01-01",
    hasSeizure = false,
    hasMortgage = false,
    carCheckSrc = "https://example.com/check",
    images = emptyList(),
)

internal val targetUser = TargetAdminUser("first", "pw", "target", 10, "example.com")
internal val nextTargetUser = targetUser.copy(id = "second")

internal class RecordingCars(
    val cars: List<Car>,
    private val onSave: (List<Car>) -> Unit = {},
) : CarRepository {
    val writes = mutableListOf<List<Car>>()
    val scans = mutableListOf<Pair<Int, Int>>()
    val saved: List<Car> get() = writes.flatten()

    override suspend fun findAll(segment: Int, totalSegments: Int): List<Car> {
        scans += segment to totalSegments
        return cars
    }

    override suspend fun findByAssignedUser(userId: String) = cars.filter { it.assignedUserId == userId }

    override suspend fun saveAll(cars: List<Car>) {
        onSave(cars)
        writes += cars.toList()
    }
}

internal class RecordingControl(
    private val stopped: Boolean = false,
    private val doneCount: Int = 1,
    private val onReset: () -> Unit = {},
) : PipelineControlRepository {
    var completed = 0
    var resets = 0

    override suspend fun isDetailCollectionStopped() = stopped

    override suspend fun markDetailChainDone(): Int {
        completed++
        return doneCount
    }

    override suspend fun resetDetailChains() {
        onReset()
        resets++
    }
}

internal class FixedUsers(
    private val users: List<TargetAdminUser> = listOf(targetUser, nextTargetUser),
) : UserRepository {
    override suspend fun findSourceAdminUser() = SourceAdminUser("source", "pw")

    override suspend fun findAllTargetAdminUsers() = users
}

internal class FixedCategories : CategoryTreeRepository {
    override suspend fun findCategoryTree() = CategoryTree(
        segments = listOf(CategoryTree.Segment("중대형", "segment", 0)),
        companies = listOf(CategoryTree.Company("현대", "hyundai", 0, CategoryTree.Origin.DOMESTIC)),
    )

    override suspend fun saveCategoryTree(tree: CategoryTree) = error("Unexpected category write")
}
