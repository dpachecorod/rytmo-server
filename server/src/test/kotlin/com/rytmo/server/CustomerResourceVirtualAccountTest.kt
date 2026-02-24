package com.rytmo.server

import com.rytmo.library.exceptions.VirtualAccountException
import com.rytmo.library.services.VirtualAccountService
import com.rytmo.models.virtualaccounts.CreateVirtualAccountRequest
import com.rytmo.models.virtualaccounts.SourceDepositInstructions
import com.rytmo.models.virtualaccounts.VirtualAccountActivity
import com.rytmo.models.virtualaccounts.VirtualAccountActivitySource
import com.rytmo.models.virtualaccounts.VirtualAccountDestination
import com.rytmo.models.virtualaccounts.VirtualAccountResponse
import com.rytmo.server.test.PrivyTestProfile
import com.rytmo.server.utils.AccessTokenUtil
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.MockitoConfig
import io.restassured.RestAssured
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
class CustomerResourceVirtualAccountTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var virtualAccountService: VirtualAccountService

    @Test
    fun `createVirtualAccount should return 201 with valid request`() {
        val virtualAccountResponse =
            VirtualAccountResponse(
                id = "va_001",
                status = null,
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
                sourceDepositInstructions =
                SourceDepositInstructions(
                    paymentRail = "ach",
                    currency = "usd",
                    bankName = "Test Bank",
                    bankAddress = "123 Main St",
                    bankRoutingNumber = "111000025",
                    bankAccountNumber = "000123456789",
                    clabe = null,
                    bankCode = null,
                    beneficiaryName = null,
                    depositMessage = null,
                ),
                destination =
                VirtualAccountDestination(
                    currency = "usdc",
                    paymentRail = "base",
                    address = "0xabc123",
                ),
            )

        whenever(virtualAccountService.createByExternalId(any(), any()))
            .thenReturn(virtualAccountResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/customers/me/virtual-accounts")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .`as`(VirtualAccountResponse::class.java)

        assertEquals("va_001", response.id)
        assertEquals("ach", response.sourceDepositInstructions?.paymentRail)
        assertEquals("usdc", response.destination?.currency)
        assertEquals("0xabc123", response.destination?.address)
    }

    @Test
    fun `createVirtualAccount should return 401 without token`() {
        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/virtual-accounts")
            .then()
            .statusCode(401)
    }

    @Test
    fun `createVirtualAccount should return 404 when customer not found`() {
        whenever(virtualAccountService.createByExternalId(any(), any()))
            .thenThrow(VirtualAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/virtual-accounts")
            .then()
            .statusCode(404)
    }

    @Test
    fun `createVirtualAccount should return 502 when Bridge API fails`() {
        whenever(virtualAccountService.createByExternalId(any(), any()))
            .thenThrow(VirtualAccountException("Failed to create virtual account via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val request =
            CreateVirtualAccountRequest(
                walletId = "wallet-789",
                sourceCurrency = "usd",
                sourcePaymentRail = "ach",
                destinationCurrency = "usdc",
                destinationPaymentRail = "base",
            )

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/virtual-accounts")
            .then()
            .statusCode(502)
    }

    @Test
    fun `listVirtualAccounts should return 200 with list`() {
        val virtualAccounts =
            listOf(
                VirtualAccountResponse(
                    id = "va_001",
                    status = null,
                    sourceCurrency = "usd",
                    sourcePaymentRail = "ach",
                    destinationCurrency = "usdc",
                    destinationPaymentRail = "base",
                    sourceDepositInstructions =
                    SourceDepositInstructions(
                        paymentRail = "ach",
                        currency = "usd",
                        bankName = "Test Bank",
                        bankAddress = null,
                        bankRoutingNumber = "111000025",
                        bankAccountNumber = "000123456789",
                        clabe = null,
                        bankCode = null,
                        beneficiaryName = null,
                        depositMessage = null,
                    ),
                    destination =
                    VirtualAccountDestination(
                        currency = "usdc",
                        paymentRail = "base",
                        address = "0xabc123",
                    ),
                ),
            )

        whenever(virtualAccountService.listByExternalId(any())).thenReturn(virtualAccounts)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/virtual-accounts")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<VirtualAccountResponse>::class.java)

        assertEquals(1, response.size)
        assertEquals("va_001", response[0].id)
        assertEquals("ach", response[0].sourceDepositInstructions?.paymentRail)
    }

    @Test
    fun `listVirtualAccounts should return 401 without token`() {
        RestAssured.given().`when`().get("/customers/me/virtual-accounts").then().statusCode(401)
    }

    @Test
    fun `listVirtualAccounts should return 404 when customer not found`() {
        whenever(virtualAccountService.listByExternalId(any()))
            .thenThrow(VirtualAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/virtual-accounts")
            .then()
            .statusCode(404)
    }

    @Test
    fun `listVirtualAccounts should return 502 when Bridge API fails`() {
        whenever(virtualAccountService.listByExternalId(any()))
            .thenThrow(VirtualAccountException("Failed to list virtual accounts via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/virtual-accounts")
            .then()
            .statusCode(502)
    }

    @Test
    fun `getVirtualAccountActivity should return 200 with activity list`() {
        val activities =
            listOf(
                VirtualAccountActivity(
                    id = "activity_001",
                    type = "deposit",
                    virtualAccountId = "va_123",
                    amount = "100.00",
                    currency = "usd",
                    developerFeeAmount = null,
                    exchangeFeeAmount = null,
                    subtotalAmount = null,
                    gasFee = null,
                    depositId = null,
                    destinationTxHash = null,
                    source =
                    VirtualAccountActivitySource(
                        paymentRail = "ach",
                        description = null,
                        senderName = "John Doe",
                        senderBankRoutingNumber = "021000021",
                    ),
                    createdAt = "2024-01-15T10:00:00Z",
                ),
            )

        whenever(virtualAccountService.getActivityByExternalId(any(), any())).thenReturn(activities)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/virtual-accounts/va_123/activity")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<VirtualAccountActivity>::class.java)

        assertEquals(1, response.size)
        assertEquals("activity_001", response[0].id)
        assertEquals("deposit", response[0].type)
        assertEquals("100.00", response[0].amount)
        assertEquals("ach", response[0].source?.paymentRail)
    }

    @Test
    fun `getVirtualAccountActivity should return 401 without token`() {
        RestAssured.given()
            .`when`()
            .get("/customers/me/virtual-accounts/va_123/activity")
            .then()
            .statusCode(401)
    }

    @Test
    fun `getVirtualAccountActivity should return 404 when customer not found`() {
        whenever(virtualAccountService.getActivityByExternalId(any(), any()))
            .thenThrow(VirtualAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/virtual-accounts/va_123/activity")
            .then()
            .statusCode(404)
    }

    @Test
    fun `getVirtualAccountActivity should return 502 when Bridge API fails`() {
        whenever(virtualAccountService.getActivityByExternalId(any(), any()))
            .thenThrow(VirtualAccountException("Failed to get virtual account activity via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/virtual-accounts/va_123/activity")
            .then()
            .statusCode(502)
    }
}
