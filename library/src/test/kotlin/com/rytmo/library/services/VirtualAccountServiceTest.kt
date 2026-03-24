package com.rytmo.library.services

import com.rytmo.library.bridge.model.CryptoCurrency
import com.rytmo.library.bridge.model.Currency
import com.rytmo.library.bridge.model.OfframpChain
import com.rytmo.library.bridge.model.VirtualAccountDestination
import com.rytmo.library.bridge.model.VirtualAccountEvent
import com.rytmo.library.bridge.model.VirtualAccountEventSource
import com.rytmo.library.bridge.model.VirtualAccountHistory
import com.rytmo.library.bridge.model.VirtualAccountResponse
import com.rytmo.library.bridge.model.VirtualAccountSourceDepositInstructions
import com.rytmo.library.bridge.model.VirtualAccountSourceDepositInstructionsMx
import com.rytmo.library.bridge.model.VirtualAccountSourceDepositInstructionsUs
import com.rytmo.library.bridge.model.VirtualAccountSourcePaymentRails
import com.rytmo.library.bridge.model.VirtualAccounts
import com.rytmo.library.exceptions.VirtualAccountException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.virtualaccounts.CreateVirtualAccountRequest
import com.rytmo.models.wallets.CryptoWallet
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

class VirtualAccountServiceTest {
    private lateinit var bridgeService: BridgeService
    private lateinit var privyService: PrivyService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var virtualAccountService: VirtualAccountService

    @BeforeEach
    fun setUp() {
        bridgeService = mock()
        privyService = mock()
        customerIdentityService = mock()
        virtualAccountService =
            VirtualAccountService(bridgeService, privyService, customerIdentityService)
    }

    @Test
    fun `createByExternalId should resolve wallet and create account`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"
        val walletId = "wallet-789"
        val walletAddress = "0xabc123def456"

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

        whenever(privyService.getWallet(walletId)).thenReturn(CryptoWallet(walletAddress))

        val bridgeResponse =
            VirtualAccountResponse()
                .id("va_001")
                .sourceDepositInstructions(
                    VirtualAccountSourceDepositInstructions(
                        VirtualAccountSourceDepositInstructionsUs()
                            .paymentRail(VirtualAccountSourcePaymentRails.ACH_PUSH)
                            .currency(VirtualAccountSourceDepositInstructionsUs.CurrencyEnum.USD)
                            .bankName("Test Bank")
                            .bankAddress("123 Main St")
                            .bankRoutingNumber("111000025")
                            .bankAccountNumber("000123456789"),
                    ),
                )
                .destination(
                    VirtualAccountDestination()
                        .currency(CryptoCurrency.USDC)
                        .paymentRail(OfframpChain.BASE)
                        .address(walletAddress),
                )

        whenever(
            bridgeService.createVirtualAccount(
                customerId = bridgeCustomerId,
                sourceCurrency = "usd",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
                destinationAddress = walletAddress,
            ),
        )
            .thenReturn(bridgeResponse)

        val request =
            CreateVirtualAccountRequest(
                walletId = walletId,
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        val result = virtualAccountService.createByExternalId(externalId, request)

        assertEquals("va_001", result.id)
        assertNotNull(result.sourceDepositInstructions)
        assertEquals("ach_push", result.sourceDepositInstructions?.paymentRail)
        assertEquals("usd", result.sourceDepositInstructions?.currency)
        assertEquals("Test Bank", result.sourceDepositInstructions?.bankName)
        assertEquals("111000025", result.sourceDepositInstructions?.bankRoutingNumber)
        assertEquals("000123456789", result.sourceDepositInstructions?.bankAccountNumber)
        assertNotNull(result.destination)
        assertEquals("usdc", result.destination?.currency)
        assertEquals("base", result.destination?.paymentRail)
        assertEquals(walletAddress, result.destination?.address)

        verify(privyService).getWallet(walletId)
    }

    @Test
    fun `createByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.createByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
        verify(bridgeService, never()).createVirtualAccount(any(), any(), any(), any(), any())
    }

    @Test
    fun `createByExternalId should throw when bridge identity not found`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.createByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
        verify(bridgeService, never()).createVirtualAccount(any(), any(), any(), any(), any())
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
        whenever(privyService.getWallet(any())).thenReturn(CryptoWallet("0xabc"))
        whenever(bridgeService.createVirtualAccount(any(), any(), any(), any(), any()))
            .thenThrow(BridgeApiException("API error", 500))

        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.createByExternalId(externalId, request)
            }

        assertTrue(exception.message?.contains("Failed to create virtual account") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
    }

    @Test
    fun `createByExternalId should default bankName to STP when null for US deposit`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"
        val walletAddress = "0xabc123def456"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge"))
            .thenReturn(
                CustomerIdentityDynamoDbBean().apply {
                    this.internalCustomerId = internalCustomerId
                    provider = "bridge"
                    this.externalId = bridgeCustomerId
                },
            )
        whenever(privyService.getWallet(any())).thenReturn(CryptoWallet(walletAddress))

        val bridgeResponse =
            VirtualAccountResponse()
                .id("va_001")
                .sourceDepositInstructions(
                    VirtualAccountSourceDepositInstructions(
                        VirtualAccountSourceDepositInstructionsUs()
                            .paymentRail(VirtualAccountSourcePaymentRails.ACH_PUSH)
                            .currency(VirtualAccountSourceDepositInstructionsUs.CurrencyEnum.USD)
                            .bankRoutingNumber("111000025")
                            .bankAccountNumber("000123456789"),
                    ),
                )
        whenever(bridgeService.createVirtualAccount(any(), any(), any(), any(), any()))
            .thenReturn(bridgeResponse)

        val result =
            virtualAccountService.createByExternalId(
                externalId,
                CreateVirtualAccountRequest(
                    walletId = "wallet-789",
                    sourceCurrency = "usd",
                    sourcePaymentRail = "ach",
                    destinationCurrency = "usdc",
                    destinationPaymentRail = "base",
                ),
            )

        assertEquals("STP", result.sourceDepositInstructions?.bankName)
    }

    @Test
    fun `createByExternalId should default bankName to STP when null for MX deposit`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"
        val walletAddress = "0xabc123def456"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge"))
            .thenReturn(
                CustomerIdentityDynamoDbBean().apply {
                    this.internalCustomerId = internalCustomerId
                    provider = "bridge"
                    this.externalId = bridgeCustomerId
                },
            )
        whenever(privyService.getWallet(any())).thenReturn(CryptoWallet(walletAddress))

        val bridgeResponse =
            VirtualAccountResponse()
                .id("va_002")
                .sourceDepositInstructions(
                    VirtualAccountSourceDepositInstructions(
                        VirtualAccountSourceDepositInstructionsMx()
                            .clabe("646180111800000001")
                            .currency(VirtualAccountSourceDepositInstructionsMx.CurrencyEnum.MXN)
                            .accountHolderName("Test User"),
                    ),
                )
        whenever(bridgeService.createVirtualAccount(any(), any(), any(), any(), any()))
            .thenReturn(bridgeResponse)

        val result =
            virtualAccountService.createByExternalId(
                externalId,
                CreateVirtualAccountRequest(
                    walletId = "wallet-789",
                    sourceCurrency = "mxn",
                    sourcePaymentRail = "spei",
                    destinationCurrency = "usdc",
                    destinationPaymentRail = "base",
                ),
            )

        assertEquals("STP", result.sourceDepositInstructions?.bankName)
        assertEquals("646180111800000001", result.sourceDepositInstructions?.clabe)
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
            VirtualAccounts()
                .count(2)
                .addDataItem(
                    VirtualAccountResponse()
                        .id("va_001")
                        .destination(
                            VirtualAccountDestination()
                                .currency(CryptoCurrency.USDC)
                                .paymentRail(OfframpChain.BASE)
                                .address("0xabc"),
                        ),
                )
                .addDataItem(
                    VirtualAccountResponse()
                        .id("va_002")
                        .destination(
                            VirtualAccountDestination()
                                .currency(CryptoCurrency.USDC)
                                .paymentRail(OfframpChain.ETHEREUM)
                                .address("0xdef"),
                        ),
                )

        whenever(bridgeService.listVirtualAccounts(bridgeCustomerId)).thenReturn(bridgeResponse)

        val result = virtualAccountService.listByExternalId(externalId)

        assertEquals(2, result.size)
        assertEquals("va_001", result[0].id)
        assertEquals("base", result[0].destination?.paymentRail)
        assertEquals("va_002", result[1].id)
        assertEquals("ethereum", result[1].destination?.paymentRail)
    }

    @Test
    fun `listByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.listByExternalId(externalId)
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
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.listByExternalId(externalId)
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
    }

    @Test
    fun `getActivityByExternalId should return mapped activities`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"
        val bridgeCustomerId = "bridge-cust-456"
        val virtualAccountId = "va_789"

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
            VirtualAccountHistory()
                .addDataItem(
                    VirtualAccountEvent(OffsetDateTime.parse("2024-01-15T10:00:01Z"))
                        .id("activity_001")
                        .type(VirtualAccountEvent.TypeEnum.FUNDS_RECEIVED)
                        .virtualAccountId(virtualAccountId)
                        .amount("50.00")
                        .currency(Currency.USD)
                        .source(
                            VirtualAccountEventSource(
                                null,
                                "Jane Smith",
                                "021000021",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                            )
                                .paymentRail(VirtualAccountSourcePaymentRails.ACH_PUSH),
                        ),
                )

        whenever(bridgeService.getVirtualAccountActivity(bridgeCustomerId, virtualAccountId))
            .thenReturn(bridgeResponse)

        val result = virtualAccountService.getActivityByExternalId(externalId, virtualAccountId)

        assertEquals(1, result.size)
        assertEquals("activity_001", result[0].id)
        assertEquals("funds_received", result[0].type)
        assertEquals(virtualAccountId, result[0].virtualAccountId)
        assertEquals("50.00", result[0].amount)
        assertEquals("usd", result[0].currency)
        assertNotNull(result[0].source)
        assertEquals("ach_push", result[0].source?.paymentRail)
        assertEquals("Jane Smith", result[0].source?.senderName)
        assertEquals("2024-01-15T10:00:01Z", result[0].createdAt)
    }

    @Test
    fun `getActivityByExternalId should throw when customer not found`() {
        val externalId = "did:privy:unknown"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId)).thenReturn(null)

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.getActivityByExternalId(externalId, "va_123")
            }

        assertTrue(exception.message?.contains("Customer not found") == true)
    }

    @Test
    fun `getActivityByExternalId should throw when bridge identity not found`() {
        val externalId = "did:privy:user123"
        val internalCustomerId = "customer-123"

        whenever(customerIdentityService.getInternalCustomerIdByExternalId(externalId))
            .thenReturn(internalCustomerId)
        whenever(customerIdentityService.getIdentity(internalCustomerId, "bridge")).thenReturn(null)

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.getActivityByExternalId(externalId, "va_123")
            }

        assertTrue(exception.message?.contains("Bridge identity not found") == true)
    }

    @Test
    fun `getActivityByExternalId should wrap bridge API exception`() {
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
        whenever(bridgeService.getVirtualAccountActivity(any(), any()))
            .thenThrow(BridgeApiException("API error", 500))

        val exception =
            assertThrows(VirtualAccountException::class.java) {
                virtualAccountService.getActivityByExternalId(externalId, "va_123")
            }

        assertTrue(exception.message?.contains("Failed to get virtual account activity") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is BridgeApiException)
    }
}
