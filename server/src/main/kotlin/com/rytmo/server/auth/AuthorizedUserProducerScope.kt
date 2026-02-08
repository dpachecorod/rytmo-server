package com.rytmo.server.auth

import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.ws.rs.container.ContainerRequestContext

@RequestScoped
class AuthorizedUserProducerScope {
    @Produces
    @RequestScoped
    fun authorizedUser(requestContext: ContainerRequestContext): AuthorizedUser? = requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as? AuthorizedUser
}
