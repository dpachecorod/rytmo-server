package com.rytmo.library.services

import com.rytmo.library.bridge.model.Customer
import com.rytmo.library.bridge.model.CustomerStatus
import com.rytmo.library.exceptions.BridgeCustomerException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class BridgeCustomerServiceTest {
    private lateinit var bridgeService: BridgeService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var bridgeCustomerService: BridgeCustomerService

    @BeforeEach
    fun setUp() {
        bridgeService = mock()
        customerIdentityService = mock()
        bridgeCustomerService = BridgeCustomerService(bridgeService, customerIdentityService)
    }

    @Test
    fun `createByExternalId should create Bridge customer and link identity`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)

        val bridgeResponse =
            Customer(null, null, OffsetDateTime.parse("2026-02-20T10:00:01Z"), null, null, null, null)
                .id("bridge-cust-456")
                .status(CustomerStatus.ACTIVE)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")

        whenever(bridgeService.createCustomer("John", "Doe", "john@example.com"))
            .thenReturn(bridgeResponse)
        whenever(customerIdentityService.linkIdentity(any(), any(), any()))
            .thenReturn(CustomerIdentityDynamoDbBean())

        val result =
            bridgeCustomerService.createByExternalId(
                externalId = externalId,
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
            )

        assertEquals("bridge-cust-456", result.id)
        assertEquals("active", result.status)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("john@example.com", result.email)
        assertEquals("2026-02-20T10:00:01Z", result.createdAt)

        verify(customerIdentityService)
            .linkIdentity(
                internalCustomerId = internalCustomerId,
                provider = "bridge",
                externalId = "bridge-cust-456",
            )
    }

    @Test
    fun `createByExternalId should throw BridgeCustomerException when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(BridgeCustomerException::class.java) {
                bridgeCustomerService.createByExternalId(
                    externalId = externalId,
                    firstName = "John",
                    lastName = "Doe",
                    email = "john@example.com",
                )
            }

        assertEquals("Customer not found for external ID: $externalId", exception.message)
        verify(bridgeService, never()).createCustomer(any(), any(), any())
    }

    @Test
    fun `createByExternalId should throw BridgeCustomerException when Bridge API fails`() {
        val externalId = "did:privy:user123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn("customer-123")
        whenever(bridgeService.createCustomer(any(), any(), any()))
            .thenThrow(BridgeApiException("API error", 500))

        val exception =
            assertThrows(BridgeCustomerException::class.java) {
                bridgeCustomerService.createByExternalId(
                    externalId = externalId,
                    firstName = "John",
                    lastName = "Doe",
                    email = "john@example.com",
                )
            }

        assertEquals(
            "Failed to create Bridge customer: API error",
            exception.message,
        )
        verify(customerIdentityService, never()).linkIdentity(any(), any(), any())
    }
}
