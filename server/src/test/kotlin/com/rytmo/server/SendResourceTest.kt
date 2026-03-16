package com.rytmo.server

import com.rytmo.library.exceptions.PrivyWalletException
import com.rytmo.library.services.PrivyServerWalletService
import com.rytmo.library.services.SolanaService
import com.rytmo.library.services.SolanaWalletInfo
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

    // ---- /send/solana ----

    @Test
    fun `sendSolana should return 200 with signature`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "SenderWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        whenever(solanaService.buildUsdcTransferTransaction(any(), any(), any()))
            .thenReturn("base64tx==")
        whenever(privyServerWalletService.signAndSend(any(), any())).thenReturn("sendSig456")

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body("""{"recipientAddress":"RecipientWallet222","amountUsdc":10.5}""")
                .`when`()
                .post("/send/solana")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(SendSolanaResponse::class.java)

        assertEquals("sendSig456", response.signature)
    }

    @Test
    fun `sendSolana should return 401 without token`() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""{"recipientAddress":"RecipientWallet222","amountUsdc":10.5}""")
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
            .body("""{"recipientAddress":"RecipientWallet222","amountUsdc":10.5}""")
            .`when`()
            .post("/send/solana")
            .then()
            .statusCode(502)
    }

    @Test
    fun `sendSolana should return 400 when amount is invalid`() {
        val wallet = SolanaWalletInfo(walletId = "wallet-id-123", address = "SenderWalletAddress111")
        whenever(privyServerWalletService.getSolanaWallet(any())).thenReturn(wallet)
        whenever(solanaService.buildUsdcTransferTransaction(any(), any(), any()))
            .thenThrow(IllegalArgumentException("Invalid recipient address"))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body("""{"recipientAddress":"bad-address","amountUsdc":10.5}""")
            .`when`()
            .post("/send/solana")
            .then()
            .statusCode(400)
    }
}
