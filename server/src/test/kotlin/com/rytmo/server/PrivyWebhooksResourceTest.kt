package com.rytmo.server

import com.rytmo.server.test.PrivyWebhookTestProfile
import com.rytmo.server.utils.PrivyWebhookSignatureUtil
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(PrivyWebhookTestProfile::class)
class PrivyWebhooksResourceTest {
    private val validPayload =
        """{"type":"user.created","message":{"id":"did:privy:abc123"},"idempotency_key":"idem_1"}"""

    @Test
    fun `POST should return 200 with valid signature`() {
        val (svixId, svixTimestamp, svixSignature) =
            PrivyWebhookSignatureUtil.createHeaders(
                payload = validPayload,
                secretBytes = PrivyWebhookTestProfile.webhookSecretBytes,
            )

        given()
            .contentType(ContentType.JSON)
            .header("svix-id", svixId)
            .header("svix-timestamp", svixTimestamp)
            .header("svix-signature", svixSignature)
            .body(validPayload)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(200)
    }

    @Test
    fun `POST should return 401 when all Svix headers are missing`() {
        given()
            .contentType(ContentType.JSON)
            .body(validPayload)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(401)
    }

    @Test
    fun `POST should return 401 when svix-timestamp is missing`() {
        given()
            .contentType(ContentType.JSON)
            .header("svix-id", "msg_test123")
            .header("svix-signature", "v1,placeholder")
            .body(validPayload)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(401)
    }

    @Test
    fun `POST should return 401 when svix-signature is missing`() {
        given()
            .contentType(ContentType.JSON)
            .header("svix-id", "msg_test123")
            .header("svix-timestamp", (System.currentTimeMillis() / 1000).toString())
            .body(validPayload)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(401)
    }

    @Test
    fun `POST should return 401 with invalid signature`() {
        given()
            .contentType(ContentType.JSON)
            .header("svix-id", "msg_test123")
            .header("svix-timestamp", (System.currentTimeMillis() / 1000).toString())
            .header("svix-signature", "v1,invalidsignature==")
            .body(validPayload)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(401)
    }

    @Test
    fun `POST should return 401 with expired timestamp`() {
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 600L
        val (svixId, _, svixSignature) =
            PrivyWebhookSignatureUtil.createHeaders(
                payload = validPayload,
                secretBytes = PrivyWebhookTestProfile.webhookSecretBytes,
                timestampSeconds = expiredTimestamp,
            )

        given()
            .contentType(ContentType.JSON)
            .header("svix-id", svixId)
            .header("svix-timestamp", expiredTimestamp.toString())
            .header("svix-signature", svixSignature)
            .body(validPayload)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(401)
    }

    @Test
    fun `POST should return 401 with tampered payload`() {
        val (svixId, svixTimestamp, svixSignature) =
            PrivyWebhookSignatureUtil.createHeaders(
                payload = validPayload,
                secretBytes = PrivyWebhookTestProfile.webhookSecretBytes,
            )

        val tampered = validPayload.replace("user.created", "user.hacked")

        given()
            .contentType(ContentType.JSON)
            .header("svix-id", svixId)
            .header("svix-timestamp", svixTimestamp)
            .header("svix-signature", svixSignature)
            .body(tampered)
            .`when`()
            .post("/webhooks/privy")
            .then()
            .statusCode(401)
    }
}
