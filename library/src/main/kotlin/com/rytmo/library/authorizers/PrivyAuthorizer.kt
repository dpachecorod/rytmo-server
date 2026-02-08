package com.rytmo.library.authorizers

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.rytmo.library.exceptions.AuthorizationException
import com.rytmo.library.mappers.AuthorizedUserMapper
import com.rytmo.models.auth.AuthorizedUser
import org.mapstruct.factory.Mappers
import java.net.URI
import java.security.interfaces.ECPublicKey
import kotlin.jvm.Throws

class PrivyAuthorizer(private val appId: String, private val expectedIssuer: String = "privy.io") {
    private lateinit var jwtProcessor: ConfigurableJWTProcessor<SecurityContext>
    private val mapper = Mappers.getMapper(AuthorizedUserMapper::class.java)

    /**
     * Alternate constructor for tests (or offline verification): verify ES256 JWTs using a
     * known/static public key instead of fetching JWKS.
     */
    constructor(
        appId: String,
        publicKey: ECPublicKey,
        expectedIssuer: String = "privy.io",
        keyId: String? = null,
    ) : this(
        appId = appId,
        expectedIssuer = expectedIssuer,
    ) {
        val ecJwk = ECKey.Builder(com.nimbusds.jose.jwk.Curve.P_256, publicKey).keyID(keyId).build()
        val jwkSource: JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(ecJwk))
        jwtProcessor = buildProcessor(jwkSource, appId, expectedIssuer)
    }

    constructor(
        appId: String,
        jwksUrl: String,
        expectedIssuer: String = "privy.io",
    ) : this(
        appId = appId,
        expectedIssuer = expectedIssuer,
    ) {
        val jwkSource: JWKSource<SecurityContext> =
            JWKSourceBuilder.create<SecurityContext>(URI(jwksUrl).toURL()).build()
        jwtProcessor = buildProcessor(jwkSource, appId, expectedIssuer)
    }

    @Throws(AuthorizationException::class)
    fun authorize(jwtToken: String): AuthorizedUser {
        if (jwtToken.isBlank()) throw AuthorizationException("Missing JWT token")

        try {
            val claims = jwtProcessor.process(jwtToken, null)
            return mapper.fromClaims(claims)
        } catch (e: Exception) {
            throw AuthorizationException("Invalid Privy JWT", e)
        }
    }

    private fun buildProcessor(jwkSource: JWKSource<SecurityContext>, appId: String, expectedIssuer: String): ConfigurableJWTProcessor<SecurityContext> {
        val keySelector: JWSKeySelector<SecurityContext> =
            JWSVerificationKeySelector(JWSAlgorithm.ES256, jwkSource)

        return DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = keySelector
            jwtClaimsSetVerifier = buildClaimsVerifier(appId, expectedIssuer)
        }
    }

    private fun buildClaimsVerifier(appId: String, expectedIssuer: String): DefaultJWTClaimsVerifier<SecurityContext> {
        val expected = JWTClaimsSet.Builder().issuer(expectedIssuer).audience(listOf(appId)).build()

        // Require standard JWT claim names (not your domain model field names)
        val requiredClaims = setOf("iss", "sub", "aud", "iat", "exp")
        return DefaultJWTClaimsVerifier(expected, requiredClaims)
    }
}
