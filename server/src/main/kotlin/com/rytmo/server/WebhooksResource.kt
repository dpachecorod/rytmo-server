package com.rytmo.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.rytmo.library.services.LiquidationAddressService
import com.rytmo.library.webhooks.BridgeWebhookSignatureVerifier
import com.rytmo.library.webhooks.PrivyWebhookSignatureVerifier
import com.rytmo.models.webhooks.PrivyWebhookEvent
import com.rytmo.models.webhooks.WebhookEvent
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.logging.Logger

@Path("/webhooks")
class WebhooksResource {
    @Inject lateinit var signatureVerifier: BridgeWebhookSignatureVerifier

    @Inject lateinit var privySignatureVerifier: PrivyWebhookSignatureVerifier

    @Inject lateinit var objectMapper: ObjectMapper

    @Inject lateinit var liquidationAddressService: LiquidationAddressService

    @Inject lateinit var log: Logger

    @POST
    @Path("/bridge")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", description = "Webhook processed successfully")
    fun handleBridgeWebhook(@HeaderParam("X-Webhook-Signature") signatureHeader: String?, payload: String): Response {
        if (signatureHeader == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Missing X-Webhook-Signature header")
                .build()
        }

        val verificationResult = signatureVerifier.verify(payload, signatureHeader)
        if (!verificationResult.isValid) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(verificationResult.errorMessage)
                .build()
        }
        log.info("Received webhook: $payload")

        val event = objectMapper.readValue(payload, WebhookEvent::class.java)

        if (event.eventType == "external_account.created") {
            try {
                liquidationAddressService.handleAddressCreatedEvent(event.eventObject)
            } catch (e: Exception) {
                log.error("Failed to handle external_account.created webhook: ${e.message}", e)
            }
        }

        return Response.ok().build()
    }

    @POST
    @Path("/privy")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", description = "Webhook processed successfully")
    fun handlePrivyWebhook(
        @HeaderParam("svix-id") svixId: String?,
        @HeaderParam("svix-timestamp") svixTimestamp: String?,
        @HeaderParam("svix-signature") svixSignature: String?,
        payload: String,
    ): Response {
        if (svixId == null || svixTimestamp == null || svixSignature == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Missing Svix headers").build()
        }

        val verificationResult =
            privySignatureVerifier.verify(svixId, svixTimestamp, svixSignature, payload)
        if (!verificationResult.isValid) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(verificationResult.errorMessage)
                .build()
        }

        val event = objectMapper.readValue(payload, PrivyWebhookEvent::class.java)
        log.info("Received Privy webhook event: ${event.type}")

        return Response.ok().build()
    }
}
