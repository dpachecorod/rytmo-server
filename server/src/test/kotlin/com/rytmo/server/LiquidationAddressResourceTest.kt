package com.rytmo.server

import com.rytmo.library.exceptions.LiquidationAddressException
import com.rytmo.library.services.LiquidationAddressService
import com.rytmo.models.liquidationaddresses.LiquidationAddressResponse
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
class LiquidationAddressResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var liquidationAddressService: LiquidationAddressService

    @Test
    fun `listLiquidationAddresses should return 200 with list`() {
        val addresses =
            listOf(
                LiquidationAddressResponse(
                    id = "liq_addr_001",
                    currency = "usdc",
                    chain = "base",
                    externalAccountId = "ext_acct_789",
                    address = "0xcrypto-address",
                    createdAt = "2024-01-15T10:00:00Z",
                ),
            )

        whenever(liquidationAddressService.listByExternalId(any())).thenReturn(addresses)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me/liquidation-addresses")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<LiquidationAddressResponse>::class.java)

        assertEquals(1, response.size)
        assertEquals("liq_addr_001", response[0].id)
        assertEquals("usdc", response[0].currency)
        assertEquals("base", response[0].chain)
        assertEquals("0xcrypto-address", response[0].address)
    }

    @Test
    fun `listLiquidationAddresses should return 401 without token`() {
        RestAssured.given().`when`().get("/customers/me/liquidation-addresses").then().statusCode(401)
    }

    @Test
    fun `listLiquidationAddresses should return 404 when customer not found`() {
        whenever(liquidationAddressService.listByExternalId(any()))
            .thenThrow(
                LiquidationAddressException("Customer not found for external ID: did:privy:tester"),
            )

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/liquidation-addresses")
            .then()
            .statusCode(404)
    }

    @Test
    fun `listLiquidationAddresses should return 502 when Bridge API fails`() {
        whenever(liquidationAddressService.listByExternalId(any()))
            .thenThrow(
                LiquidationAddressException("Failed to list liquidation addresses via Bridge API"),
            )

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me/liquidation-addresses")
            .then()
            .statusCode(502)
    }
}
