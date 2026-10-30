package jyk.bcar.configuration

import jyk.bcar.automation.job.boolOption
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.port.DetailCarSource
import jyk.bcar.port.TargetCarSite
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.CategoryTreeRepository
import jyk.bcar.repository.PipelineControlRepository
import jyk.bcar.repository.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertFailsWith

class JobConfigurationTest {
    private val configuration = JobConfiguration()

    @Test
    fun uploadOptionsAreParsedOnlyForSelectedJob() {
        val args = arrayOf("--delete=invalid", "--submit=invalid", "--limit=invalid")
        val properties = UploadProperties(ClassPathResource("upload-comment.txt"), emptyList())
        configuration.syncUploadJob(
            mock(TargetCarSite::class.java),
            mock(UserRepository::class.java),
            mock(CarRepository::class.java),
            mock(CategoryTreeRepository::class.java),
            properties,
            DefaultApplicationArguments("--job=assign-cars", *args),
        )

        assertFailsWith<IllegalArgumentException> {
            configuration.syncUploadJob(
                mock(TargetCarSite::class.java),
                mock(UserRepository::class.java),
                mock(CarRepository::class.java),
                mock(CategoryTreeRepository::class.java),
                properties,
                DefaultApplicationArguments("--job=sync-upload", "--user=u", "--delete=invalid"),
            )
        }
    }

    @Test
    fun detailCliOptionsReachUseCaseAndEmptyShardCompletes() = runTest {
        val scans = mutableListOf<Pair<Int, Int>>()
        var completions = 0
        val cars = object : CarRepository {
            override suspend fun findAll(segment: Int, totalSegments: Int): List<Car> {
                scans += segment to totalSegments
                return emptyList()
            }

            override suspend fun findByAssignedUser(userId: String) = error("Unexpected assigned query")

            override suspend fun saveAll(cars: List<Car>) = error("Unexpected write")
        }
        val control = object : PipelineControlRepository {
            override suspend fun isDetailCollectionStopped() = false

            override suspend fun markDetailChainDone(): Int = ++completions

            override suspend fun resetDetailChains() = error("Unexpected reset")
        }
        val source = object : DetailCarSource {
            override suspend fun fetch(detailPageNumber: String) = error("Unexpected fetch")

            override suspend fun parse(bytes: ByteArray): CarDetail = error("Unexpected parse")
        }
        val job = configuration.collectDetailJob(
            cars,
            control,
            DefaultApplicationArguments("--job=collect-detail", "--shards=3", "--shard=1", "--hop=4", "--idle=1"),
            BatchProperties(),
            source,
        )

        val result = job.execute()

        assertEquals(listOf(1 to 3), scans)
        assertEquals(1, result.shard)
        assertEquals(3, result.shards)
        assertEquals(4, result.hop)
        assertEquals(2, result.idleHops)
        assertTrue(result.chainEnded)
        assertEquals(1, completions)
    }

    @Test
    fun booleanCliDefaultsRemainTrueAndRejectOtherValues() {
        val defaults = DefaultApplicationArguments()
        assertTrue(defaults.boolOption("next", default = true))
        assertTrue(defaults.boolOption("delete", default = true))
        assertTrue(defaults.boolOption("submit", default = true))
        assertFalse(DefaultApplicationArguments("--submit=false").boolOption("submit", default = true))
        assertFailsWith<IllegalArgumentException> {
            DefaultApplicationArguments("--next=yes").boolOption("next", default = true)
        }
    }
}
