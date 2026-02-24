package com.rytmo.server

import com.rytmo.library.exceptions.ExternalAccountException
import com.rytmo.library.services.ExternalAccountService
import com.rytmo.models.externalaccounts.CreateExternalAccountRequest
import com.rytmo.models.externalaccounts.ExternalAccountAddress
import com.rytmo.models.externalaccounts.ExternalAccountResponse
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
class ExternalAccountResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var externalAccountService: ExternalAccountService

    @Test
    fun `createExternalAccount should return 201 with valid request`() {
        val externalAccountResponse =
            ExternalAccountResponse(
                id = "ext_acct_001",
                accountOwnerName = "Jane Doe",
                bankName = "Test Bank",
                last4 = "6789",
                active = "true",
                createdAt = "2024-01-15T10:00:00Z",
                updatedAt = "2024-01-16T10:00:00Z",
            )

        whenever(externalAccountService.createByExternalId(any(), any()))
            .thenReturn(externalAccountResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

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

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/customers/me/external-accounts")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .`as`(ExternalAccountResponse::class.java)

        assertEquals("ext_acct_001", response.id)
        assertEquals("Jane Doe", response.accountOwnerName)
        assertEquals("Test Bank", response.bankName)
        assertEquals("6789", response.last4)
    }

    @Test
    fun `createExternalAccount should return 401 without token`() {
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

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/external-accounts")
            .then()
            .statusCode(401)
    }

    @Test
    fun `createExternalAccount should return 404 when customer not found`() {
        whenever(externalAccountService.createByExternalId(any(), any()))
            .thenThrow(ExternalAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

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

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/external-accounts")
            .then()
            .statusCode(404)
    }

    @Test
    fun `createExternalAccount should return 502 when Bridge API fails`() {
        whenever(externalAccountService.createByExternalId(any(), any()))
            .thenThrow(ExternalAccountException("Failed to create external account via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

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

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/external-accounts")
            .then()
            .statusCode(502)
    }

    @Test
    fun `listExternalAccounts should return 200 with list`() {
        val externalAccounts =
            listOf(
                ExternalAccountResponse(
                    id = "ext_acct_001",
                    accountOwnerName = "Jane Doe",
                    bankName = "Test Bank",
                    last4 = "6789",
                    active = "true",
                ),
            )

        whenever(externalAccountService.listByExternalId(any())).thenReturn(externalAccounts)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/external-accounts")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<ExternalAccountResponse>::class.java)

        assertEquals(1, response.size)
        assertEquals("ext_acct_001", response[0].id)
        assertEquals("Test Bank", response[0].bankName)
        assertEquals("6789", response[0].last4)
    }

    @Test
    fun `listExternalAccounts should return 401 without token`() {
        RestAssured.given().`when`().get("/customers/me/external-accounts").then().statusCode(401)
    }

    @Test
    fun `listExternalAccounts should return 404 when customer not found`() {
        whenever(externalAccountService.listByExternalId(any()))
            .thenThrow(ExternalAccountException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/external-accounts")
            .then()
            .statusCode(404)
    }

    @Test
    fun `listExternalAccounts should return 502 when Bridge API fails`() {
        whenever(externalAccountService.listByExternalId(any()))
            .thenThrow(ExternalAccountException("Failed to list external accounts via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/external-accounts")
            .then()
            .statusCode(502)
    }
}
