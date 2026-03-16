package com.rytmo.server

import com.rytmo.library.exceptions.PrivyWalletException
import com.rytmo.library.services.DFlowService
import com.rytmo.library.services.HeliusService
import com.rytmo.library.services.JupiterService
import com.rytmo.library.services.PrivyServerWalletService
import com.rytmo.library.services.SolanaService
import com.rytmo.library.services.SolanaWalletInfo
import com.rytmo.library.services.SwapSponsorService
import com.rytmo.models.swap.SwapExecuteResponse
import com.rytmo.models.swap.SwapQuoteResponse
import com.rytmo.models.swap.SwapStatusResponse
import com.rytmo.server.test.BackendWalletTestProfile
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
@TestProfile(BackendWalletTestProfile::class)
class SwapResourceBackendWalletTest {
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

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var solanaService: SolanaService

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var swapSponsorService: SwapSponsorService

    @Test
    fun `execute backend-wallet mode should return 200 with signature`() {
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
        whenever(swapSponsorService.execute(any(), any())).thenReturn("sponsortxsig123")

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

        assertEquals("sponsortxsig123", response.signature)
    }

    @Test
    fun `execute backend-wallet mode should return 502 on exception`() {
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
        whenever(swapSponsorService.execute(any(), any()))
            .thenThrow(PrivyWalletException("sponsor failed", 502))

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
    fun `getStatus backend-wallet mode should return 200 with on-chain status`() {
        val status = SwapStatusResponse(status = "closed", fills = emptyList(), error = null)
        whenever(solanaService.getSignatureStatus(any())).thenReturn(status)

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
    fun `getStatus backend-wallet mode should return 502 on exception`() {
        whenever(solanaService.getSignatureStatus(any()))
            .thenThrow(RuntimeException("Helius RPC error"))

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
