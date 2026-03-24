package com.rytmo.library.webhooks

import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class SignatureVerificationResult(val isValid: Boolean, val errorMessage: String?)

class BridgeWebhookSignatureVerifier(private val publicKeyPem: String) {
    companion object {
        private const val TIMESTAMP_MAX_AGE_MS = 600_000L
        private val log = LoggerFactory.getLogger(BridgeWebhookSignatureVerifier::class.java)
    }

    fun verify(payload: String, signatureHeader: String): SignatureVerificationResult {
        return try {
            val (timestamp, signature) =
                parseSignatureHeader(signatureHeader)
                    ?: return SignatureVerificationResult(false, "Missing timestamp or signature")

            if (!isTimestampValid(timestamp)) {
                return SignatureVerificationResult(false, "Timestamp too old")
            }

            val signedPayload = "$timestamp.$payload"
            val publicKey = parsePublicKey()
            val isValid = verifySignature(signedPayload, signature, publicKey)

            SignatureVerificationResult(isValid, if (isValid) null else "Invalid signature")
        } catch (e: Exception) {
            log.error(
                "Signature verification failed — header: {}, error: {}",
                signatureHeader,
                e.message,
                e,
            )
            SignatureVerificationResult(false, "Signature verification failed: ${e.message}")
        }
    }

    private fun parseSignatureHeader(header: String): Pair<String, String>? {
        var timestamp: String? = null
        var signature: String? = null

        for (part in header.split(",")) {
            when {
                part.startsWith("t=") -> timestamp = part.substring(2)
                part.startsWith("v0=") -> signature = part.substring(3)
            }
        }

        return if (timestamp != null && signature != null) {
            Pair(timestamp, signature)
        } else {
            null
        }
    }

    private fun isTimestampValid(timestamp: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val eventTime = timestamp.toLong()
        return currentTime - eventTime <= TIMESTAMP_MAX_AGE_MS
    }

    private fun parsePublicKey(): PublicKey {
        val publicKeyContent =
            publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\n", "")
                .replace("\\s".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(publicKeyContent)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }

    private fun verifySignature(signedPayload: String, signature: String, publicKey: PublicKey): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val dataDigest = digest.digest(signedPayload.toByteArray())

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initVerify(publicKey)
        sig.update(dataDigest)

        val signatureBytes = Base64.getDecoder().decode(signature)
        return sig.verify(signatureBytes)
    }
}
