package com.rytmo.server

import com.rytmo.library.exceptions.ExternalAccountException
import com.rytmo.library.services.ExternalAccountService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.externalaccounts.CreateExternalAccountRequest
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse

@Path("/customers/me/external-accounts")
class ExternalAccountResource {
    @Inject lateinit var externalAccountService: ExternalAccountService

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "201",
        description = "External account created",
        content =
        [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema =
                Schema(
                    implementation =
                    com.rytmo.models.externalaccounts.ExternalAccountResponse::class,
                ),
            ),
        ],
    )
    fun createExternalAccount(request: CreateExternalAccountRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val externalAccount =
                externalAccountService.createByExternalId(
                    externalId = authorizedUser.userId,
                    request = request,
                )
            Response.status(Response.Status.CREATED).entity(externalAccount).build()
        } catch (e: ExternalAccountException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to create external account"))
                    .build()
            }
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "List of external accounts",
        content =
        [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema =
                Schema(
                    implementation =
                    Array<
                        com.rytmo.models.externalaccounts.ExternalAccountResponse,
                        >::class,
                ),
            ),
        ],
    )
    fun listExternalAccounts(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val externalAccounts = externalAccountService.listByExternalId(authorizedUser.userId)
            Response.ok(externalAccounts).build()
        } catch (e: ExternalAccountException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to list external accounts"))
                    .build()
            }
        }
    }
}
