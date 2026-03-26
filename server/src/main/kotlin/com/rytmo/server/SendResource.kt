package com.rytmo.server

import com.rytmo.library.exceptions.PrivyWalletException
import com.rytmo.library.services.PrivyServerWalletService
import com.rytmo.library.services.SolanaService
import com.rytmo.library.services.SwapSponsorService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.send.SendSolanaPartialResponse
import com.rytmo.models.send.SendSolanaRequest
import com.rytmo.models.send.SendSolanaResponse
import com.rytmo.models.swap.SwapSubmitRequest
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
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
import org.slf4j.LoggerFactory

@Path("/send")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SendResource {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Inject lateinit var privyServerWalletService: PrivyServerWalletService

    @Inject lateinit var solanaService: SolanaService

    @Inject lateinit var swapSponsorService: SwapSponsorService

    @POST
    @Path("/solana")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Partially signed transfer transaction ready for client signing",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SendSolanaPartialResponse::class),
            ),
        ],
    )
    fun sendSolana(request: SendSolanaRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser
        return try {
            val wallet = privyServerWalletService.getSolanaWallet(authorizedUser.userId)
            val txBase64 =
                solanaService.buildSplTransferTransaction(
                    fromAddress = wallet.address,
                    toAddress = request.recipientAddress,
                    mintAddress = request.mintAddress,
                    amount = request.amount.toLong(),
                    decimals = request.decimals,
                )
            val partialTx = swapSponsorService.prepare(txBase64)
            Response.ok(SendSolanaPartialResponse(partialTx)).build()
        } catch (e: PrivyWalletException) {
            log.error("Privy wallet error [${e.statusCode}]: ${e.message}")
            Response.status(Response.Status.BAD_GATEWAY).entity(mapOf("error" to "Wallet error")).build()
        } catch (e: IllegalArgumentException) {
            log.error("Invalid send request: ${e.message}")
            Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to e.message)).build()
        }
    }

    @POST
    @Path("/submit")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Transaction submitted, returns signature",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SendSolanaResponse::class),
            ),
        ],
    )
    fun submit(request: SwapSubmitRequest, @Context requestContext: ContainerRequestContext): Response = try {
        val signature = solanaService.submit(request.signedTransaction, skipPreflight = false)
        log.info("Send submit success — signature={}", signature)
        Response.ok(SendSolanaResponse(signature)).build()
    } catch (e: IllegalStateException) {
        log.error("Send submit failed — RPC error: ${e.message}")
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to (e.message ?: "Send submission failed")))
            .build()
    } catch (e: Exception) {
        log.error("Send submit failed: ${e.message}", e)
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Send submission failed"))
            .build()
    }
}
