package com.rytmo.server.test

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.rytmo.server.utils.AccessTokenUtil.KEY_ID
import io.quarkus.test.junit.QuarkusTestProfile
import java.util.Base64
import javax.crypto.KeyGenerator

class PrivyWebhookTestProfile : QuarkusTestProfile {
    companion object {
        val webhookSecretBytes: ByteArray = run {
            val keyGen = KeyGenerator.getInstance("HmacSHA256")
            keyGen.generateKey().encoded
        }

        fun getWebhookSecret(): String = "whsec_${Base64.getEncoder().encodeToString(webhookSecretBytes)}"
    }

    override fun getConfigOverrides(): Map<String, String> {
        val ecJWK = ECKeyGenerator(Curve.P_256).keyID(KEY_ID).generate()

        return mapOf(
            "privy.webhook-secret" to getWebhookSecret(),
            "bridge.webhook.public-key-pem" to BridgeWebhookTestProfile.getPublicKeyPem(),
            "bridge.api-key" to "test-api-key",
            "bridge.base-url" to "https://test.bridge.xyz/v0",
            "bridge.liquidation.return-address" to "0xtest-return-address",
            "privy.public-key-pem" to ecJWK.toPublicJWK().toJSONString(),
            "privy.private-key-pem" to ecJWK.toJSONString(),
            "dynamodb.table.customers" to "test-customers",
            "dynamodb.table.customer-identities" to "test-customer-identities",
            "pagination.encryption-key" to "test-encryption-key-32!",
            "swap.sponsorship-mode" to "privy",
            "swap.fee-payer-private-key" to "",
        )
    }
}
