package com.rytmo.server

import com.rytmo.library.exceptions.KycLinkCreationException
import com.rytmo.library.services.KycService
import com.rytmo.models.kyc.CreateKycLinkRequest
import com.rytmo.models.kyc.KycLinkResponse
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
class CustomerResourceKycTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var kycService: KycService

    @Test
    fun `createKycLink should return 201 with valid token and request`() {
        val fullName = "John Doe"
        val email = "john@example.com"

        val kycLinkResponse =
            KycLinkResponse(
                id = "kyc_link_abc",
                kycLink = "https://kyc.bridge.xyz/abc",
                kycStatus = "not_started",
                tosLink = "https://tos.bridge.xyz/abc",
                tosStatus = "pending",
                createdAt = "2024-01-15T10:00:00Z",
            )

        whenever(kycService.createKycLinkByExternalId(any(), eq(fullName), eq(email)))
            .thenReturn(kycLinkResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(CreateKycLinkRequest(fullName, email))
                .`when`()
                .post("/customers/kyc")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .`as`(KycLinkResponse::class.java)

        assertEquals("kyc_link_abc", response.id)
        assertEquals("https://kyc.bridge.xyz/abc", response.kycLink)
        assertEquals("not_started", response.kycStatus)
        assertEquals("https://tos.bridge.xyz/abc", response.tosLink)
        assertEquals("pending", response.tosStatus)
    }

    @Test
    fun `createKycLink should return 401 without authentication token`() {
        val fullName = "John Doe"
        val email = "john@example.com"

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(CreateKycLinkRequest(fullName, email))
            .`when`()
            .post("/customers/kyc")
            .then()
            .statusCode(401)
    }

    @Test
    fun `createKycLink should return 404 when customer not found`() {
        val fullName = "John Doe"
        val email = "john@example.com"

        whenever(kycService.createKycLinkByExternalId(any(), any(), any()))
            .thenThrow(KycLinkCreationException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(CreateKycLinkRequest(fullName, email))
            .`when`()
            .post("/customers/kyc")
            .then()
            .statusCode(404)
    }

    @Test
    fun `createKycLink should return 502 when Bridge API fails`() {
        val fullName = "John Doe"
        val email = "john@example.com"

        whenever(kycService.createKycLinkByExternalId(any(), any(), any()))
            .thenThrow(KycLinkCreationException("Failed to create KYC link via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(CreateKycLinkRequest(fullName, email))
            .`when`()
            .post("/customers/kyc")
            .then()
            .statusCode(502)
    }

    @Test
    fun `listKycLinks should return 200 with valid token`() {
        val kycLinks =
            listOf(
                KycLinkResponse(
                    id = "kyc_link_abc",
                    kycLink = "https://kyc.bridge.xyz/abc",
                    kycStatus = "approved",
                    tosLink = "https://tos.bridge.xyz/abc",
                    tosStatus = "approved",
                    createdAt = "2024-01-15T10:00:00Z",
                ),
            )

        whenever(kycService.listKycLinksByExternalId(any())).thenReturn(kycLinks)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/kyc")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<KycLinkResponse>::class.java)

        assertEquals(1, response.size)
        assertEquals("kyc_link_abc", response[0].id)
        assertEquals("approved", response[0].kycStatus)
    }

    @Test
    fun `listKycLinks should return 401 without authentication token`() {
        RestAssured.given().`when`().get("/customers/kyc").then().statusCode(401)
    }

    @Test
    fun `listKycLinks should return 404 when customer not found`() {
        whenever(kycService.listKycLinksByExternalId(any()))
            .thenThrow(KycLinkCreationException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/kyc")
            .then()
            .statusCode(404)
    }

    @Test
    fun `listKycLinks should return 404 when bridge identity not found`() {
        whenever(kycService.listKycLinksByExternalId(any()))
            .thenThrow(KycLinkCreationException("Bridge identity not found for customer: cust123"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/kyc")
            .then()
            .statusCode(404)
    }

    @Test
    fun `listKycLinks should return 502 when Bridge API fails`() {
        whenever(kycService.listKycLinksByExternalId(any()))
            .thenThrow(KycLinkCreationException("Failed to get KYC links via Bridge API"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/kyc")
            .then()
            .statusCode(502)
    }
}
