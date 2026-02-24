package com.rytmo.library.services

import com.rytmo.library.bridge.model.ExternalAccount1
import com.rytmo.library.bridge.model.ExternalAccountResponse
import com.rytmo.library.exceptions.ExternalAccountException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.externalaccounts.CreateExternalAccountRequest
import com.rytmo.models.externalaccounts.ExternalAccountAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class ExternalAccountServiceTest {
    private lateinit var bridgeService: BridgeService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var externalAccountService: ExternalAccountService

    @BeforeEach
    fun setUp() {
        bridgeService = mock()
        customerIdentityService = mock()
        externalAccountService = ExternalAccountService(bridgeService, customerIdentityService)
    }

    @Test
    fun `createByExternalId should return mapped response`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)

        val bridgeIdentity =
            CustomerIdentityDynamoDbBean().apply {
                this.internalCustomerId = internalCustomerId
                provider = "bridge"
                this.externalId = bridgeCustomerId
            }
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge"))
            .thenReturn(bridgeIdentity)

        val bridgeResponse =
            ExternalAccountResponse(
                null,
                "6789",
                OffsetDateTime.parse("2024-01-15T10:00:01Z"),
                OffsetDateTime.parse("2024-01-16T10:00:01Z"),
                true,
                null,
            )
                .id("ext_acct_001")
                .accountOwnerName("Jane Doe")
                .bankName("Test Bank")

        whenever(bridgeService.createExternalAccount(any(), any(), any(), anyOrNull(), any(), any()))
            .thenReturn(bridgeResponse)

        val request =
            CreateExternalAccountRequest(
                accountType = "clabe",
                accountOwnerName = "Jane Doe",
                accountNumber = "626899715090851234",
                address =
                ExternalAccountAddress(
                    streetLine1 = "Av. Reforma",
                    city = "Mexico City",
                    country = "MEX",
                ),
            )

        val result = externalAccountService.createByExternalId(externalId, request)

        assertEquals("ext_acct_001", result.id)
        assertEquals("Jane Doe", result.accountOwnerName)
        assertEquals("Test Bank", result.bankName)
        assertEquals("6789", result.last4)
        assertEquals("true", result.active)
        assertEquals("2024-01-15T10:00:01Z", result.createdAt)
        assertEquals("2024-01-16T10:00:01Z", result.updatedAt)
    }

    @Test
    fun `createByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val request =
            CreateExternalAccountRequest(
                accountType = "clabe",
                accountOwnerName = "Jane Doe",
                accountNumber = "626899715090851234",
                address =
                ExternalAccountAddress(
                    streetLine1 = "Av. Reforma",
                    city = "Mexico City",
                    country = "MEX",
                ),
            )

        val exception =
            assertThrows(ExternalAccountException::class.java) {
                externalAccountService.createByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
        verify(bridgeService, never())
            .createExternalAccount(any(), any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `createByExternalId should throw when bridge identity not found`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val request =
            CreateExternalAccountRequest(
                accountType = "clabe",
                accountOwnerName = "Jane Doe",
                accountNumber = "626899715090851234",
                address =
                ExternalAccountAddress(
                    streetLine1 = "Av. Reforma",
                    city = "Mexico City",
                    country = "MEX",
                ),
            )

        val exception =
            assertThrows(ExternalAccountException::class.java) {
                externalAccountService.createByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
        verify(bridgeService, never())
            .createExternalAccount(any(), any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `createByExternalId should wrap bridge API exception`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)

        val bridgeIdentity =
            CustomerIdentityDynamoDbBean().apply {
                this.internalCustomerId = internalCustomerId
                provider = "bridge"
                this.externalId = bridgeCustomerId
            }
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge"))
            .thenReturn(bridgeIdentity)
        whenever(bridgeService.createExternalAccount(any(), any(), any(), anyOrNull(), any(), any()))
            .thenThrow(BridgeApiException("API error", 500))

        val request =
            CreateExternalAccountRequest(
                accountType = "clabe",
                accountOwnerName = "Jane Doe",
                accountNumber = "626899715090851234",
                address =
                ExternalAccountAddress(
                    streetLine1 = "Av. Reforma",
                    city = "Mexico City",
                    country = "MEX",
                ),
            )

        val exception =
            assertThrows(ExternalAccountException::class.java) {
                externalAccountService.createByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Failed to create external account") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
    }

    @Test
    fun `listByExternalId should return mapped accounts`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)

        val bridgeIdentity =
            CustomerIdentityDynamoDbBean().apply {
                this.internalCustomerId = internalCustomerId
                provider = "bridge"
                this.externalId = bridgeCustomerId
            }
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge"))
            .thenReturn(bridgeIdentity)

        val bridgeResponse =
            ExternalAccount1()
                .addDataItem(
                    ExternalAccountResponse(null, "1234", null, null, null, null)
                        .id("ext_acct_001")
                        .accountOwnerName("Jane Doe")
                        .bankName("First Bank"),
                )
                .addDataItem(
                    ExternalAccountResponse(null, "5678", null, null, null, null)
                        .id("ext_acct_002")
                        .accountOwnerName("Jane Doe")
                        .bankName("Second Bank"),
                )

        whenever(bridgeService.listExternalAccounts(bridgeCustomerId)).thenReturn(bridgeResponse)

        val result = externalAccountService.listByExternalId(externalId)

        assertEquals(2, result.size)
        assertEquals("ext_acct_001", result[0].id)
        assertEquals("First Bank", result[0].bankName)
        assertEquals("1234", result[0].last4)
        assertEquals("ext_acct_002", result[1].id)
        assertEquals("Second Bank", result[1].bankName)
        assertEquals("5678", result[1].last4)
    }

    @Test
    fun `listByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(ExternalAccountException::class.java) {
                externalAccountService.listByExternalId(externalId)
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
    }

    @Test
    fun `listByExternalId should throw when bridge identity not found`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val exception =
            assertThrows(ExternalAccountException::class.java) {
                externalAccountService.listByExternalId(externalId)
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
    }
}
