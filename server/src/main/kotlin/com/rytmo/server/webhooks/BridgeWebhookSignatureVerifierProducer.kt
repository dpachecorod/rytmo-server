package com.rytmo.server.webhooks

import com.rytmo.library.webhooks.BridgeWebhookSignatureVerifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class BridgeWebhookSignatureVerifierProducer {
    @Produces
    @ApplicationScoped
    fun createVerifier(@ConfigProperty(name = "bridge.webhook.public-key-pem") publicKeyPem: String): BridgeWebhookSignatureVerifier = BridgeWebhookSignatureVerifier(publicKeyPem)
}
