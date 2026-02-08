package com.rytmo.server.test

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.rytmo.server.utils.AccessTokenUtil.KEY_ID
import io.quarkus.test.junit.QuarkusTestProfile

class PrivyTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> {
        val ecJWK = ECKeyGenerator(Curve.P_256).keyID(KEY_ID).generate()

        val privateEcJwkJson = ecJWK.toJSONString()
        val publicEcJwkJson = ecJWK.toPublicJWK().toJSONString()

        return mapOf(
            "privy.public-key-pem" to publicEcJwkJson,
            "privy.private-key-pem" to privateEcJwkJson,
            "bridge.webhook.public-key-pem" to BridgeWebhookTestProfile.getPublicKeyPem(),
            "bridge.api-key" to "test-api-key",
            "bridge.base-url" to "https://test.bridge.xyz/v0",
            "dynamodb.table.customers" to "test-customers",
            "dynamodb.table.customer-identities" to "test-customer-identities",
            "pagination.encryption-key" to "test-encryption-key-32!",
        )
    }
}
