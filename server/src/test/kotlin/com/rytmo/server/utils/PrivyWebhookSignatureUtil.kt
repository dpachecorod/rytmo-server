package com.rytmo.server.utils

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PrivyWebhookSignatureUtil {
    fun createHeaders(svixId: String = "msg_test123", payload: String, secretBytes: ByteArray, timestampSeconds: Long = System.currentTimeMillis() / 1000): Triple<String, String, String> {
        val signedContent = "$svixId.$timestampSeconds.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val sig = Base64.getEncoder().encodeToString(mac.doFinal(signedContent.toByteArray()))
        return Triple(svixId, timestampSeconds.toString(), "v1,$sig")
    }
}
