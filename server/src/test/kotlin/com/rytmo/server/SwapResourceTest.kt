package com.rytmo.server

import com.rytmo.library.exceptions.DFlowException
import com.rytmo.library.exceptions.PrivyWalletException
import com.rytmo.library.services.DFlowService
import com.rytmo.library.services.HeliusService
import com.rytmo.library.services.JupiterService
import com.rytmo.library.services.PrivyServerWalletService
import com.rytmo.library.services.SolanaWalletInfo
import com.rytmo.models.swap.OutputToken
import com.rytmo.models.swap.SwapExecuteResponse
import com.rytmo.models.swap.SwapHistoryResponse
import com.rytmo.models.swap.SwapQuoteResponse
import com.rytmo.models.swap.SwapStatusResponse
import com.rytmo.models.swap.TokenBalance
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
class SwapResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var dFlowService: DFlowService

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var heliusService: HeliusService

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var jupiterService: JupiterService

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var privyServerWalletService: PrivyServerWalletService

    // ---- /swap/execute ----

    @Test
    fun `execute should return 200 with signature`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "FakeWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        val quote =
            SwapQuoteResponse(
                transaction = "base64encodedtx==",
                inputMint = "So11111111111111111111111111111111111111112",
                outputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                inAmount = "1000000000",
                outAmount = "150000000",
                executionMode = "sync",
                slippageBps = 50,
            )
        whenever(dFlowService.getOrderQuote(any(), any(), any(), any(), any(), any())).thenReturn(quote)
        whenever(privyServerWalletService.signAndSend(any(), any())).thenReturn("txsig123")

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(
                    """{"inputMint":"So11111111111111111111111111111111111111112","outputMint":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"1000000000","slippageBps":null}""",
                )
                .`when`()
                .post("/swap/execute")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SwapExecuteResponse::class.java)

        assertEquals("txsig123", response.signature)
    }

    @Test
    fun `execute should return 200 with custom slippageBps`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "FakeWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        val quote =
            SwapQuoteResponse(
                transaction = "base64encodedtx==",
                inputMint = "So11111111111111111111111111111111111111112",
                outputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                inAmount = "1000000000",
                outAmount = "150000000",
                executionMode = "sync",
                slippageBps = 100,
            )
        whenever(dFlowService.getOrderQuote(any(), any(), any(), any(), any(), any())).thenReturn(quote)
        whenever(privyServerWalletService.signAndSend(any(), any())).thenReturn("txsig456")

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(
                    """{"inputMint":"So11111111111111111111111111111111111111112","outputMint":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"1000000000","slippageBps":"100"}""",
                )
                .`when`()
                .post("/swap/execute")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SwapExecuteResponse::class.java)

        assertEquals("txsig456", response.signature)
    }

    @Test
    fun `execute should return 401 without token`() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                """{"inputMint":"So11111111111111111111111111111111111111112","outputMint":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"1000000000","slippageBps":null}""",
            )
            .`when`()
            .post("/swap/execute")
            .then()
            .statusCode(401)
    }

    @Test
    fun `execute should return 502 when DFlow fails`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "FakeWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        whenever(dFlowService.getOrderQuote(any(), any(), any(), any(), any(), any()))
            .thenThrow(DFlowException("DFlow error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(
                """{"inputMint":"So11111111111111111111111111111111111111112","outputMint":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"1000000000","slippageBps":null}""",
            )
            .`when`()
            .post("/swap/execute")
            .then()
            .statusCode(502)
    }

    @Test
    fun `execute should return 502 when Privy wallet lookup fails`() {
        whenever(privyServerWalletService.getSolanaWallet(any()))
            .thenThrow(PrivyWalletException("No wallet found", 404))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(
                """{"inputMint":"So11111111111111111111111111111111111111112","outputMint":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"1000000000","slippageBps":null}""",
            )
            .`when`()
            .post("/swap/execute")
            .then()
            .statusCode(502)
    }

    // ---- /swap/quote ----

    @Test
    fun `getQuote should return 200 with quote`() {
        val quote =
            SwapQuoteResponse(
                transaction = "base64encodedtx==",
                inputMint = "So11111111111111111111111111111111111111112",
                outputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                inAmount = "1000000000",
                outAmount = "150000000",
                executionMode = "sync",
                slippageBps = 50,
            )
        whenever(dFlowService.getOrderQuote(any(), any(), any(), any(), any(), any())).thenReturn(quote)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("userPublicKey", "FakePublicKey111")
                .queryParam("inputMint", "So11111111111111111111111111111111111111112")
                .queryParam("outputMint", "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                .queryParam("amount", "1000000000")
                .`when`()
                .get("/swap/quote")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SwapQuoteResponse::class.java)

        assertEquals("base64encodedtx==", response.transaction)
        assertEquals("sync", response.executionMode)
        assertEquals(50, response.slippageBps)
    }

    @Test
    fun `getQuote should return 401 without token`() {
        RestAssured.given()
            .queryParam("userPublicKey", "FakePublicKey111")
            .queryParam("inputMint", "So11111111111111111111111111111111111111112")
            .queryParam("outputMint", "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
            .queryParam("amount", "1000000000")
            .`when`()
            .get("/swap/quote")
            .then()
            .statusCode(401)
    }

    @Test
    fun `getQuote should return 502 when DFlow fails`() {
        whenever(dFlowService.getOrderQuote(any(), any(), any(), any(), any(), any()))
            .thenThrow(DFlowException("DFlow error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("userPublicKey", "FakePublicKey111")
            .queryParam("inputMint", "So11111111111111111111111111111111111111112")
            .queryParam("outputMint", "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
            .queryParam("amount", "1000000000")
            .`when`()
            .get("/swap/quote")
            .then()
            .statusCode(502)
    }

    // ---- /swap/tokens ----

    @Test
    fun `getTokens should return 200 with balances`() {
        val balances =
            listOf(
                TokenBalance(
                    mint = "So11111111111111111111111111111111111111112",
                    symbol = "SOL",
                    name = "Solana",
                    balance = "5000000000",
                    decimals = 9,
                    priceUsd = 150.0,
                    imageUrl = null,
                ),
            )
        whenever(heliusService.getTokenBalances(any())).thenReturn(balances)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("walletAddress", "FakeWallet111")
                .`when`()
                .get("/swap/tokens")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<TokenBalance>::class.java)

        assertEquals(1, response.size)
        assertEquals("SOL", response[0].symbol)
        assertEquals("5000000000", response[0].balance)
    }

    @Test
    fun `getTokens should return 401 without token`() {
        RestAssured.given()
            .queryParam("walletAddress", "FakeWallet111")
            .`when`()
            .get("/swap/tokens")
            .then()
            .statusCode(401)
    }

    @Test
    fun `getTokens should return 502 when Helius fails`() {
        whenever(heliusService.getTokenBalances(any())).thenThrow(RuntimeException("Helius error"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("walletAddress", "FakeWallet111")
            .`when`()
            .get("/swap/tokens")
            .then()
            .statusCode(502)
    }

    // ---- /swap/output-tokens ----

    @Test
    fun `getOutputTokens should return 200 with token list`() {
        val tokens =
            listOf(
                OutputToken(
                    mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                    symbol = "USDC",
                    name = "USD Coin",
                    decimals = 6,
                    logoURI = null,
                ),
            )
        whenever(jupiterService.getOutputTokens()).thenReturn(tokens)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/swap/output-tokens")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Array<OutputToken>::class.java)

        assertEquals(1, response.size)
        assertEquals("USDC", response[0].symbol)
    }

    @Test
    fun `getOutputTokens should return 401 without token`() {
        RestAssured.given().`when`().get("/swap/output-tokens").then().statusCode(401)
    }

    @Test
    fun `getOutputTokens should return 502 when Jupiter fails`() {
        whenever(jupiterService.getOutputTokens()).thenThrow(RuntimeException("Jupiter error"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/swap/output-tokens")
            .then()
            .statusCode(502)
    }

    // ---- /swap/history ----

    @Test
    fun `getHistory should return 200 with items`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "FakeWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        val history =
            SwapHistoryResponse(
                items =
                listOf(
                    com.rytmo.models.swap.SwapHistoryItem(
                        signature = "sig1",
                        timestamp = 1700000000L,
                        success = true,
                        confirmationStatus = "finalized",
                    ),
                ),
                nextCursor = null,
            )
        whenever(heliusService.getSwapHistory(any(), anyOrNull(), any())).thenReturn(history)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/swap/history")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SwapHistoryResponse::class.java)

        assertEquals(1, response.items.size)
        assertEquals("sig1", response.items[0].signature)
        assertEquals("finalized", response.items[0].confirmationStatus)
    }

    @Test
    fun `getHistory should return 401 without token`() {
        RestAssured.given().`when`().get("/swap/history").then().statusCode(401)
    }

    @Test
    fun `getHistory should return 502 when Helius fails`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "FakeWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        whenever(heliusService.getSwapHistory(any(), anyOrNull(), any()))
            .thenThrow(RuntimeException("Helius error"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/swap/history")
            .then()
            .statusCode(502)
    }

    // ---- /swap/status ----

    @Test
    fun `getStatus should return 200 with status`() {
        val status =
            SwapStatusResponse(
                status = "closed",
                fills = emptyList(),
                error = null,
            )
        whenever(dFlowService.getOrderStatus(any(), anyOrNull())).thenReturn(status)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("signature", "fakeSig123")
                .`when`()
                .get("/swap/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SwapStatusResponse::class.java)

        assertEquals("closed", response.status)
    }

    @Test
    fun `getStatus should return 200 with open status`() {
        val status = SwapStatusResponse(status = "open", fills = emptyList(), error = null)
        whenever(dFlowService.getOrderStatus(any(), anyOrNull())).thenReturn(status)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("signature", "fakeSig123")
                .`when`()
                .get("/swap/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SwapStatusResponse::class.java)

        assertEquals("open", response.status)
    }

    @Test
    fun `getStatus should return 401 without token`() {
        RestAssured.given()
            .queryParam("signature", "fakeSig123")
            .`when`()
            .get("/swap/status")
            .then()
            .statusCode(401)
    }

    @Test
    fun `getStatus should return 502 when DFlow fails`() {
        whenever(dFlowService.getOrderStatus(any(), anyOrNull()))
            .thenThrow(DFlowException("DFlow error", 500))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .queryParam("signature", "fakeSig123")
            .`when`()
            .get("/swap/status")
            .then()
            .statusCode(502)
    }
}
