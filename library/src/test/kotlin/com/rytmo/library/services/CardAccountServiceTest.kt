package com.rytmo.library.services

import com.rytmo.library.bridge.model.ArrayOfAllCardAccounts
import com.rytmo.library.bridge.model.CardAccount
import com.rytmo.library.bridge.model.CardAccountFundingInstructions
import com.rytmo.library.bridge.model.CardBalance
import com.rytmo.library.bridge.model.CardBalances
import com.rytmo.library.bridge.model.CardTransaction
import com.rytmo.library.bridge.model.CardholderName
import com.rytmo.library.bridge.model.CardsCryptoCurrency
import com.rytmo.library.bridge.model.ListOfCardTransactions
import com.rytmo.library.bridge.model.OfframpChainForCards
import com.rytmo.library.exceptions.CardAccountException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.cardaccounts.CreateCardAccountRequest
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

class CardAccountServiceTest {
    private lateinit var bridgeService: BridgeService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var cardAccountService: CardAccountService

    @BeforeEach
    fun setUp() {
        bridgeService = mock()
        customerIdentityService = mock()
        cardAccountService = CardAccountService(bridgeService, customerIdentityService)
    }

    @Test
    fun `provisionByExternalId should create card account`() {
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
            CardAccount()
                .id("card_001")
                .customerId(bridgeCustomerId)
                .status(CardAccount.StatusEnum.ACTIVE)
                .cardholderName(CardholderName().firstName("John").lastName("Doe"))
                .balances(
                    CardBalances()
                        .available(CardBalance().amount("100.00").currency(CardsCryptoCurrency.USDC))
                        .hold(CardBalance().amount("0.00").currency(CardsCryptoCurrency.USDC)),
                )
                .fundingInstructions(
                    CardAccountFundingInstructions()
                        .currency(CardsCryptoCurrency.USDC)
                        .chain(OfframpChainForCards.BASE)
                        .address("0xfunding123"),
                )

        whenever(bridgeService.provisionCardAccount(any(), any(), any(), any()))
            .thenReturn(bridgeResponse)

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        val result = cardAccountService.provisionByExternalId(externalId, request)

        assertEquals("card_001", result.id)
        assertEquals("active", result.status)
        assertEquals("John", result.cardholderName?.firstName)
        assertEquals("Doe", result.cardholderName?.lastName)
        assertEquals("100.00", result.balances?.available)
        assertEquals("0.00", result.balances?.hold)
        assertEquals("usdc", result.fundingInstructions?.currency)
        assertEquals("base", result.fundingInstructions?.chain)
        assertEquals("0xfunding123", result.fundingInstructions?.address)
    }

    @Test
    fun `provisionByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.provisionByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
        verify(bridgeService, never()).provisionCardAccount(any(), any(), any(), any())
    }

    @Test
    fun `provisionByExternalId should throw when bridge identity not found`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.provisionByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
        verify(bridgeService, never()).provisionCardAccount(any(), any(), any(), any())
    }

    @Test
    fun `provisionByExternalId should wrap bridge API exception`() {
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
        whenever(bridgeService.provisionCardAccount(any(), any(), any(), any()))
            .thenThrow(BridgeApiException("API error", 500))

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.provisionByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Failed to provision card account") == true)
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
            ArrayOfAllCardAccounts()
                .count(2)
                .addDataItem(
                    CardAccount()
                        .id("card_001")
                        .customerId(bridgeCustomerId)
                        .status(CardAccount.StatusEnum.ACTIVE)
                        .balances(
                            CardBalances()
                                .available(
                                    CardBalance().amount("50.00").currency(CardsCryptoCurrency.USDC),
                                )
                                .hold(CardBalance().amount("0.00").currency(CardsCryptoCurrency.USDC)),
                        ),
                )
                .addDataItem(
                    CardAccount()
                        .id("card_002")
                        .customerId(bridgeCustomerId)
                        .status(CardAccount.StatusEnum.PENDING)
                        .balances(
                            CardBalances()
                                .available(
                                    CardBalance().amount("0.00").currency(CardsCryptoCurrency.USDC),
                                )
                                .hold(CardBalance().amount("0.00").currency(CardsCryptoCurrency.USDC)),
                        ),
                )

        whenever(bridgeService.listCardAccounts(bridgeCustomerId)).thenReturn(bridgeResponse)

        val result = cardAccountService.listByExternalId(externalId)

        assertEquals(2, result.size)
        assertEquals("card_001", result[0].id)
        assertEquals("active", result[0].status)
        assertEquals("card_002", result[1].id)
        assertEquals("pending", result[1].status)
    }

    @Test
    fun `listByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.listByExternalId(externalId)
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
    }

    @Test
    fun `getTransactionsByExternalId should return mapped transactions`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"
        val cardAccountId = "card_001"

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
            ListOfCardTransactions()
                .page(1)
                .count(1)
                .totalPages(1)
                .totalCount(1)
                .addDataItem(
                    CardTransaction()
                        .id("txn_001")
                        .cardAccountId(cardAccountId)
                        .category(CardTransaction.CategoryEnum.PURCHASE)
                        .amount("25.00")
                        .currency(CardTransaction.CurrencyEnum.USD)
                        .merchantName("Coffee Shop")
                        .merchantLocation("New York, NY")
                        .description("Coffee purchase")
                        .postedAt("2024-01-15T10:00:00Z")
                        .status(CardTransaction.StatusEnum.POSTED),
                )

        whenever(bridgeService.getCardTransactions(bridgeCustomerId, cardAccountId))
            .thenReturn(bridgeResponse)

        val result = cardAccountService.getTransactionsByExternalId(externalId, cardAccountId)

        assertEquals(1, result.page)
        assertEquals(1, result.count)
        assertEquals(1, result.totalPages)
        assertEquals(1, result.totalCount)
        assertEquals(1, result.data.size)
        assertEquals("txn_001", result.data[0].id)
        assertEquals(cardAccountId, result.data[0].cardAccountId)
        assertEquals("purchase", result.data[0].category)
        assertEquals("25.00", result.data[0].amount)
        assertEquals("usd", result.data[0].currency)
        assertEquals("Coffee Shop", result.data[0].merchantName)
        assertEquals("New York, NY", result.data[0].merchantLocation)
        assertEquals("Coffee purchase", result.data[0].description)
        assertEquals("posted", result.data[0].status)
    }

    @Test
    fun `getTransactionsByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.getTransactionsByExternalId(externalId, "card_001")
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
    }

    @Test
    fun `getTransactionsByExternalId should throw when bridge identity not found`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.getTransactionsByExternalId(externalId, "card_001")
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
    }

    @Test
    fun `getTransactionsByExternalId should wrap bridge API exception`() {
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
        whenever(
            bridgeService.getCardTransactions(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenThrow(BridgeApiException("API error", 500))

        val exception =
            assertThrows(CardAccountException::class.java) {
                cardAccountService.getTransactionsByExternalId(externalId, "card_001")
            }

        assertTrue(exception.message?.contains("Failed to get card transactions") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
    }
}
