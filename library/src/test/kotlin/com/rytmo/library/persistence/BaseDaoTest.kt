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

class BaseDaoTest {
    private lateinit var dynamoDbEnhancedClient: DynamoDbEnhancedClient
    private lateinit var table: DynamoDbTable<TestEntity>
    private lateinit var paginationTokenEncryptor: PaginationTokenEncryptor
    private lateinit var baseDao: TestBaseDao

    @BeforeEach
    fun setUp() {
        dynamoDbEnhancedClient = mock()
        table = mock()
        paginationTokenEncryptor = PaginationTokenEncryptor("0123456789abcdef")
        baseDao = TestBaseDao(dynamoDbEnhancedClient, table, paginationTokenEncryptor)
    }

    @Test
    fun `create should set id, createdAt and updatedAt`() {
        val entity = TestEntity()
        entity.name = "Test"

        val captor = argumentCaptor<TestEntity>()

        baseDao.create(entity)

        verify(table).putItem(captor.capture())

        val savedEntity = captor.firstValue
        assertNotNull(savedEntity.id)
        assertTrue(savedEntity.id.isNotEmpty())
        assertNotNull(savedEntity.createdAt)
        assertNotNull(savedEntity.updatedAt)
        assertEquals(savedEntity.createdAt, savedEntity.updatedAt)
    }

    @Test
    fun `update should update updatedAt`() {
        val originalCreatedAt = Instant.now().minusSeconds(3600)
        val entity =
            TestEntity().apply {
                id = "test-id"
                name = "Test"
                createdAt = originalCreatedAt
                updatedAt = originalCreatedAt
            }

        baseDao.update(entity)

        verify(table).updateItem(any<TestEntity>())
        assertNotNull(entity.updatedAt)
        assertTrue(entity.updatedAt!!.isAfter(originalCreatedAt))
        assertEquals(originalCreatedAt, entity.createdAt)
    }

    @Test
    fun `get should call table getItem with correct key`() {
        val expectedEntity =
            TestEntity().apply {
                id = "test-id"
                name = "Test"
            }
        whenever(table.getItem(any<Key>())).thenReturn(expectedEntity)

        val result = baseDao.get("test-id")

        assertNotNull(result)
        assertEquals("test-id", result?.id)
    }

    @Test
    fun `delete should call table deleteItem with correct key`() {
        baseDao.delete("test-id")

        verify(table).deleteItem(any<Key>())
    }

    class TestEntity : Persistable {
        override var id: String = ""
        var name: String = ""
        override var createdAt: Instant? = null
        override var updatedAt: Instant? = null
        override var ttl: Long? = null
    }

    class TestBaseDao(dynamoDbEnhancedClient: DynamoDbEnhancedClient, table: DynamoDbTable<TestEntity>, paginationTokenEncryptor: PaginationTokenEncryptor) :
        BaseDao<TestEntity>(dynamoDbEnhancedClient, table, paginationTokenEncryptor)
}
