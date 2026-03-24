package com.rytmo.server

import com.rytmo.library.services.LiquidationAddressService
import com.rytmo.server.test.BridgeWebhookTestProfile
import com.rytmo.server.utils.BridgeWebhookSignatureUtil
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.MockitoConfig
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@QuarkusTest
@TestProfile(BridgeWebhookTestProfile::class)
class WebhooksResourceTest {
    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var liquidationAddressService: LiquidationAddressService

    private val validPayload =
        """
        {
            "api_version": "2024-01",
            "event_id": "evt_123",
            "event_category": "transfer",
            "event_type": "transfer.completed",
            "event_object": {"id": "txn_456"},
            "event_object_changes": null,
            "event_created_at": "2024-01-15T10:00:00Z"
        }
        """
            .trimIndent()

    private val addressCreatedPayload =
        """
        {
            "api_version": "2024-01",
            "event_id": "evt_789",
            "event_category": "address",
            "event_type": "external_account.created",
            "event_object": {"customer_id": "bridge-cust-456", "external_account_id": "ext_acct_789"},
            "event_object_changes": null,
            "event_created_at": "2024-01-15T10:00:00Z"
        }
        """
            .trimIndent()

    @Test
    fun testValidSignature() {
        val signature =
            BridgeWebhookSignatureUtil.createSignatureHeader(
                validPayload,
                BridgeWebhookTestProfile.getPrivateKey(),
            )

        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", signature)
            .body(validPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(200)
    }

    @Test
    fun testMissingSignatureHeader() {
        given()
            .contentType(ContentType.JSON)
            .body(validPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(401)
    }

    @Test
    fun testInvalidSignature() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", "t=123456789,v0=invalidsignature")
            .body(validPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(401)
    }

    @Test
    fun testExpiredTimestamp() {
        val expiredTimestamp = System.currentTimeMillis() - 700_000L
        val signature =
            BridgeWebhookSignatureUtil.createSignatureHeaderWithTimestamp(
                validPayload,
                BridgeWebhookTestProfile.getPrivateKey(),
                expiredTimestamp,
            )

        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", signature)
            .body(validPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(401)
    }

    @Test
    fun testTamperedPayload() {
        val signature =
            BridgeWebhookSignatureUtil.createSignatureHeader(
                validPayload,
                BridgeWebhookTestProfile.getPrivateKey(),
            )

        val tamperedPayload = validPayload.replace("evt_123", "evt_hacked")

        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", signature)
            .body(tamperedPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(401)
    }

    @Test
    fun `testAddressCreatedEvent should trigger liquidation address creation`() {
        val signature =
            BridgeWebhookSignatureUtil.createSignatureHeader(
                addressCreatedPayload,
                BridgeWebhookTestProfile.getPrivateKey(),
            )

        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", signature)
            .body(addressCreatedPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(200)

        verify(liquidationAddressService)
            .handleAddressCreatedEvent(
                mapOf("customer_id" to "bridge-cust-456", "external_account_id" to "ext_acct_789"),
            )
    }

    @Test
    fun `testOtherEventType should not trigger liquidation address creation`() {
        val signature =
            BridgeWebhookSignatureUtil.createSignatureHeader(
                validPayload,
                BridgeWebhookTestProfile.getPrivateKey(),
            )

        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", signature)
            .body(validPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(200)

        verify(liquidationAddressService, never()).handleAddressCreatedEvent(any())
    }

    @Test
    fun `testAddressCreatedEvent should return 200 even if service throws`() {
        whenever(liquidationAddressService.handleAddressCreatedEvent(any()))
            .thenThrow(RuntimeException("Bridge API error"))

        val signature =
            BridgeWebhookSignatureUtil.createSignatureHeader(
                addressCreatedPayload,
                BridgeWebhookTestProfile.getPrivateKey(),
            )

        given()
            .contentType(ContentType.JSON)
            .header("X-Webhook-Signature", signature)
            .body(addressCreatedPayload)
            .`when`()
            .post("/webhooks/bridge")
            .then()
            .statusCode(200)
    }
}
