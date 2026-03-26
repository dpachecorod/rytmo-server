package com.rytmo.server

import com.rytmo.library.exceptions.LiquidationAddressException
import com.rytmo.library.services.LiquidationAddressService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.liquidationaddresses.LiquidationAddressResponse
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse

@Path("/customers/me/liquidation-addresses")
class LiquidationAddressResource {
    @Inject lateinit var liquidationAddressService: LiquidationAddressService

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Liquidation addresses for current customer",
        content =
        [
            Content(
                mediaType = "application/json",
                schema =
                Schema(
                    type = SchemaType.ARRAY,
                    implementation = LiquidationAddressResponse::class,
                ),
            ),
        ],
    )
    fun listLiquidationAddresses(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val addresses = liquidationAddressService.listByExternalId(authorizedUser.userId)
            Response.ok(addresses).build()
        } catch (e: LiquidationAddressException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to list liquidation addresses"))
                    .build()
            }
        }
    }
}
