package com.rytmo.library.persistence.customers

import com.rytmo.library.persistence.PagedResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CustomerServiceTest {
    private lateinit var customerDao: CustomerDao
    private lateinit var customerService: CustomerService

    @BeforeEach
    fun setUp() {
        customerDao = mock()
        customerService = CustomerService(customerDao)
    }

    @Test
    fun `createCustomer should create customer with email and name`() {
        val captor = argumentCaptor<CustomerDynamoDbBean>()
        whenever(customerDao.create(captor.capture())).thenAnswer { invocation ->
            val customer = invocation.getArgument<CustomerDynamoDbBean>(0)
            customer.id = "test-id"
            customer.createdAt = Instant.now()
            customer.updatedAt = Instant.now()
            customer
        }

        val result = customerService.createCustomer("john@example.com", "John Doe")

        assertEquals("john@example.com", captor.firstValue.email)
        assertEquals("John Doe", captor.firstValue.name)
        assertNotNull(result.id)
    }

    @Test
    fun `get should return customer by id`() {
        val expectedCustomer =
            CustomerDynamoDbBean().apply {
                id = "test-id"
                email = "john@example.com"
                name = "John Doe"
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        whenever(customerDao.get("test-id")).thenReturn(expectedCustomer)

        val result = customerService.get("test-id")

        assertNotNull(result)
        assertEquals("test-id", result?.id)
        assertEquals("john@example.com", result?.email)
        assertEquals("John Doe", result?.name)
    }

    @Test
    fun `get should return null when customer not found`() {
        whenever(customerDao.get("non-existent")).thenReturn(null)

        val result = customerService.get("non-existent")

        assertNull(result)
    }

    @Test
    fun `updateCustomer should update existing customer`() {
        val existingCustomer =
            CustomerDynamoDbBean().apply {
                id = "test-id"
                email = "old@example.com"
                name = "Old Name"
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        whenever(customerDao.get("test-id")).thenReturn(existingCustomer)
        whenever(customerDao.update(any())).thenAnswer { it.getArgument(0) }

        val result = customerService.updateCustomer("test-id", "new@example.com", "New Name")

        assertNotNull(result)
        assertEquals("new@example.com", result?.email)
        assertEquals("New Name", result?.name)
    }

    @Test
    fun `updateCustomer should return null when customer not found`() {
        whenever(customerDao.get("non-existent")).thenReturn(null)

        val result = customerService.updateCustomer("non-existent", "email@example.com", "Name")

        assertNull(result)
    }

    @Test
    fun `updateCustomer should only update provided fields`() {
        val existingCustomer =
            CustomerDynamoDbBean().apply {
                id = "test-id"
                email = "old@example.com"
                name = "Old Name"
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        whenever(customerDao.get("test-id")).thenReturn(existingCustomer)
        whenever(customerDao.update(any())).thenAnswer { it.getArgument(0) }

        val result = customerService.updateCustomer("test-id", null, "New Name")

        assertNotNull(result)
        assertEquals("old@example.com", result?.email)
        assertEquals("New Name", result?.name)
    }

    @Test
    fun `delete should call dao delete`() {
        customerService.delete("test-id")

        verify(customerDao).delete("test-id")
    }

    @Test
    fun `list should return paged results`() {
        val customers =
            listOf(
                CustomerDynamoDbBean().apply {
                    id = "id-1"
                    email = "user1@example.com"
                    name = "User 1"
                },
                CustomerDynamoDbBean().apply {
                    id = "id-2"
                    email = "user2@example.com"
                    name = "User 2"
                },
            )
        whenever(customerDao.list(eq(20), isNull())).thenReturn(PagedResult(customers, null))

        val result = customerService.list()

        assertEquals(2, result.items.size)
        assertNull(result.nextPageToken)
    }

    @Test
    fun `list should pass nextPageToken to dao`() {
        val customers = listOf<CustomerDynamoDbBean>()
        val nextToken = "encrypted-token"
        whenever(customerDao.list(eq(10), eq(nextToken))).thenReturn(PagedResult(customers, null))

        customerService.list(limit = 10, nextPageToken = nextToken)

        verify(customerDao).list(eq(10), eq(nextToken))
    }
}
