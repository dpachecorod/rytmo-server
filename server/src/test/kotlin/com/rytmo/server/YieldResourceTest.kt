package com.rytmo.server

import com.rytmo.library.exceptions.DeframeException
import com.rytmo.library.services.DeframeService
import com.rytmo.models.yield.YieldBytecodeMetadata
import com.rytmo.models.yield.YieldBytecodeResponse
import com.rytmo.models.yield.YieldBytecodeStep
import com.rytmo.models.yield.YieldQuoteResponse
import com.rytmo.models.yield.YieldStrategiesResponse
import com.rytmo.models.yield.YieldStrategy
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
class YieldResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var deframeService: DeframeService

    private val strategy =
        YieldStrategy(
            id = "strat-1",
            slug = "aave-usdc",
            protocol = "aave",
            asset = "0xusdc",
            assetName = "USDC",
            network = "base",
            networkId = "8453",
            apy = 4.5,
            paused = false,
            availableActions = listOf("lend", "withdraw"),
            logoUrl = null,
            fee = null,
        )

    @Test
    fun `GET yield strategies returns 200`() {
        val strategiesResponse =
            YieldStrategiesResponse(
                strategies = listOf(strategy),
                totalDocs = 1,
                limit = 10,
                page = 1,
                totalPages = 1,
                hasNextPage = false,
                hasPrevPage = false,
            )
        whenever(deframeService.listStrategies(any(), any())).thenReturn(strategiesResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/yield/strategies")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(YieldStrategiesResponse::class.java)

        assertEquals(1, response.strategies.size)
        assertEquals("strat-1", response.strategies[0].id)
    }

    @Test
    fun `GET yield strategies returns 401 without token`() {
        RestAssured.given().`when`().get("/yield/strategies").then().statusCode(401)
    }

    @Test
    fun `GET yield strategy by id returns 200`() {
        whenever(deframeService.getStrategy(any())).thenReturn(strategy)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/yield/strategies/strat-1")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(YieldStrategy::class.java)

        assertEquals("strat-1", response.id)
        assertEquals("aave", response.protocol)
    }

    @Test
    fun `GET yield strategy quote returns 200`() {
        val quote = YieldQuoteResponse(expectedReturn = "105.00", fee = "0.50", apy = 4.5)
        whenever(deframeService.getQuote(any(), any(), any())).thenReturn(quote)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("amount", "100000000")
                .queryParam("walletAddress", "0xwallet")
                .`when`()
                .get("/yield/strategies/strat-1/quote")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(YieldQuoteResponse::class.java)

        assertEquals("105.00", response.expectedReturn)
    }

    @Test
    fun `GET yield strategy bytecode lend returns 200`() {
        val bytecodeResponse =
            YieldBytecodeResponse(
                feeCharged = "0",
                metadata =
                YieldBytecodeMetadata(
                    isCrossChain = false,
                    isSameChainSwap = false,
                    crossChainQuoteId = null,
                ),
                bytecode =
                listOf(
                    YieldBytecodeStep(
                        chainId = 8453,
                        to = "0xcontract",
                        data = "0xdata",
                        value = "0",
                        from = "0xwallet",
                    ),
                ),
            )
        whenever(deframeService.getBytecode(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(bytecodeResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("action", "lend")
                .queryParam("amount", "100000000")
                .queryParam("walletAddress", "0xwallet")
                .`when`()
                .get("/yield/strategies/strat-1/bytecode")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(YieldBytecodeResponse::class.java)

        assertEquals("0", response.feeCharged)
        assertEquals(1, response.bytecode.size)
        assertEquals(8453, response.bytecode[0].chainId)
    }

    @Test
    fun `GET yield strategy bytecode withdraw returns 200`() {
        val bytecodeResponse =
            YieldBytecodeResponse(
                feeCharged = "0",
                metadata =
                YieldBytecodeMetadata(
                    isCrossChain = false,
                    isSameChainSwap = false,
                    crossChainQuoteId = null,
                ),
                bytecode =
                listOf(
                    YieldBytecodeStep(
                        chainId = 8453,
                        to = "0xcontract",
                        data = "0xdata",
                        value = null,
                        from = null,
                    ),
                ),
            )
        whenever(deframeService.getBytecode(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(bytecodeResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("action", "withdraw")
            .queryParam("amount", "100000000")
            .queryParam("walletAddress", "0xwallet")
            .`when`()
            .get("/yield/strategies/strat-1/bytecode")
            .then()
            .statusCode(200)
    }

    @Test
    fun `GET yield strategies with explicit page and limit returns 200`() {
        val strategiesResponse =
            YieldStrategiesResponse(
                strategies = emptyList(),
                totalDocs = 0,
                limit = 5,
                page = 2,
                totalPages = 0,
                hasNextPage = false,
                hasPrevPage = true,
            )
        whenever(deframeService.listStrategies(any(), any())).thenReturn(strategiesResponse)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("page", 2)
            .queryParam("limit", 5)
            .`when`()
            .get("/yield/strategies")
            .then()
            .statusCode(200)
    }

    @Test
    fun `GET yield strategies returns 502 on DeframeException`() {
        whenever(deframeService.listStrategies(any(), any()))
            .thenThrow(DeframeException("DFrame error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/yield/strategies")
            .then()
            .statusCode(502)
    }

    @Test
    fun `GET yield strategy by id returns 502 on DeframeException`() {
        whenever(deframeService.getStrategy(any())).thenThrow(DeframeException("Not found", 404))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/yield/strategies/missing")
            .then()
            .statusCode(502)
    }

    @Test
    fun `GET yield strategy quote returns 502 on DeframeException`() {
        whenever(deframeService.getQuote(any(), any(), any()))
            .thenThrow(DeframeException("DFrame error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("amount", "100000000")
            .queryParam("walletAddress", "0xwallet")
            .`when`()
            .get("/yield/strategies/strat-1/quote")
            .then()
            .statusCode(502)
    }

    @Test
    fun `GET yield strategy bytecode returns 502 on DeframeException`() {
        whenever(deframeService.getBytecode(any(), any(), any(), any(), anyOrNull()))
            .thenThrow(DeframeException("DFrame error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("action", "lend")
            .queryParam("amount", "100000000")
            .queryParam("walletAddress", "0xwallet")
            .`when`()
            .get("/yield/strategies/strat-1/bytecode")
            .then()
            .statusCode(502)
    }
}
