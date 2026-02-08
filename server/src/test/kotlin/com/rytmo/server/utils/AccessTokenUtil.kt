package com.rytmo.server.utils

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.util.Date

object AccessTokenUtil {
    const val KEY_ID = "rytmo"

    fun generateMockAccessToken(privateKeyPem: String, appId: String): String {
        val keyMaterial = privateKeyPem.trim()
        val jwk = JWK.parse(keyMaterial)
        val ecKey = jwk as ECKey
        // 1. Create the header
        val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY_ID).build()

        // 2. Create claims similar to a Privy token
        val claims =
            JWTClaimsSet.Builder()
                .issuer("privy.io")
                .audience(appId)
                .subject("did:privy:tester")
                .claim("sid", "session-id-123")
                .issueTime(Date())
                // 1h expiry
                .expirationTime(Date(Date().time + 3600000))
                .build()

        // 3. Create and sign the JWT
        val signedJWT = SignedJWT(header, claims)
        val signer = ECDSASigner(ecKey.toECPrivateKey())
        signedJWT.sign(signer)

        return signedJWT.serialize()
    }
}
