package com.rytmo.library.services

import com.rytmo.library.bridge.model.IndividualKycLinkResponse
import com.rytmo.library.bridge.model.KycStatus
import com.rytmo.library.bridge.model.TosStatus
import com.rytmo.library.exceptions.KycLinkCreationException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.customers.CustomerDynamoDbBean
import com.rytmo.library.persistence.customers.CustomerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class KycServiceTest {
    private lateinit var bridgeService: BridgeService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var customerService: CustomerService
    private lateinit var kycService: KycService

    @BeforeEach
    fun setUp() {
        bridgeService = mock()
        customerIdentityService = mock()
        customerService = mock()
        kycService = KycService(bridgeService, customerIdentityService, customerService)
    }

    @Test
    fun `createKycLinkByExternalId should lookup internal ID and create link`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val fullName = "John Doe"
        val email = "john@example.com"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)

        val customer = CustomerDynamoDbBean().apply { id = internalCustomerId }
        whenever(customerService.get(internalCustomerId)).thenReturn(customer)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val bridgeResponse =
            IndividualKycLinkResponse(null)
                .id("kyc_link_abc")
                .customerId("bridge-cust-456")
                .kycLink("https://kyc.bridge.xyz/abc")
                .kycStatus(KycStatus.NOT_STARTED)
                .tosLink("https://tos.bridge.xyz/abc")
                .tosStatus(TosStatus.PENDING)
                .createdAt(OffsetDateTime.parse("2024-01-15T10:00:01Z"))

        whenever(bridgeService.createKycLink(fullName, email)).thenReturn(bridgeResponse)
        whenever(customerIdentityService.linkIdentity(any(), any(), any()))
            .thenReturn(CustomerIdentityDynamoDbBean())

        val result = kycService.createKycLinkByExternalId(externalId, fullName, email)

        assertEquals("kyc_link_abc", result.id)
        verify(customerIdentityService).getInternalCustomerIdByExternalId(externalId)
        verify(customerIdentityService).linkIdentity(internalCustomerId, "bridge", "bridge-cust-456")
    }

    @Test
    fun `createKycLinkByExternalId should throw when external ID not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(KycLinkCreationException::class.java) {
                kycService.createKycLinkByExternalId(externalId, "John Doe", "john@example.com", "uri")
            }

        assertTrue(exception.message?.contains("Customer not found for external ID") == true)
        verify(bridgeService, never()).createKycLink(any(), any(), any(), any())
    }

    @Test
    fun `createKycLink should create link and save identity`() {
        val internalCustomerId = "customer-123"
        val fullName = "John Doe"
        val email = "john@example.com"

        val customer = CustomerDynamoDbBean().apply { id = internalCustomerId }
        whenever(customerService.get(internalCustomerId)).thenReturn(customer)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val bridgeResponse =
            IndividualKycLinkResponse(null)
                .id("kyc_link_abc")
                .customerId("bridge-cust-456")
                .kycLink("https://kyc.bridge.xyz/abc")
                .kycStatus(KycStatus.NOT_STARTED)
                .tosLink("https://tos.bridge.xyz/abc")
                .tosStatus(TosStatus.PENDING)
                .createdAt(OffsetDateTime.parse("2024-01-15T10:00:01Z"))

        whenever(bridgeService.createKycLink(fullName, email)).thenReturn(bridgeResponse)
        whenever(customerIdentityService.linkIdentity(any(), any(), any()))
            .thenReturn(CustomerIdentityDynamoDbBean())

        val result = kycService.createKycLink(internalCustomerId, fullName, email)

        assertEquals("kyc_link_abc", result.id)
        assertEquals("https://kyc.bridge.xyz/abc", result.kycLink)
        assertEquals("not_started", result.kycStatus)
        assertEquals("https://tos.bridge.xyz/abc", result.tosLink)
        assertEquals("pending", result.tosStatus)
        assertEquals("2024-01-15T10:00:01Z", result.createdAt)

        verify(customerIdentityService).linkIdentity(internalCustomerId, "bridge", "bridge-cust-456")
    }

    @Test
    fun `createKycLink should throw when customer not found`() {
        val internalCustomerId = "non-existent"

        whenever(customerService.get(internalCustomerId)).thenReturn(null)

        val exception =
            assertThrows(KycLinkCreationException::class.java) {
                kycService.createKycLink(internalCustomerId, "John Doe", "john@example.com", "uri")
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
        verify(bridgeService, never()).createKycLink(any(), any(), any(), any())
        verify(customerIdentityService, never()).linkIdentity(any(), any(), any())
    }

    @Test
    fun `createKycLink should skip linking when bridge identity already exists`() {
        val internalCustomerId = "customer-123"
        val fullName = "John Doe"
        val email = "john@example.com"

        val customer = CustomerDynamoDbBean().apply { id = internalCustomerId }
        whenever(customerService.get(internalCustomerId)).thenReturn(customer)

        val existingIdentity =
            CustomerIdentityDynamoDbBean().apply {
                this.internalCustomerId = internalCustomerId
                provider = "bridge"
                externalId = "existing-bridge-id"
            }
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge"))
            .thenReturn(existingIdentity)

        val bridgeResponse =
            IndividualKycLinkResponse(null)
                .id("kyc_link_new")
                .kycLink("https://kyc.bridge.xyz/new")
                .kycStatus(KycStatus.NOT_STARTED)
                .tosLink("https://tos.bridge.xyz/new")
                .tosStatus(TosStatus.PENDING)
                .createdAt(OffsetDateTime.parse("2024-01-15T10:00:01Z"))

        whenever(bridgeService.createKycLink(fullName, email)).thenReturn(bridgeResponse)

        val result = kycService.createKycLink(internalCustomerId, fullName, email)

        assertEquals("kyc_link_new", result.id)
        assertEquals("https://kyc.bridge.xyz/new", result.kycLink)
        verify(bridgeService).createKycLink(fullName, email)
        verify(customerIdentityService, never()).linkIdentity(any(), any(), any())
    }

    @Test
    fun `createKycLink should wrap bridge API exception`() {
        val internalCustomerId = "customer-123"
        val fullName = "John Doe"
        val email = "john@example.com"

        val customer = CustomerDynamoDbBean().apply { id = internalCustomerId }
        whenever(customerService.get(internalCustomerId)).thenReturn(customer)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)
        whenever(bridgeService.createKycLink(fullName, email))
            .thenThrow(BridgeApiException("API error", 500))

        val exception =
            assertThrows(KycLinkCreationException::class.java) {
                kycService.createKycLink(internalCustomerId, fullName, email)
            }

        assertTrue(exception.message?.contains("Failed to create KYC link via Bridge API") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
        verify(customerIdentityService, never()).linkIdentity(any(), any(), any())
    }
}
