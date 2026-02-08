package com.rytmo.library.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import java.time.Instant

class CompositeKeyBaseDaoTest {
    private lateinit var dynamoDbEnhancedClient: DynamoDbEnhancedClient
    private lateinit var table: DynamoDbTable<TestCompositeEntity>
    private lateinit var paginationTokenEncryptor: PaginationTokenEncryptor
    private lateinit var baseDao: TestCompositeKeyBaseDao

    @BeforeEach
    fun setUp() {
        dynamoDbEnhancedClient = mock()
        table = mock()
        paginationTokenEncryptor = PaginationTokenEncryptor("0123456789abcdef")
        baseDao = TestCompositeKeyBaseDao(dynamoDbEnhancedClient, table, paginationTokenEncryptor)
    }

    @Test
    fun `create should set createdAt and updatedAt`() {
        val entity =
            TestCompositeEntity().apply {
                partitionKey = "pk-123"
                sortKey = "sk-456"
                data = "Test"
            }

        val captor = argumentCaptor<TestCompositeEntity>()

        baseDao.create(entity)

        verify(table).putItem(captor.capture())

        val savedEntity = captor.firstValue
        assertNotNull(savedEntity.createdAt)
        assertNotNull(savedEntity.updatedAt)
        assertEquals(savedEntity.createdAt, savedEntity.updatedAt)
        assertEquals("pk-123", savedEntity.partitionKey)
        assertEquals("sk-456", savedEntity.sortKey)
    }

    @Test
    fun `update should update updatedAt`() {
        val originalCreatedAt = Instant.now().minusSeconds(3600)
        val entity =
            TestCompositeEntity().apply {
                partitionKey = "pk-123"
                sortKey = "sk-456"
                data = "Test"
                createdAt = originalCreatedAt
                updatedAt = originalCreatedAt
            }

        baseDao.update(entity)

        verify(table).updateItem(any<TestCompositeEntity>())
        assertNotNull(entity.updatedAt)
        assertTrue(entity.updatedAt!!.isAfter(originalCreatedAt))
        assertEquals(originalCreatedAt, entity.createdAt)
    }

    @Test
    fun `get should call table getItem with correct composite key`() {
        val expectedEntity =
            TestCompositeEntity().apply {
                partitionKey = "pk-123"
                sortKey = "sk-456"
                data = "Test"
            }
        whenever(table.getItem(any<Key>())).thenReturn(expectedEntity)

        val result = baseDao.get("pk-123", "sk-456")

        assertNotNull(result)
        assertEquals("pk-123", result?.partitionKey)
        assertEquals("sk-456", result?.sortKey)
    }

    @Test
    fun `delete should call table deleteItem with correct composite key`() {
        baseDao.delete("pk-123", "sk-456")

        verify(table).deleteItem(any<Key>())
    }

    class TestCompositeEntity : CompositeKeyPersistable {
        override var partitionKey: String = ""
        override var sortKey: String = ""
        var data: String = ""
        override var createdAt: Instant? = null
        override var updatedAt: Instant? = null
        override var ttl: Long? = null
    }

    class TestCompositeKeyBaseDao(dynamoDbEnhancedClient: DynamoDbEnhancedClient, table: DynamoDbTable<TestCompositeEntity>, paginationTokenEncryptor: PaginationTokenEncryptor) :
        CompositeKeyBaseDao<TestCompositeEntity>(
            dynamoDbEnhancedClient,
            table,
            paginationTokenEncryptor,
        )
}
