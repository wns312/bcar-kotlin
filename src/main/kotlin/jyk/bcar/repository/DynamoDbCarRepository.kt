package jyk.bcar.repository

import jyk.bcar.configuration.DynamoDbProperties
import jyk.bcar.domain.Car
import jyk.bcar.domain.CarDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.WriteRequest

@Component
class DynamoDbCarRepository(
    private val properties: DynamoDbProperties,
    private val client: DynamoDbAsyncClient = defaultClient(),
) : CarRepository {
    companion object {
        private const val BATCH_WRITE_LIMIT = 25

        fun defaultClient(): DynamoDbAsyncClient =
            DynamoDbAsyncClient
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .build()
    }

    override suspend fun findAll(): List<Car> {
        val cars = mutableListOf<Car>()
        client
            .scanPaginator { it.tableName(properties.carsTable) }
            .items()
            .subscribe { cars += itemToCar(it) }
            .await()
        return cars
    }

    override suspend fun saveAll(cars: List<Car>) {
        cars.chunked(BATCH_WRITE_LIMIT).forEach { chunk ->
            val writes = chunk.map { car ->
                WriteRequest.builder().putRequest { it.item(carToItem(car)) }.build()
            }
            var unprocessed = mapOf(properties.carsTable to writes)
            while (unprocessed.isNotEmpty()) {
                unprocessed = client.batchWriteItem { it.requestItems(unprocessed) }.await().unprocessedItems()
                if (unprocessed.isNotEmpty()) delay(200)
            }
        }
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
