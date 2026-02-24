package com.rytmo.server.auth.filters

import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.rytmo.library.authorizers.PrivyAuthorizer
import com.rytmo.library.exceptions.AuthorizationException
import com.rytmo.server.auth.PrivyProtected
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.util.*

@Provider
@PrivyProtected
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
class PrivyAuthFilterScope(@ConfigProperty(name = "privy.app-id") private val appId: String, @ConfigProperty(name = "privy.public-key-pem") private val publicKeyPem: Optional<String>) :
    ContainerRequestFilter {
    companion object {
        const val AUTHORIZED_USER_PROPERTY = "authorizedUser"
    }

    @Inject lateinit var log: Logger

    private val authorizer: PrivyAuthorizer = buildAuthorizer()

    override fun filter(requestContext: ContainerRequestContext) {
        val authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)

        var token: String? = null
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring("Bearer ".length).trim { it <= ' ' }
        }

        try {
            val authorizedUser = authorizer.authorize(token ?: "")
            requestContext.setProperty(AUTHORIZED_USER_PROPERTY, authorizedUser)
        } catch (e: AuthorizationException) {
            log.error("Failed to authenticate user: ${e.message}", e)
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED).entity("Unauthorized").build(),
            )
        }
    }

    private fun buildAuthorizer(): PrivyAuthorizer {
        val pem = publicKeyPem.orElse(null)?.trim()
        if (!pem.isNullOrBlank()) {
            val ecKey = JWK.parse(pem) as ECKey
            return PrivyAuthorizer(appId = appId, publicKey = ecKey.toECPublicKey(), keyId = ecKey.keyID)
        }
        return PrivyAuthorizer(
            appId,
            "https://auth.privy.io/api/v1/apps/$appId/jwks.json",
            expectedIssuer = "privy.io",
        )
    }
}
