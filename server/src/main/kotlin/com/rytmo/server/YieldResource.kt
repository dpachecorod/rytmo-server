package com.rytmo.server

import com.rytmo.library.exceptions.DeframeException
import com.rytmo.library.services.DeframeService
import com.rytmo.models.yield.YieldBytecodeResponse
import com.rytmo.models.yield.YieldQuoteResponse
import com.rytmo.models.yield.YieldStrategiesResponse
import com.rytmo.models.yield.YieldStrategy
import com.rytmo.server.auth.PrivyProtected
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.slf4j.LoggerFactory

@Path("/yield")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class YieldResource {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Inject lateinit var deframeService: DeframeService

    @GET
    @Path("/strategies")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Available yield strategies",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = YieldStrategiesResponse::class),
            ),
        ],
    )
    fun listStrategies(@Context requestContext: ContainerRequestContext, @QueryParam("page") page: Int?, @QueryParam("limit") limit: Int?): Response = try {
        val result = deframeService.listStrategies(page ?: 1, limit ?: 10)
        Response.ok(result).build()
    } catch (e: DeframeException) {
        log.error("DFrame listStrategies failed [${e.statusCode}]: ${e.message}")
        Response.status(Response.Status.BAD_GATEWAY).entity(mapOf("error" to e.message)).build()
    }

    @GET
    @Path("/strategies/{id}")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Yield strategy details",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = YieldStrategy::class),
            ),
        ],
    )
    fun getStrategy(@Context requestContext: ContainerRequestContext, @PathParam("id") id: String): Response = try {
        val result = deframeService.getStrategy(id)
        Response.ok(result).build()
    } catch (e: DeframeException) {
        log.error("DFrame getStrategy failed [${e.statusCode}]: ${e.message}")
        Response.status(Response.Status.BAD_GATEWAY).entity(mapOf("error" to e.message)).build()
    }

    @GET
    @Path("/strategies/{id}/quote")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Quote for a yield strategy deposit",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = YieldQuoteResponse::class),
            ),
        ],
    )
    fun getQuote(@Context requestContext: ContainerRequestContext, @PathParam("id") id: String, @QueryParam("amount") amount: String, @QueryParam("walletAddress") walletAddress: String): Response =
        try {
            val result = deframeService.getQuote(id, amount, walletAddress)
            Response.ok(result).build()
        } catch (e: DeframeException) {
            log.error("DFrame getQuote failed [${e.statusCode}]: ${e.message}")
            Response.status(Response.Status.BAD_GATEWAY).entity(mapOf("error" to e.message)).build()
        }

    @GET
    @Path("/strategies/{id}/bytecode")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Bytecode for a yield strategy transaction",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = YieldBytecodeResponse::class),
            ),
        ],
    )
    fun getBytecode(
        @Context requestContext: ContainerRequestContext,
        @PathParam("id") id: String,
        @QueryParam("action") action: String,
        @QueryParam("amount") amount: String,
        @QueryParam("walletAddress") walletAddress: String,
        @QueryParam("fromToken") fromToken: String?,
    ): Response = try {
        val result = deframeService.getBytecode(id, action, amount, walletAddress, fromToken)
        Response.ok(result).build()
    } catch (e: DeframeException) {
        log.error("DFrame getBytecode failed [${e.statusCode}]: ${e.message}")
        Response.status(Response.Status.BAD_GATEWAY).entity(mapOf("error" to e.message)).build()
    }
}
