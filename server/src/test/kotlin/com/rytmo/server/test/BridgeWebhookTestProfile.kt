package com.rytmo.server.test

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.rytmo.server.utils.AccessTokenUtil.KEY_ID
import io.quarkus.test.junit.QuarkusTestProfile
import java.security.KeyPairGenerator
import java.util.Base64

class BridgeWebhookTestProfile : QuarkusTestProfile {
    companion object {
        val keyPair = generateRsaKeyPair()

        private fun generateRsaKeyPair(): java.security.KeyPair {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            return keyGen.generateKeyPair()
        }

        fun getPublicKeyPem(): String {
            val encoded = Base64.getEncoder().encodeToString(keyPair.public.encoded)
            return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
        }

        fun getPrivateKey() = keyPair.private
    }

    override fun getConfigOverrides(): Map<String, String> {
        val ecJWK = ECKeyGenerator(Curve.P_256).keyID(KEY_ID).generate()
        val privateEcJwkJson = ecJWK.toJSONString()
        val publicEcJwkJson = ecJWK.toPublicJWK().toJSONString()

        return mapOf(
            "bridge.webhook.public-key-pem" to getPublicKeyPem(),
            "bridge.api-key" to "test-api-key",
            "bridge.base-url" to "https://test.bridge.xyz/v0",
            "privy.public-key-pem" to publicEcJwkJson,
            "privy.private-key-pem" to privateEcJwkJson,
            "dynamodb.table.customers" to "test-customers",
            "dynamodb.table.customer-identities" to "test-customer-identities",
            "pagination.encryption-key" to "test-encryption-key-32!",
        )
    }
}
