package com.rytmo.server

import com.rytmo.library.exceptions.VirtualAccountException
import com.rytmo.library.services.VirtualAccountService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.virtualaccounts.CreateVirtualAccountRequest
import com.rytmo.models.virtualaccounts.VirtualAccountResponse
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse

@Path("/customers/me/virtual-accounts")
class VirtualAccountResource {
    @Inject lateinit var virtualAccountService: VirtualAccountService

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    fun createVirtualAccount(request: CreateVirtualAccountRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val virtualAccount =
                virtualAccountService.createByExternalId(
                    externalId = authorizedUser.userId,
                    request = request,
                )
            Response.status(Response.Status.CREATED).entity(virtualAccount).build()
        } catch (e: VirtualAccountException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to create virtual account"))
                    .build()
            }
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema =
                Schema(
                    type = SchemaType.ARRAY,
                    implementation = VirtualAccountResponse::class,
                ),
            ),
        ],
    )
    fun listVirtualAccounts(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val virtualAccounts = virtualAccountService.listByExternalId(authorizedUser.userId)
            Response.ok(virtualAccounts).build()
        } catch (e: VirtualAccountException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to list virtual accounts"))
                    .build()
            }
        }
    }

    @GET
    @Path("/{virtualAccountId}/activity")
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    fun getVirtualAccountActivity(@PathParam("virtualAccountId") virtualAccountId: String, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val activity =
                virtualAccountService.getActivityByExternalId(authorizedUser.userId, virtualAccountId)
            Response.ok(activity).build()
        } catch (e: VirtualAccountException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to get virtual account activity"))
                    .build()
            }
        }
    }
}
