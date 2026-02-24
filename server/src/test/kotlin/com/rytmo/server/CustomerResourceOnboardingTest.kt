package com.rytmo.server

import com.rytmo.library.services.BridgeApiException
import com.rytmo.library.services.KycService
import com.rytmo.models.onboarding.BridgeOnboardingStatus
import com.rytmo.server.test.PrivyTestProfile
import com.rytmo.server.utils.AccessTokenUtil
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.MockitoConfig
import io.restassured.RestAssured
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
class CustomerResourceOnboardingTest {
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
    fun `getBridgeOnboardingStatus should return 200 with status`() {
        val status =
            BridgeOnboardingStatus(
                kycStatus = "approved",
                tosStatus = "approved",
                kycLink = "https://kyc.bridge.xyz/abc",
                tosLink = "https://tos.bridge.xyz/abc",
                rejectionReasons = emptyList(),
                createdAt = "2024-01-15T10:00:00Z",
            )

        whenever(kycService.getOnboardingStatusByExternalId(any())).thenReturn(status)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/onboarding/bridge")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(BridgeOnboardingStatus::class.java)

        assertEquals("approved", response.kycStatus)
        assertEquals("approved", response.tosStatus)
        assertEquals("https://kyc.bridge.xyz/abc", response.kycLink)
        assertEquals("https://tos.bridge.xyz/abc", response.tosLink)
    }

    @Test
    fun `getBridgeOnboardingStatus should return 401 without token`() {
        RestAssured.given().`when`().get("/customers/me/onboarding/bridge").then().statusCode(401)
    }

    @Test
    fun `getBridgeOnboardingStatus should return 404 when customer not found`() {
        whenever(kycService.getOnboardingStatusByExternalId(any())).thenReturn(null)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/onboarding/bridge")
            .then()
            .statusCode(404)
    }

    @Test
    fun `getBridgeOnboardingStatus should return 502 when Bridge API fails`() {
        whenever(kycService.getOnboardingStatusByExternalId(any()))
            .thenThrow(BridgeApiException("API error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/onboarding/bridge")
            .then()
            .statusCode(502)
    }
}
