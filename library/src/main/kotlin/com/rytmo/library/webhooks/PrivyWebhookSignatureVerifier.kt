package com.rytmo.library.webhooks

import org.slf4j.LoggerFactory
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PrivyWebhookSignatureVerifier(private val webhookSecret: String) {
    companion object {
        private const val TIMESTAMP_MAX_AGE_MS = 300_000L // 5 minutes
        private val log = LoggerFactory.getLogger(PrivyWebhookSignatureVerifier::class.java)
    }

    fun verify(svixId: String, svixTimestamp: String, svixSignature: String, body: String): SignatureVerificationResult {
        return try {
            if (!isTimestampValid(svixTimestamp)) {
                return SignatureVerificationResult(false, "Timestamp too old")
            }

            val signedContent = "$svixId.$svixTimestamp.$body"
            val secretBytes = Base64.getDecoder().decode(webhookSecret.removePrefix("whsec_"))
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
            val computedSig = Base64.getEncoder().encodeToString(mac.doFinal(signedContent.toByteArray()))

            val isValid =
                svixSignature.split(" ").any { part ->
                    part.startsWith("v1,") && part.substring(3) == computedSig
                }

            SignatureVerificationResult(isValid, if (isValid) null else "Invalid signature")
        } catch (e: Exception) {
            log.error("Privy webhook signature verification failed: {}", e.message, e)
            SignatureVerificationResult(false, "Signature verification failed: ${e.message}")
        }
    }

    private fun isTimestampValid(timestamp: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val eventTimeMs = timestamp.toLong() * 1000L
        return currentTime - eventTimeMs <= TIMESTAMP_MAX_AGE_MS
    }
}
