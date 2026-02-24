package com.rytmo.server

import com.rytmo.library.exceptions.BridgeCustomerException
import com.rytmo.library.services.BridgeCustomerService
import com.rytmo.models.bridge.BridgeCustomer
import com.rytmo.models.bridge.CreateBridgeCustomerRequest
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
class CustomerResourceBridgeCustomerTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var bridgeCustomerService: BridgeCustomerService

    @Test
    fun `createBridgeCustomer should return 201 with created customer`() {
        val request =
            CreateBridgeCustomerRequest(
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
            )

        val bridgeCustomer =
            BridgeCustomer(
                id = "bridge-cust-123",
                status = "active",
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
                createdAt = "2026-02-20T10:00:00Z",
            )

        whenever(
            bridgeCustomerService.createByExternalId(
                externalId = any(),
                firstName = eq("John"),
                lastName = eq("Doe"),
                email = eq("john@example.com"),
            ),
        )
            .thenReturn(bridgeCustomer)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/customers/bridge")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .`as`(BridgeCustomer::class.java)

        assertEquals("bridge-cust-123", response.id)
        assertEquals("active", response.status)
        assertEquals("John", response.firstName)
        assertEquals("Doe", response.lastName)
        assertEquals("john@example.com", response.email)
    }

    @Test
    fun `createBridgeCustomer should return 401 without authentication token`() {
        val request =
            CreateBridgeCustomerRequest(
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
            )

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/bridge")
            .then()
            .statusCode(401)
    }

    @Test
    fun `createBridgeCustomer should return 404 when customer not found`() {
        whenever(bridgeCustomerService.createByExternalId(any(), any(), any(), any()))
            .thenThrow(BridgeCustomerException("Customer not found for external ID: did:privy:tester"))

        val request =
            CreateBridgeCustomerRequest(
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
            )
        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/bridge")
            .then()
            .statusCode(404)
    }

    @Test
    fun `createBridgeCustomer should return 502 when Bridge API fails`() {
        whenever(bridgeCustomerService.createByExternalId(any(), any(), any(), any()))
            .thenThrow(BridgeCustomerException("Failed to create Bridge customer: API error"))

        val request =
            CreateBridgeCustomerRequest(
                firstName = "John",
                lastName = "Doe",
                email = "john@example.com",
            )
        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/bridge")
            .then()
            .statusCode(502)
    }
}
