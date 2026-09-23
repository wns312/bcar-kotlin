package jyk.bcar.repository

import jyk.bcar.configuration.DynamoDbProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import jyk.bcar.domain.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import software.amazon.awssdk.services.dynamodb.model.WriteRequest
import java.time.Duration
import java.time.Instant

@Component
class DynamoDbCarRepository(
    private val properties: DynamoDbProperties,
    private val client: DynamoDbClient = defaultClient(),
) : CarRepository {
    companion object {
        private const val BATCH_WRITE_LIMIT = 25
        private const val ASSIGNED_USER_INDEX = "assignedUserId-index"
        private const val CONTROL_KEY = "_control"
        private const val STOP_DETAIL_ATTR = "stopDetail"
        private const val CHAINS_DONE_ATTR = "detailChainsDone"

        // Fargate에서 async(netty) 클라이언트가 응답 없이 매달린 적 있음. Secrets Manager와 같은 sync(apache) 경로 + 타임아웃
        fun defaultClient(): DynamoDbClient =
            DynamoDbClient
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .overrideConfiguration(
                    ClientOverrideConfiguration
                        .builder()
                        .apiCallTimeout(Duration.ofMinutes(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .build(),
                ).build()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun findAll(segment: Int, totalSegments: Int): List<Car> = withContext(Dispatchers.IO) {
        require(segment in 0 until totalSegments) { "segment=$segment out of range for totalSegments=$totalSegments" }
        val cars = client
            .scanPaginator {
                it.tableName(properties.carsTable)
                if (totalSegments > 1) it.segment(segment).totalSegments(totalSegments)
            }.items()
            .filterNot { it.getValue("carNumber").s().startsWith("_") }
            .map(::itemToCar)
        logger.info("Scanned ${cars.size} cars from ${properties.carsTable} (segment $segment/$totalSegments)")
        cars
    }

    override suspend fun findByAssignedUser(userId: String): List<Car> = withContext(Dispatchers.IO) {
        client
            .queryPaginator {
                it
                    .tableName(properties.carsTable)
                    .indexName(ASSIGNED_USER_INDEX)
                    .keyConditionExpression("assignedUserId = :u")
                    .expressionAttributeValues(mapOf(":u" to s(userId)))
            }.items()
            .map(::itemToCar)
    }

    override suspend fun saveAll(cars: List<Car>) = withContext(Dispatchers.IO) {
        logger.info("Saving ${cars.size} cars to ${properties.carsTable}")
        cars.chunked(BATCH_WRITE_LIMIT).forEach { chunk ->
            val writes = chunk.map { car ->
                WriteRequest.builder().putRequest { it.item(carToItem(car)) }.build()
            }
            var unprocessed = mapOf(properties.carsTable to writes)
            while (unprocessed.isNotEmpty()) {
                unprocessed = client.batchWriteItem { it.requestItems(unprocessed) }.unprocessedItems()
                if (unprocessed.isNotEmpty()) delay(200)
            }
        }
    }

    override suspend fun isDetailCollectionStopped(): Boolean = withContext(Dispatchers.IO) {
        client
            .getItem {
                it.tableName(properties.carsTable).key(mapOf("carNumber" to AttributeValue.fromS(CONTROL_KEY)))
            }.item()[STOP_DETAIL_ATTR]
            ?.bool() == true
    }

    override suspend fun markDetailChainDone(): Int = withContext(Dispatchers.IO) {
        client
            .updateItem {
                it
                    .tableName(properties.carsTable)
                    .key(mapOf("carNumber" to AttributeValue.fromS(CONTROL_KEY)))
                    .updateExpression("ADD $CHAINS_DONE_ATTR :one")
                    .expressionAttributeValues(mapOf(":one" to n(1)))
                    .returnValues(ReturnValue.UPDATED_NEW)
            }.attributes()
            .getValue(CHAINS_DONE_ATTR)
            .n()
            .toInt()
    }

    override suspend fun resetDetailChains() = withContext(Dispatchers.IO) {
        client.updateItem {
            it
                .tableName(properties.carsTable)
                .key(mapOf("carNumber" to AttributeValue.fromS(CONTROL_KEY)))
                .updateExpression("SET $CHAINS_DONE_ATTR = :zero")
                .expressionAttributeValues(mapOf(":zero" to n(0)))
        }
        Unit
    }
}

internal fun carToItem(car: Car): Map<String, AttributeValue> = buildMap {
    put("carNumber", s(car.carNumber))
    put("title", s(car.title))
    put("company", s(car.company))
    put("detailPageNum", s(car.detailPageNum))
    put("agency", s(car.agency))
    put("seller", s(car.seller))
    put("sellerPhone", s(car.sellerPhone))
    put("price", n(car.price))
    put("isActive", AttributeValue.fromBool(car.isActive))
    car.detail?.let { put("detail", AttributeValue.fromM(detailToItem(it))) }
    car.detailError?.let { put("detailError", s(it)) }
    car.assignedUserId?.let { put("assignedUserId", s(it)) }
    car.assignedAt?.let { put("assignedAt", s(it.toString())) }
    car.targetSite?.let { put("targetSite", s(it)) }
    put("uploadStatus", s(car.uploadStatus.name))
    car.uploadedAt?.let { put("uploadedAt", s(it.toString())) }
    car.uploadError?.let { put("uploadError", s(it)) }
    car.externalId?.let { put("externalId", s(it)) }
}

internal fun itemToCar(item: Map<String, AttributeValue>): Car =
    Car(
        carNumber = item.str("carNumber"),
        title = item.str("title"),
        company = item.str("company"),
        detailPageNum = item.str("detailPageNum"),
        agency = item.str("agency"),
        seller = item.str("seller"),
        sellerPhone = item.str("sellerPhone"),
        price = item.int("price"),
        isActive = item.getValue("isActive").bool(),
        detail = item["detail"]?.m()?.let(::itemToDetail),
        detailError = item["detailError"]?.s(),
        assignedUserId = item["assignedUserId"]?.s(),
        assignedAt = item["assignedAt"]?.s()?.let(Instant::parse),
        targetSite = item["targetSite"]?.s(),
        uploadStatus = item["uploadStatus"]?.s()?.let(UploadStatus::valueOf) ?: UploadStatus.NONE,
        uploadedAt = item["uploadedAt"]?.s()?.let(Instant::parse),
        uploadError = item["uploadError"]?.s(),
        externalId = item["externalId"]?.s(),
    )

private fun detailToItem(detail: CarDetail): Map<String, AttributeValue> = buildMap {
    put("category", s(detail.category))
    put("displacement", n(detail.displacement))
    put("modelYear", s(detail.modelYear))
    put("mileage", n(detail.mileage))
    put("color", s(detail.color))
    put("gearBox", s(detail.gearBox))
    put("fuelType", s(detail.fuelType))
    put("presentationNumber", s(detail.presentationNumber))
    put("hasAccident", s(detail.hasAccident))
    put("registerNumber", s(detail.registerNumber))
    put("presentationsDate", s(detail.presentationsDate))
    put("hasSeizure", AttributeValue.fromBool(detail.hasSeizure))
    put("hasMortgage", AttributeValue.fromBool(detail.hasMortgage))
    put("carCheckSrc", s(detail.carCheckSrc))
    put("images", AttributeValue.fromL(detail.images.map(::s)))
}

private fun itemToDetail(item: Map<String, AttributeValue>): CarDetail =
    CarDetail(
        category = item.str("category"),
        displacement = item.int("displacement"),
        modelYear = item.str("modelYear"),
        mileage = item.int("mileage"),
        color = item.str("color"),
        gearBox = item.str("gearBox"),
        fuelType = item.str("fuelType"),
        presentationNumber = item.str("presentationNumber"),
        hasAccident = item.str("hasAccident"),
        registerNumber = item.str("registerNumber"),
        presentationsDate = item.str("presentationsDate"),
        hasSeizure = item.getValue("hasSeizure").bool(),
        hasMortgage = item.getValue("hasMortgage").bool(),
        carCheckSrc = item.str("carCheckSrc"),
        images = item.getValue("images").l().map { it.s() },
    )

private fun s(value: String): AttributeValue = AttributeValue.fromS(value)

private fun n(value: Int): AttributeValue = AttributeValue.fromN(value.toString())

private fun Map<String, AttributeValue>.str(key: String): String = getValue(key).s()

private fun Map<String, AttributeValue>.int(key: String): Int = getValue(key).n().toInt()
