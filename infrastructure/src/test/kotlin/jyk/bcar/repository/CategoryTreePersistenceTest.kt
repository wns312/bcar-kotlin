package jyk.bcar.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jyk.bcar.configuration.DynamoDbProperties
import jyk.bcar.domain.CategoryTree
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import java.util.function.Consumer

class CategoryTreePersistenceTest {
    @Test
    fun serializesExistingSchemaAndReadsUnknownFieldsWithoutDomainJacksonAnnotations() = runTest {
        var stored: Map<String, AttributeValue> = emptyMap()
        val client = mock(DynamoDbClient::class.java) { call ->
            when (call.method.name) {
                "putItem" -> {
                    val builder = PutItemRequest.builder()
                    call.getArgument<Consumer<PutItemRequest.Builder>>(0).accept(builder)
                    val request = builder.build()
                    assertEquals("test-cars", request.tableName())
                    stored = request.item()
                    PutItemResponse.builder().build()
                }
                "getItem" -> GetItemResponse.builder().item(stored).build()
                else -> error("Unexpected DynamoDB call: ${call.method.name}")
            }
        }
        val repository = DynamoDbCarRepository(DynamoDbProperties("test-cars"), client)
        val tree = CategoryTree(
            listOf(CategoryTree.Segment("중대형", "s", 0)),
            listOf(
                CategoryTree.Company(
                    "현대",
                    "c",
                    0,
                    CategoryTree.Origin.DOMESTIC,
                    listOf(CategoryTree.Model("쏘나타", "m", 0, "중대형", listOf(CategoryTree.DetailModel("DN8", "d", 0)))),
                ),
            ),
        )

        repository.saveCategoryTree(tree)

        assertEquals("_categories", stored.getValue("carNumber").s())
        assertNotNull(stored["updatedAt"])
        val serialized = stored.getValue("tree").s()
        assertEquals(
            setOf("segments", "companies"),
            jacksonObjectMapper()
                .readTree(serialized)
                .fieldNames()
                .asSequence()
                .toSet(),
        )
        stored = stored + ("tree" to AttributeValue.fromS(serialized.dropLast(1) + ",\"futureField\":true,\"modelCount\":999}"))
        assertEquals(tree, repository.findCategoryTree())
    }
}
