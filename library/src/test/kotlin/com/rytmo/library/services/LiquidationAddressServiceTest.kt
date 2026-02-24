package com.rytmo.library.services

import com.rytmo.library.bridge.model.LiquidationAddress
import com.rytmo.library.bridge.model.LiquidationAddressSourceChain
import com.rytmo.library.bridge.model.LiquidationAddressSourceCurrency
import com.rytmo.library.bridge.model.LiquidationAddresses
import com.rytmo.library.exceptions.LiquidationAddressException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
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

class LiquidationAddressServiceTest {
    private lateinit var bridgeService: BridgeService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var liquidationAddressService: LiquidationAddressService

    private val returnAddress = "0xtest-return-address"

    @BeforeEach
    fun setUp() {
        bridgeService = mock()
        customerIdentityService = mock()
        liquidationAddressService =
            LiquidationAddressService(bridgeService, returnAddress, customerIdentityService)
    }

    @Test
    fun `handleAddressCreatedEvent should create liquidation address with correct params`() {
        val eventObject =
            mapOf(
                "customer_id" to "bridge-cust-456",
                "external_account_id" to "ext_acct_789",
            )

        liquidationAddressService.handleAddressCreatedEvent(eventObject)

        verify(bridgeService)
            .createLiquidationAddress(
                customerId = "bridge-cust-456",
                currency = LiquidationAddressService.CURRENCY,
                chain = LiquidationAddressService.CHAIN,
                externalAccountId = "ext_acct_789",
                destinationPaymentRail = LiquidationAddressService.DESTINATION_PAYMENT_RAIL,
                destinationCurrency = LiquidationAddressService.DESTINATION_CURRENCY,
                returnAddress = returnAddress,
            )
    }

    @Test
    fun `handleAddressCreatedEvent should throw when customer_id missing from event`() {
        val eventObject = mapOf("external_account_id" to "ext_acct_789")

        val exception =
            assertThrows(LiquidationAddressException::class.java) {
                liquidationAddressService.handleAddressCreatedEvent(eventObject)
            }

        assertTrue(exception.message?.contains("Missing customer_id") == true)
        verify(bridgeService, never())
            .createLiquidationAddress(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `handleAddressCreatedEvent should throw when external_account_id missing from event`() {
        val eventObject = mapOf("customer_id" to "bridge-cust-456")

        val exception =
            assertThrows(LiquidationAddressException::class.java) {
                liquidationAddressService.handleAddressCreatedEvent(eventObject)
            }

        assertTrue(exception.message?.contains("Missing external_account_id") == true)
        verify(bridgeService, never())
            .createLiquidationAddress(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `handleAddressCreatedEvent should wrap bridge API exception`() {
        val eventObject =
            mapOf(
                "customer_id" to "bridge-cust-456",
                "external_account_id" to "ext_acct_789",
            )

        whenever(
            bridgeService.createLiquidationAddress(any(), any(), any(), any(), any(), any(), any()),
        )
            .thenThrow(BridgeApiException("API error", 500))

        val exception =
            assertThrows(LiquidationAddressException::class.java) {
                liquidationAddressService.handleAddressCreatedEvent(eventObject)
            }

        assertTrue(exception.message?.contains("Failed to create liquidation address") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
    }

    @Test
    fun `listByExternalId should return mapped liquidation addresses`() {
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
            LiquidationAddresses()
                .addDataItem(
                    LiquidationAddress(
                        "0xcrypto-address",
                        OffsetDateTime.parse("2024-01-15T10:00:01Z"),
                        OffsetDateTime.parse("2024-01-16T10:00:01Z"),
                    )
                        .id("liq_addr_001")
                        .currency(LiquidationAddressSourceCurrency.USDC)
                        .chain(LiquidationAddressSourceChain.BASE)
                        .externalAccountId("ext_acct_789"),
                )

        whenever(bridgeService.listLiquidationAddresses(bridgeCustomerId)).thenReturn(bridgeResponse)

        val result = liquidationAddressService.listByExternalId(externalId)

        assertEquals(1, result.size)
        assertEquals("liq_addr_001", result[0].id)
        assertEquals("usdc", result[0].currency)
        assertEquals("base", result[0].chain)
        assertEquals("ext_acct_789", result[0].externalAccountId)
        assertEquals("0xcrypto-address", result[0].address)
        assertEquals("2024-01-15T10:00:01Z", result[0].createdAt)
        assertEquals("2024-01-16T10:00:01Z", result[0].updatedAt)
    }

    @Test
    fun `listByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(LiquidationAddressException::class.java) {
                liquidationAddressService.listByExternalId(externalId)
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
            assertThrows(LiquidationAddressException::class.java) {
                liquidationAddressService.listByExternalId(externalId)
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
    }

    @Test
    fun `listByExternalId should wrap bridge API exception`() {
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
        whenever(bridgeService.listLiquidationAddresses(bridgeCustomerId))
            .thenThrow(BridgeApiException("API error", 500))

        val exception =
            assertThrows(LiquidationAddressException::class.java) {
                liquidationAddressService.listByExternalId(externalId)
            }

        assertTrue(exception.message?.contains("Failed to list liquidation addresses") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
    }
}
