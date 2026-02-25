package com.rytmo.library.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.rytmo.library.bridge.api.CustomersApi
import com.rytmo.library.bridge.api.ExternalAccountsApi
import com.rytmo.library.bridge.api.KycLinksApi
import com.rytmo.library.bridge.api.LiquidationAddressesApi
import com.rytmo.library.bridge.api.VirtualAccountsApi
import com.rytmo.library.bridge.invoker.ApiClient
import com.rytmo.library.bridge.invoker.ApiException
import com.rytmo.library.bridge.model.CreateIndividualCustomerPayload
import com.rytmo.library.bridge.model.CreateKycLinks
import com.rytmo.library.bridge.model.CreateLiquidationAddress
import com.rytmo.library.bridge.model.CreateLiquidationAddressResponse
import com.rytmo.library.bridge.model.CreateVirtualAccount
import com.rytmo.library.bridge.model.CryptoCurrency
import com.rytmo.library.bridge.model.Currency
import com.rytmo.library.bridge.model.Customer
import com.rytmo.library.bridge.model.CustomerStatus
import com.rytmo.library.bridge.model.CustomersPostRequest
import com.rytmo.library.bridge.model.EuroInclusiveCurrency
import com.rytmo.library.bridge.model.ExternalAccount1
import com.rytmo.library.bridge.model.ExternalAccountResponse
import com.rytmo.library.bridge.model.IndividualKycLinkResponse
import com.rytmo.library.bridge.model.KycStatus
import com.rytmo.library.bridge.model.LiquidationAddress
import com.rytmo.library.bridge.model.LiquidationAddressSourceChain
import com.rytmo.library.bridge.model.LiquidationAddressSourceCurrency
import com.rytmo.library.bridge.model.LiquidationAddresses
import com.rytmo.library.bridge.model.OfframpChain
import com.rytmo.library.bridge.model.SepaSwiftInclusivePaymentRail
import com.rytmo.library.bridge.model.TosStatus
import com.rytmo.library.bridge.model.VirtualAccountDestination
import com.rytmo.library.bridge.model.VirtualAccountEvent
import com.rytmo.library.bridge.model.VirtualAccountEventSource
import com.rytmo.library.bridge.model.VirtualAccountHistory
import com.rytmo.library.bridge.model.VirtualAccountResponse
import com.rytmo.library.bridge.model.VirtualAccountSourcePaymentRails
import com.rytmo.library.bridge.model.VirtualAccounts
import com.rytmo.models.externalaccounts.ExternalAccountAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpResponse
import java.time.OffsetDateTime

class BridgeServiceTest {
    private lateinit var kycLinksApi: KycLinksApi
    private lateinit var virtualAccountsApi: VirtualAccountsApi
    private lateinit var externalAccountsApi: ExternalAccountsApi
    private lateinit var liquidationAddressesApi: LiquidationAddressesApi
    private lateinit var customersApi: CustomersApi
    private lateinit var apiClient: ApiClient
    private lateinit var bridgeService: BridgeService

    private val objectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

    @BeforeEach
    fun setUp() {
        kycLinksApi = mock()
        virtualAccountsApi = mock()
        externalAccountsApi = mock()
        liquidationAddressesApi = mock()
        customersApi = mock()
        apiClient = mock()
        bridgeService =
            BridgeService(
                apiClient,
                kycLinksApi,
                virtualAccountsApi,
                externalAccountsApi,
                liquidationAddressesApi,
                customersApi,
            )
    }

    @Test
    fun `createKycLink should call API with correct parameters`() {
        val expectedResponse =
            IndividualKycLinkResponse()
                .id("kyc_link_123")
                .fullName("John Doe")
                .email("johndoe@example.com")
                .kycLink("https://kyc.bridge.xyz/abc123")
                .kycStatus(KycStatus.NOT_STARTED)
                .tosLink("https://tos.bridge.xyz/abc123")
                .tosStatus(TosStatus.PENDING)
                .createdAt(OffsetDateTime.parse("2024-01-15T10:00:01Z"))

        val requestCaptor = argumentCaptor<CreateKycLinks>()
        whenever(kycLinksApi.kycLinksPost(any(), requestCaptor.capture())).thenReturn(expectedResponse)

        val result =
            bridgeService.createKycLink(
                fullName = "John Doe",
                email = "johndoe@example.com",
                type = "individual",
                redirectUri = "https://example.com/redirect",
            )

        assertEquals(expectedResponse.id, result.id)
        assertEquals(expectedResponse.fullName, result.fullName)
        assertEquals(expectedResponse.email, result.email)
        assertEquals(expectedResponse.kycLink, result.kycLink)
        assertEquals(expectedResponse.kycStatus, result.kycStatus)

        val capturedRequest = requestCaptor.firstValue
        assertEquals("John Doe", capturedRequest.fullName)
        assertEquals("johndoe@example.com", capturedRequest.email)
        assertEquals("individual", capturedRequest.type?.value)
    }

    @Test
    fun `createKycLink should generate idempotency key`() {
        val idempotencyKeyCaptor = argumentCaptor<String>()
        whenever(kycLinksApi.kycLinksPost(idempotencyKeyCaptor.capture(), any()))
            .thenReturn(IndividualKycLinkResponse())

        bridgeService.createKycLink(
            fullName = "John Doe",
            email = "johndoe@example.com",
            redirectUri = "https://example.com/redirect",
        )

        val idempotencyKey = idempotencyKeyCaptor.firstValue
        assertNotNull(idempotencyKey)
        assertEquals(36, idempotencyKey.length)
    }

    @Test
    fun `createKycLink should use default type individual`() {
        val requestCaptor = argumentCaptor<CreateKycLinks>()
        whenever(kycLinksApi.kycLinksPost(any(), requestCaptor.capture()))
            .thenReturn(IndividualKycLinkResponse())

        bridgeService.createKycLink(
            fullName = "John Doe",
            email = "johndoe@example.com",
            redirectUri = "https://example.com/redirect",
        )

        assertEquals("individual", requestCaptor.firstValue.type?.value)
    }

    @Test
    fun `createKycLink should wrap API exception`() {
        whenever(kycLinksApi.kycLinksPost(any(), any())).thenThrow(ApiException(400, "Bad Request"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createKycLink(
                    fullName = "John Doe",
                    email = "johndoe@example.com",
                    redirectUri = "https://example.com/redirect",
                )
            }

        assertEquals(400, exception.statusCode)
    }

    @Test
    fun `createKycLink should return existing kyc link on duplicate_record error`() {
        val responseBody =
            """
            {
                "existing_kyc_link": {
                    "id": "existing-kyc-123",
                    "full_name": "John Doe",
                    "email": "johndoe@example.com",
                    "kyc_link": "https://kyc.bridge.xyz/existing",
                    "kyc_status": "not_started",
                    "tos_link": "https://tos.bridge.xyz/existing",
                    "tos_status": "pending",
                    "created_at": "2026-02-20T20:08:30.769Z"
                },
                "code": "duplicate_record",
                "message": "A kyc link has already been created for this email."
            }
            """
                .trimIndent()

        val headers = HttpHeaders.of(emptyMap(), { _, _ -> true })
        whenever(kycLinksApi.kycLinksPost(any(), any()))
            .thenThrow(ApiException(400, "duplicate", headers, responseBody))

        val result =
            bridgeService.createKycLink(
                fullName = "John Doe",
                email = "johndoe@example.com",
                redirectUri = "https://example.com/redirect",
            )

        assertEquals("existing-kyc-123", result.id)
        assertEquals(KycStatus.NOT_STARTED, result.kycStatus)
        assertEquals(TosStatus.PENDING, result.tosStatus)
        assertEquals("https://kyc.bridge.xyz/existing", result.kycLink)
    }

    @Test
    fun `createKycLink should throw BridgeApiException for non-duplicate 400 error`() {
        val responseBody = """{"code": "validation_error", "message": "Invalid email"}"""
        val headers = HttpHeaders.of(emptyMap(), { _, _ -> true })
        whenever(kycLinksApi.kycLinksPost(any(), any()))
            .thenThrow(ApiException(400, "validation error", headers, responseBody))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createKycLink(
                    fullName = "John Doe",
                    email = "bad-email",
                    redirectUri = "https://example.com/redirect",
                )
            }

        assertEquals(400, exception.statusCode)
    }

    @Test
    fun `createVirtualAccount should call API with correct parameters`() {
        val expectedResponse =
            VirtualAccountResponse()
                .id("va_123")
                .destination(
                    VirtualAccountDestination()
                        .currency(CryptoCurrency.USDC)
                        .paymentRail(OfframpChain.BASE)
                        .address("0xabc123"),
                )

        val requestCaptor = argumentCaptor<CreateVirtualAccount>()
        whenever(
            virtualAccountsApi.customersCustomerIDVirtualAccountsPost(
                any(),
                any(),
                requestCaptor.capture(),
            ),
        )
            .thenReturn(expectedResponse)

        val result =
            bridgeService.createVirtualAccount(
                customerId = "cust_123",
                sourceCurrency = "usd",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
                destinationAddress = "0xabc123",
            )

        assertEquals("va_123", result.id)
        assertEquals(CryptoCurrency.USDC, result.destination?.currency)

        val capturedRequest = requestCaptor.firstValue
        assertEquals("usd", capturedRequest.source?.currency?.value)
        assertEquals("usdc", capturedRequest.destination?.currency?.value)
        assertEquals("base", capturedRequest.destination?.paymentRail?.value)
        assertEquals("0xabc123", capturedRequest.destination?.address)
    }

    @Test
    fun `createVirtualAccount should wrap API exception`() {
        whenever(virtualAccountsApi.customersCustomerIDVirtualAccountsPost(any(), any(), any()))
            .thenThrow(ApiException(500, "Internal Server Error"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createVirtualAccount(
                    customerId = "cust_123",
                    sourceCurrency = "usd",
                    destinationCurrency = "usdc",
                    destinationPaymentRail = "base",
                    destinationAddress = "0xabc123",
                )
            }

        assertEquals(500, exception.statusCode)
    }

    @Test
    fun `listVirtualAccounts should return response`() {
        val expectedResponse =
            VirtualAccounts().count(1).addDataItem(VirtualAccountResponse().id("va_123"))

        whenever(
            virtualAccountsApi.customersCustomerIDVirtualAccountsGet(
                "cust_123",
                null,
                null,
                null,
                null,
            ),
        )
            .thenReturn(expectedResponse)

        val result = bridgeService.listVirtualAccounts("cust_123")

        assertEquals(1, result.count)
        assertEquals(1, result.data.size)
        assertEquals("va_123", result.data[0].id)
    }

    @Test
    fun `listVirtualAccounts should wrap API exception`() {
        whenever(
            virtualAccountsApi.customersCustomerIDVirtualAccountsGet(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenThrow(ApiException(404, "Not Found"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.listVirtualAccounts("cust_123")
            }

        assertEquals(404, exception.statusCode)
    }

    @Test
    fun `getVirtualAccountActivity should return response`() {
        val expectedResponse =
            VirtualAccountHistory()
                .addDataItem(
                    VirtualAccountEvent()
                        .id("activity_123")
                        .type(VirtualAccountEvent.TypeEnum.FUNDS_RECEIVED)
                        .amount("100.00")
                        .currency(Currency.USD)
                        .source(
                            VirtualAccountEventSource(
                                null,
                                "John Doe",
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
                                null,
                            )
                                .paymentRail(VirtualAccountSourcePaymentRails.ACH_PUSH),
                        ),
                )

        whenever(
            virtualAccountsApi.customersCustomerIDVirtualAccountsVirtualAccountIDHistoryGet(
                "cust_123",
                "va_456",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            ),
        )
            .thenReturn(expectedResponse)

        val result = bridgeService.getVirtualAccountActivity("cust_123", "va_456")

        assertEquals(1, result.data.size)
        assertEquals("activity_123", result.data[0].id)
        assertEquals(VirtualAccountEvent.TypeEnum.FUNDS_RECEIVED, result.data[0].type)
        assertEquals(VirtualAccountSourcePaymentRails.ACH_PUSH, result.data[0].source?.paymentRail)
    }

    @Test
    fun `getVirtualAccountActivity should wrap API exception`() {
        whenever(
            virtualAccountsApi.customersCustomerIDVirtualAccountsVirtualAccountIDHistoryGet(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenThrow(ApiException(503, "Service Unavailable"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.getVirtualAccountActivity("cust_123", "va_456")
            }

        assertEquals(503, exception.statusCode)
    }

    @Test
    fun `createExternalAccount should return response on success`() {
        val responseJson =
            """{"id":"ext_acct_123","account_owner_name":"Jane Doe","bank_name":"Test Bank","last_4":"6789","active":true}"""

        val mockHttpClient = mock<HttpClient>()
        val mockResponse = mock<HttpResponse<InputStream>>()

        whenever(apiClient.httpClient).thenReturn(mockHttpClient)
        whenever(apiClient.objectMapper).thenReturn(objectMapper)
        whenever(apiClient.baseUri).thenReturn("https://api.bridge.xyz/v0")
        whenever(apiClient.requestInterceptor).thenReturn(null)

        @Suppress("UNCHECKED_CAST")
        doReturn(mockResponse as HttpResponse<Any>)
            .whenever(mockHttpClient)
            .send(any(), any<HttpResponse.BodyHandler<InputStream>>())
        whenever(mockResponse.statusCode()).thenReturn(200)
        whenever(mockResponse.body()).thenReturn(ByteArrayInputStream(responseJson.toByteArray()))

        val result =
            bridgeService.createExternalAccount(
                customerId = "cust_123",
                accountType = "us",
                accountOwnerName = "Jane Doe",
                routingNumber = "987654321",
                accountNumber = "123456789",
                address =
                ExternalAccountAddress(
                    streetLine1 = "123 Main St",
                    city = "New York",
                    country = "USA",
                ),
            )

        assertEquals("ext_acct_123", result.id)
        assertEquals("Jane Doe", result.accountOwnerName)
        assertEquals("Test Bank", result.bankName)
        assertEquals("6789", result.last4)
    }

    @Test
    fun `createExternalAccount should throw BridgeApiException on non-2xx response`() {
        val mockHttpClient = mock<HttpClient>()
        val mockResponse = mock<HttpResponse<InputStream>>()

        whenever(apiClient.httpClient).thenReturn(mockHttpClient)
        whenever(apiClient.objectMapper).thenReturn(objectMapper)
        whenever(apiClient.baseUri).thenReturn("https://api.bridge.xyz/v0")
        whenever(apiClient.requestInterceptor).thenReturn(null)

        @Suppress("UNCHECKED_CAST")
        doReturn(mockResponse as HttpResponse<Any>)
            .whenever(mockHttpClient)
            .send(any(), any<HttpResponse.BodyHandler<InputStream>>())
        whenever(mockResponse.statusCode()).thenReturn(422)
        whenever(mockResponse.body()).thenReturn(ByteArrayInputStream("{}".toByteArray()))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createExternalAccount(
                    customerId = "cust_123",
                    accountType = "us",
                    accountOwnerName = "Jane Doe",
                    routingNumber = null,
                    accountNumber = "123456789",
                    address =
                    ExternalAccountAddress(
                        streetLine1 = "123 Main St",
                        city = "New York",
                        country = "USA",
                    ),
                )
            }

        assertEquals(422, exception.statusCode)
    }

    @Test
    fun `listExternalAccounts should return response`() {
        val expectedResponse =
            ExternalAccount1()
                .addDataItem(
                    ExternalAccountResponse(null, "6789", null, null, null, null)
                        .id("ext_acct_001")
                        .accountOwnerName("Jane Doe")
                        .bankName("Test Bank"),
                )

        whenever(
            externalAccountsApi.customersCustomerIDExternalAccountsGet(
                "cust_123",
                null,
                null,
                null,
            ),
        )
            .thenReturn(expectedResponse)

        val result = bridgeService.listExternalAccounts("cust_123")

        assertEquals(1, result.data.size)
        assertEquals("ext_acct_001", result.data[0].id)
        assertEquals("Jane Doe", result.data[0].accountOwnerName)
    }

    @Test
    fun `listExternalAccounts should wrap API exception`() {
        whenever(
            externalAccountsApi.customersCustomerIDExternalAccountsGet(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenThrow(ApiException(404, "Not Found"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.listExternalAccounts("cust_123")
            }

        assertEquals(404, exception.statusCode)
    }

    @Test
    fun `listLiquidationAddresses should return response`() {
        val expectedResponse =
            LiquidationAddresses()
                .addDataItem(
                    LiquidationAddress("0xcrypto-address", null, null)
                        .id("liq_addr_001")
                        .currency(LiquidationAddressSourceCurrency.USDC)
                        .chain(LiquidationAddressSourceChain.BASE)
                        .externalAccountId("ext_acct_123"),
                )

        whenever(
            liquidationAddressesApi.customersCustomerIDLiquidationAddressesGet(
                "cust_123",
                null,
                null,
                null,
            ),
        )
            .thenReturn(expectedResponse)

        val result = bridgeService.listLiquidationAddresses("cust_123")

        assertEquals(1, result.data.size)
        assertEquals("liq_addr_001", result.data[0].id)
        assertEquals(LiquidationAddressSourceCurrency.USDC, result.data[0].currency)
        assertEquals(LiquidationAddressSourceChain.BASE, result.data[0].chain)
        assertEquals("0xcrypto-address", result.data[0].address)
    }

    @Test
    fun `listLiquidationAddresses should wrap API exception`() {
        whenever(
            liquidationAddressesApi.customersCustomerIDLiquidationAddressesGet(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenThrow(ApiException(404, "Not Found"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.listLiquidationAddresses("cust_123")
            }

        assertEquals(404, exception.statusCode)
    }

    @Test
    fun `createLiquidationAddress should call API with correct parameters`() {
        val expectedResponse =
            CreateLiquidationAddressResponse()
                .currency(LiquidationAddressSourceCurrency.USDC)
                .chain(LiquidationAddressSourceChain.BASE)
                .externalAccountId("ext_acct_123")
                .destinationPaymentRail(SepaSwiftInclusivePaymentRail.ACH)
                .destinationCurrency(EuroInclusiveCurrency.USD)
                .returnAddress("0xreturn-address")

        val requestCaptor = argumentCaptor<CreateLiquidationAddress>()
        whenever(
            liquidationAddressesApi.customersCustomerIDLiquidationAddressesPost(
                any(),
                any(),
                requestCaptor.capture(),
            ),
        )
            .thenReturn(expectedResponse)

        val result =
            bridgeService.createLiquidationAddress(
                customerId = "bridge-cust-123",
                currency = "usdc",
                chain = "base",
                externalAccountId = "ext_acct_123",
                destinationPaymentRail = "ach",
                destinationCurrency = "usd",
                returnAddress = "0xreturn-address",
            )

        assertEquals(LiquidationAddressSourceCurrency.USDC, result.currency)
        assertEquals(LiquidationAddressSourceChain.BASE, result.chain)
        assertEquals("ext_acct_123", result.externalAccountId)

        val capturedRequest = requestCaptor.firstValue
        assertEquals("usdc", capturedRequest.currency?.value)
        assertEquals("base", capturedRequest.chain?.value)
        assertEquals("ext_acct_123", capturedRequest.externalAccountId)
        assertEquals("ach", capturedRequest.destinationPaymentRail?.value)
        assertEquals("usd", capturedRequest.destinationCurrency?.value)
        assertEquals("0xreturn-address", capturedRequest.returnAddress)
    }

    @Test
    fun `createLiquidationAddress should wrap API exception`() {
        whenever(
            liquidationAddressesApi.customersCustomerIDLiquidationAddressesPost(
                any(),
                any(),
                any(),
            ),
        )
            .thenThrow(ApiException(422, "Unprocessable Entity"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createLiquidationAddress(
                    customerId = "bridge-cust-123",
                    currency = "usdc",
                    chain = "base",
                    externalAccountId = "ext_acct_123",
                    destinationPaymentRail = "ach",
                    destinationCurrency = "usd",
                    returnAddress = "0xreturn-address",
                )
            }

        assertEquals(422, exception.statusCode)
    }

    @Test
    fun `createCustomer should call API with correct parameters`() {
        val expectedResponse =
            Customer()
                .id("bridge-cust-123")
                .status(CustomerStatus.ACTIVE)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")

        val requestCaptor = argumentCaptor<CustomersPostRequest>()
        whenever(customersApi.customersPost(any(), requestCaptor.capture()))
            .thenReturn(expectedResponse)

        val result =
            bridgeService.createCustomer(
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
            )

        assertEquals("bridge-cust-123", result.id)
        assertEquals(CustomerStatus.ACTIVE, result.status)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("john@example.com", result.email)

        val capturedPayload = requestCaptor.firstValue.actualInstance as CreateIndividualCustomerPayload
        assertEquals("John", capturedPayload.firstName)
        assertEquals("Doe", capturedPayload.lastName)
        assertEquals("john@example.com", capturedPayload.email)
    }

    @Test
    fun `createCustomer should generate idempotency key`() {
        val idempotencyKeyCaptor = argumentCaptor<String>()
        whenever(customersApi.customersPost(idempotencyKeyCaptor.capture(), any()))
            .thenReturn(Customer().id("bridge-cust-123"))

        bridgeService.createCustomer(firstName = "John", lastName = "Doe", email = "john@example.com")

        val idempotencyKey = idempotencyKeyCaptor.firstValue
        assertNotNull(idempotencyKey)
        assertEquals(36, idempotencyKey.length)
    }

    @Test
    fun `createCustomer should wrap API exception`() {
        whenever(customersApi.customersPost(any(), any()))
            .thenThrow(ApiException(422, "Unprocessable Entity"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createCustomer(
                    firstName = "John",
                    lastName = "Doe",
                    email = "john@example.com",
                )
            }

        assertEquals(422, exception.statusCode)
    }
}
