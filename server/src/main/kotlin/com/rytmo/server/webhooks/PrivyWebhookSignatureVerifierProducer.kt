package com.rytmo.server.webhooks

import com.rytmo.library.webhooks.PrivyWebhookSignatureVerifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class PrivyWebhookSignatureVerifierProducer {
    @Produces
    @ApplicationScoped
    fun createVerifier(@ConfigProperty(name = "privy.webhook-secret") webhookSecret: String): PrivyWebhookSignatureVerifier = PrivyWebhookSignatureVerifier(webhookSecret)
}
