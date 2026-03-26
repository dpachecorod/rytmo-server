package com.rytmo.server

import com.rytmo.library.exceptions.DeviceTokenException
import com.rytmo.library.services.ExpoDeviceTokenService
import com.rytmo.models.devicetoken.DeleteDeviceTokenRequest
import com.rytmo.models.devicetoken.DeviceTokenResponse
import com.rytmo.models.devicetoken.RegisterDeviceTokenRequest
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
class DeviceTokenResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var expoDeviceTokenService: ExpoDeviceTokenService

    private val expoToken = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"

    @Test
    fun `POST should return 200 with DeviceTokenResponse`() {
        val tokenResponse =
            DeviceTokenResponse(
                expoToken = expoToken,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z",
            )
        whenever(expoDeviceTokenService.registerByExternalId(any(), any())).thenReturn(tokenResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val request = RegisterDeviceTokenRequest(expoToken = expoToken)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/customers/me/device-tokens")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(DeviceTokenResponse::class.java)

        assertEquals(expoToken, response.expoToken)
        assertEquals("2024-01-01T00:00:00Z", response.createdAt)
    }

    @Test
    fun `POST should return 401 without auth token`() {
        val request = RegisterDeviceTokenRequest(expoToken = expoToken)

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/device-tokens")
            .then()
            .statusCode(401)
    }

    @Test
    fun `POST should return 404 when customer not found`() {
        whenever(expoDeviceTokenService.registerByExternalId(any(), any()))
            .thenThrow(DeviceTokenException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val request = RegisterDeviceTokenRequest(expoToken = expoToken)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/device-tokens")
            .then()
            .statusCode(404)
    }

    @Test
    fun `DELETE should return 204`() {
        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val request = DeleteDeviceTokenRequest(expoToken = expoToken)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .delete("/customers/me/device-tokens")
            .then()
            .statusCode(204)
    }

    @Test
    fun `DELETE should return 404 when customer not found`() {
        whenever(expoDeviceTokenService.deleteByExternalId(any(), any()))
            .thenThrow(DeviceTokenException("Customer not found for external ID: did:privy:tester"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val request = DeleteDeviceTokenRequest(expoToken = expoToken)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .delete("/customers/me/device-tokens")
            .then()
            .statusCode(404)
    }

    @Test
    fun `POST should return 502 when unexpected error occurs`() {
        whenever(expoDeviceTokenService.registerByExternalId(any(), any()))
            .thenThrow(DeviceTokenException("Internal error"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val request = RegisterDeviceTokenRequest(expoToken = expoToken)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/customers/me/device-tokens")
            .then()
            .statusCode(502)
    }

    @Test
    fun `DELETE should return 502 when unexpected error occurs`() {
        whenever(expoDeviceTokenService.deleteByExternalId(any(), any()))
            .thenThrow(DeviceTokenException("Internal error"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val request = DeleteDeviceTokenRequest(expoToken = expoToken)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .delete("/customers/me/device-tokens")
            .then()
            .statusCode(502)
    }
}
