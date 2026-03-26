package com.rytmo.server

import com.rytmo.library.exceptions.DeviceTokenException
import com.rytmo.library.services.ExpoDeviceTokenService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.devicetoken.DeleteDeviceTokenRequest
import com.rytmo.models.devicetoken.DeviceTokenResponse
import com.rytmo.models.devicetoken.RegisterDeviceTokenRequest
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
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

@Path("/customers/me/device-tokens")
class DeviceTokenResource {
    @Inject lateinit var expoDeviceTokenService: ExpoDeviceTokenService

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Device token registered",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = DeviceTokenResponse::class),
            ),
        ],
    )
    fun registerDeviceToken(request: RegisterDeviceTokenRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val response =
                expoDeviceTokenService.registerByExternalId(authorizedUser.userId, request.expoToken)
            Response.ok(response).build()
        } catch (e: DeviceTokenException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to register device token"))
                    .build()
            }
        }
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(responseCode = "204", description = "Device token deleted")
    fun deleteDeviceToken(request: DeleteDeviceTokenRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            expoDeviceTokenService.deleteByExternalId(authorizedUser.userId, request.expoToken)
            Response.noContent().build()
        } catch (e: DeviceTokenException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to delete device token"))
                    .build()
            }
        }
    }
}
