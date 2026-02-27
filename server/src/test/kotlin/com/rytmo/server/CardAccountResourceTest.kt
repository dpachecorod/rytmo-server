package com.rytmo.server

import com.rytmo.library.exceptions.CardAccountException
import com.rytmo.library.services.CardAccountService
import com.rytmo.models.cardaccounts.CardAccountResponse
import com.rytmo.models.cardaccounts.CardBalancesResponse
import com.rytmo.models.cardaccounts.CardDetailsResponse
import com.rytmo.models.cardaccounts.CardFundingInstructionsResponse
import com.rytmo.models.cardaccounts.CardTransactionResponse
import com.rytmo.models.cardaccounts.CardholderNameResponse
import com.rytmo.models.cardaccounts.CreateCardAccountRequest
import com.rytmo.models.cardaccounts.PaginatedCardTransactionsResponse
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
class CardAccountResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var cardAccountService: CardAccountService

    @Test
    fun `provisionCardAccount should return 201 with valid request`() {
        val cardAccountResponse =
            CardAccountResponse(
                id = "card_001",
                status = "active",
                cardholderName =
                CardholderNameResponse(
                    firstName = "John",
                    middleName = null,
                    lastName = "Doe",
                ),
                cardDetails =
                CardDetailsResponse(
                    last4 = "1234",
                    expiry = "12/28",
                    bin = "411111",
                    pinStatus = "not_set",
                ),
                balances =
                CardBalancesResponse(
                    available = "100.00",
                    hold = "0.00",
                ),
                fundingInstructions =
                CardFundingInstructionsResponse(
                    currency = "usdc",
                    chain = "base",
                    address = "0xfunding123",
                ),
            )

        whenever(cardAccountService.provisionByExternalId(any(), any())).thenReturn(cardAccountResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/customers/me/card-accounts")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .`as`(CardAccountResponse::class.java)

        assertEquals("card_001", response.id)
        assertEquals("active", response.status)
        assertEquals("John", response.cardholderName?.firstName)
        assertEquals("1234", response.cardDetails?.last4)
        assertEquals("100.00", response.balances?.available)
        assertEquals("usdc", response.fundingInstructions?.currency)
    }

    @Test
    fun `provisionCardAccount should return 401 without token`() {
        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/card-accounts")
            .then()
            .statusCode(401)
    }

    @Test
    fun `provisionCardAccount should return 404 when customer not found`() {
        whenever(cardAccountService.provisionByExternalId(any(), any()))
            .thenThrow(CardAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/card-accounts")
            .then()
            .statusCode(404)
    }

    @Test
    fun `provisionCardAccount should return 502 when Bridge API fails`() {
        whenever(cardAccountService.provisionByExternalId(any(), any()))
            .thenThrow(CardAccountException("Failed to provision card account via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val request =
            CreateCardAccountRequest(
                currency = "usdc",
                chain = "base",
                walletAddress = "0xabc123",
            )

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/card-accounts")
            .then()
            .statusCode(502)
    }

    @Test
    fun `listCardAccounts should return 200 with list`() {
        val cardAccounts =
            listOf(
                CardAccountResponse(
                    id = "card_001",
                    status = "active",
                    cardholderName =
                    CardholderNameResponse(
                        firstName = "John",
                        middleName = null,
                        lastName = "Doe",
                    ),
                    cardDetails = null,
                    balances =
                    CardBalancesResponse(
                        available = "50.00",
                        hold = "0.00",
                    ),
                    fundingInstructions =
                    CardFundingInstructionsResponse(
                        currency = "usdc",
                        chain = "base",
                        address = "0xfunding123",
                    ),
                ),
            )

        whenever(cardAccountService.listByExternalId(any())).thenReturn(cardAccounts)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/card-accounts")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<CardAccountResponse>::class.java)

        assertEquals(1, response.size)
        assertEquals("card_001", response[0].id)
        assertEquals("50.00", response[0].balances?.available)
    }

    @Test
    fun `listCardAccounts should return 401 without token`() {
        RestAssured.given().`when`().get("/customers/me/card-accounts").then().statusCode(401)
    }

    @Test
    fun `listCardAccounts should return 404 when customer not found`() {
        whenever(cardAccountService.listByExternalId(any()))
            .thenThrow(CardAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/card-accounts")
            .then()
            .statusCode(404)
    }

    @Test
    fun `listCardAccounts should return 502 when Bridge API fails`() {
        whenever(cardAccountService.listByExternalId(any()))
            .thenThrow(CardAccountException("Failed to list card accounts via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/card-accounts")
            .then()
            .statusCode(502)
    }

    @Test
    fun `getCardTransactions should return 200 with paginated transaction list`() {
        val paginatedResponse =
            PaginatedCardTransactionsResponse(
                page = 1,
                paginationToken = "next_token_123",
                count = 1,
                totalPages = 3,
                totalCount = 25,
                data =
                listOf(
                    CardTransactionResponse(
                        id = "txn_001",
                        cardAccountId = "card_001",
                        category = "purchase",
                        amount = "25.00",
                        billingAmount = null,
                        currency = "usd",
                        merchantName = "Coffee Shop",
                        merchantLocation = "New York, NY",
                        merchantCategoryCode = "5812",
                        description = "Coffee purchase",
                        postedAt = "2024-01-15T10:00:00Z",
                        authorizedAt = null,
                        status = "posted",
                    ),
                ),
            )

        whenever(
            cardAccountService.getTransactionsByExternalId(
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
            .thenReturn(paginatedResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/card-accounts/card_001/transactions")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(PaginatedCardTransactionsResponse::class.java)

        assertEquals(1, response.page)
        assertEquals("next_token_123", response.paginationToken)
        assertEquals(1, response.count)
        assertEquals(3, response.totalPages)
        assertEquals(25, response.totalCount)
        assertEquals(1, response.data.size)
        assertEquals("txn_001", response.data[0].id)
        assertEquals("purchase", response.data[0].category)
        assertEquals("25.00", response.data[0].amount)
        assertEquals("Coffee Shop", response.data[0].merchantName)
    }

    @Test
    fun `getCardTransactions should return 401 without token`() {
        RestAssured.given()
            .`when`()
            .get("/customers/me/card-accounts/card_001/transactions")
            .then()
            .statusCode(401)
    }

    @Test
    fun `getCardTransactions should return 404 when customer not found`() {
        whenever(
            cardAccountService.getTransactionsByExternalId(
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
            .thenThrow(CardAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/card-accounts/card_001/transactions")
            .then()
            .statusCode(404)
    }

    @Test
    fun `getCardTransactions should return 502 when Bridge API fails`() {
        whenever(
            cardAccountService.getTransactionsByExternalId(
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
            .thenThrow(CardAccountException("Failed to get card transactions via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/card-accounts/card_001/transactions")
            .then()
            .statusCode(502)
    }
}
