package com.rytmo.server

import com.rytmo.library.exceptions.PrivyWalletException
import com.rytmo.library.services.PrivyServerWalletService
import com.rytmo.library.services.SolanaService
import com.rytmo.library.services.SolanaWalletInfo
import com.rytmo.library.services.SwapSponsorService
import com.rytmo.models.send.SendSolanaPartialResponse
import com.rytmo.models.send.SendSolanaResponse
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
class SendResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var privyServerWalletService: PrivyServerWalletService

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var solanaService: SolanaService

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var swapSponsorService: SwapSponsorService

    // ---- /send/solana ----

    @Test
    fun `sendSolana should return 200 with partialTransaction`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "SenderWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        whenever(solanaService.buildSplTransferTransaction(any(), any(), any(), any(), any()))
            .thenReturn("base64tx==")
        whenever(swapSponsorService.prepare(any())).thenReturn("partialTxBase64==")

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(
                    """{"recipientAddress":"RecipientWallet222","mintAddress":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"10500000","decimals":6}""",
                )
                .`when`()
                .post("/send/solana")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SendSolanaPartialResponse::class.java)

        assertEquals("partialTxBase64==", response.partialTransaction)
    }

    @Test
    fun `sendSolana should return 401 without token`() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                """{"recipientAddress":"RecipientWallet222","mintAddress":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"10500000","decimals":6}""",
            )
            .`when`()
            .post("/send/solana")
            .then()
            .statusCode(401)
    }

    @Test
    fun `sendSolana should return 502 when Privy wallet not found`() {
        whenever(privyServerWalletService.getSolanaWallet(any()))
            .thenThrow(PrivyWalletException("No Solana wallet found", 404))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(
                """{"recipientAddress":"RecipientWallet222","mintAddress":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"10500000","decimals":6}""",
            )
            .`when`()
            .post("/send/solana")
            .then()
            .statusCode(502)
    }

    @Test
    fun `sendSolana should return 400 when amount is invalid`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "SenderWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        whenever(solanaService.buildSplTransferTransaction(any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("Invalid recipient address"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(
                """{"recipientAddress":"bad-address","mintAddress":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","amount":"10500000","decimals":6}""",
            )
            .`when`()
            .post("/send/solana")
            .then()
            .statusCode(400)
    }

    // ---- /send/submit ----

    @Test
    fun `submit should return 200 with signature`() {
        whenever(solanaService.submit(any(), any())).thenReturn("sendSig789")

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body("""{"signedTransaction":"fullysignedtxbase64=="}""")
                .`when`()
                .post("/send/submit")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SendSolanaResponse::class.java)

        assertEquals("sendSig789", response.signature)
    }

    @Test
    fun `submit should return 401 without token`() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""{"signedTransaction":"fullysignedtxbase64=="}""")
            .`when`()
            .post("/send/submit")
            .then()
            .statusCode(401)
    }

    @Test
    fun `submit should return 502 with RPC message when preflight fails`() {
        whenever(solanaService.submit(any(), any()))
            .thenThrow(
                IllegalStateException("Helius RPC sendTransaction failed [400]: insufficient funds"),
            )

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body("""{"signedTransaction":"fullysignedtxbase64=="}""")
            .`when`()
            .post("/send/submit")
            .then()
            .statusCode(502)
    }

    @Test
    fun `submit should return 502 when Solana submit fails`() {
        whenever(solanaService.submit(any(), any())).thenThrow(RuntimeException("RPC error"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body("""{"signedTransaction":"fullysignedtxbase64=="}""")
            .`when`()
            .post("/send/submit")
            .then()
            .statusCode(502)
    }
}
