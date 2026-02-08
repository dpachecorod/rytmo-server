package com.rytmo.server.utils

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

object BridgeWebhookSignatureUtil {
    fun createSignatureHeader(payload: String, privateKey: PrivateKey): String {
        val timestamp = System.currentTimeMillis().toString()
        val signature = sign(payload, timestamp, privateKey)
        return "t=$timestamp,v0=$signature"
    }

    fun createSignatureHeaderWithTimestamp(payload: String, privateKey: PrivateKey, timestamp: Long): String {
        val signature = sign(payload, timestamp.toString(), privateKey)
        return "t=$timestamp,v0=$signature"
    }

    private fun sign(payload: String, timestamp: String, privateKey: PrivateKey): String {
        val signedPayload = "$timestamp.$payload"

        val digest = MessageDigest.getInstance("SHA-256")
        val dataDigest = digest.digest(signedPayload.toByteArray())

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(dataDigest)

        val signatureBytes = sig.sign()
        return Base64.getEncoder().encodeToString(signatureBytes)
    }
}
