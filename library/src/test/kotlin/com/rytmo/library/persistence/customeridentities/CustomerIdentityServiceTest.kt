package com.rytmo.library.persistence.customeridentities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CustomerIdentityServiceTest {
    private lateinit var customerIdentityDao: CustomerIdentityDao
    private lateinit var customerIdentityService: CustomerIdentityService

    @BeforeEach
    fun setUp() {
        customerIdentityDao = mock()
        customerIdentityService = CustomerIdentityService(customerIdentityDao)
    }

    @Test
    fun `linkIdentity should create identity with correct fields`() {
        val captor = argumentCaptor<CustomerIdentityDynamoDbBean>()
        whenever(customerIdentityDao.create(captor.capture())).thenAnswer { invocation ->
            val identity = invocation.getArgument<CustomerIdentityDynamoDbBean>(0)
            identity.createdAt = Instant.now()
            identity.updatedAt = Instant.now()
            identity
        }

        val result =
            customerIdentityService.linkIdentity(
                internalCustomerId = "internal-123",
                provider = "privy",
                externalId = "privy-456",
            )

        assertEquals("internal-123", captor.firstValue.internalCustomerId)
        assertEquals("privy", captor.firstValue.provider)
        assertEquals("privy-456", captor.firstValue.externalId)
        assertNotNull(result)
    }

    @Test
    fun `getIdentity should return identity by composite key`() {
        val expectedIdentity =
            CustomerIdentityDynamoDbBean().apply {
                internalCustomerId = "internal-123"
                provider = "privy"
                externalId = "privy-456"
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        whenever(customerIdentityDao.get("internal-123", "privy")).thenReturn(expectedIdentity)

        val result = customerIdentityService.getIdentity("internal-123", "privy")

        assertNotNull(result)
        assertEquals("internal-123", result?.internalCustomerId)
        assertEquals("privy", result?.provider)
        assertEquals("privy-456", result?.externalId)
    }

    @Test
    fun `getIdentity should return null when identity not found`() {
        whenever(customerIdentityDao.get("non-existent", "privy")).thenReturn(null)

        val result = customerIdentityService.getIdentity("non-existent", "privy")

        assertNull(result)
    }

    @Test
    fun `unlinkIdentity should call dao delete with correct keys`() {
        customerIdentityService.unlinkIdentity("internal-123", "privy")

        verify(customerIdentityDao).delete("internal-123", "privy")
    }

    @Test
    fun `listIdentitiesForCustomer should return all identities for customer`() {
        val identities =
            listOf(
                CustomerIdentityDynamoDbBean().apply {
                    internalCustomerId = "internal-123"
                    provider = "privy"
                    externalId = "privy-456"
                },
                CustomerIdentityDynamoDbBean().apply {
                    internalCustomerId = "internal-123"
                    provider = "bridge"
                    externalId = "bridge-789"
                },
            )
        whenever(customerIdentityDao.listByPartitionKey("internal-123")).thenReturn(identities)

        val result = customerIdentityService.listIdentitiesForCustomer("internal-123")

        assertEquals(2, result.size)
        assertEquals("privy", result[0].provider)
        assertEquals("bridge", result[1].provider)
    }

    @Test
    fun `listIdentitiesForCustomer should return empty list when no identities found`() {
        whenever(customerIdentityDao.listByPartitionKey("internal-123")).thenReturn(emptyList())

        val result = customerIdentityService.listIdentitiesForCustomer("internal-123")

        assertEquals(0, result.size)
    }

    @Test
    fun `getInternalCustomerIdByExternalId should return internal id when found`() {
        val identity =
            CustomerIdentityDynamoDbBean().apply {
                internalCustomerId = "internal-123"
                provider = "privy"
                externalId = "privy-456"
            }
        whenever(customerIdentityDao.findByExternalId("privy-456")).thenReturn(identity)

        val result = customerIdentityService.getInternalCustomerIdByExternalId("privy-456")

        assertEquals("internal-123", result)
    }

    @Test
    fun `getInternalCustomerIdByExternalId should return null when not found`() {
        whenever(customerIdentityDao.findByExternalId("non-existent")).thenReturn(null)

        val result = customerIdentityService.getInternalCustomerIdByExternalId("non-existent")

        assertNull(result)
    }

    @Test
    fun `findIdentityByExternalId should return full identity when found`() {
        val expectedIdentity =
            CustomerIdentityDynamoDbBean().apply {
                internalCustomerId = "internal-123"
                provider = "bridge"
                externalId = "bridge-789"
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        whenever(customerIdentityDao.findByExternalId("bridge-789")).thenReturn(expectedIdentity)

        val result = customerIdentityService.findIdentityByExternalId("bridge-789")

        assertNotNull(result)
        assertEquals("internal-123", result?.internalCustomerId)
        assertEquals("bridge", result?.provider)
        assertEquals("bridge-789", result?.externalId)
    }

    @Test
    fun `findIdentityByExternalId should return null when not found`() {
        whenever(customerIdentityDao.findByExternalId("non-existent")).thenReturn(null)

        val result = customerIdentityService.findIdentityByExternalId("non-existent")

        assertNull(result)
    }

    @Test
    fun `update should call dao update`() {
        val identity =
            CustomerIdentityDynamoDbBean().apply {
                internalCustomerId = "internal-123"
                provider = "privy"
                externalId = "privy-456"
            }
        whenever(customerIdentityDao.update(any())).thenAnswer { it.getArgument(0) }

        val result = customerIdentityService.update(identity)

        verify(customerIdentityDao).update(identity)
        assertNotNull(result)
    }
}
