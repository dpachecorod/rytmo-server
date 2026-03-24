package com.rytmo.server

import com.rytmo.library.exceptions.DFlowException
import com.rytmo.library.exceptions.PrivyWalletException
import com.rytmo.library.services.DFlowService
import com.rytmo.library.services.HeliusService
import com.rytmo.library.services.JupiterService
import com.rytmo.library.services.PrivyServerWalletService
import com.rytmo.library.services.SolanaService
import com.rytmo.library.services.SwapSponsorService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.swap.OutputToken
import com.rytmo.models.swap.SwapExecuteRequest
import com.rytmo.models.swap.SwapExecuteResponse
import com.rytmo.models.swap.SwapHistoryResponse
import com.rytmo.models.swap.SwapPrepareResponse
import com.rytmo.models.swap.SwapQuoteResponse
import com.rytmo.models.swap.SwapStatusResponse
import com.rytmo.models.swap.SwapSubmitRequest
import com.rytmo.models.swap.TokenBalance
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.slf4j.LoggerFactory

@Path("/swap")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SwapResource {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Inject lateinit var dFlowService: DFlowService

    @Inject lateinit var heliusService: HeliusService

    @Inject lateinit var jupiterService: JupiterService

    @Inject lateinit var privyServerWalletService: PrivyServerWalletService

    @Inject lateinit var swapSponsorService: SwapSponsorService

    @Inject lateinit var solanaService: SolanaService

    @POST
    @Path("/execute")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SwapPrepareResponse::class),
            ),
        ],
    )
    fun execute(request: SwapExecuteRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser
        return try {
            val wallet = privyServerWalletService.getSolanaWallet(authorizedUser.userId)
            log.info(
                "Swap execute — userId={} walletAddress={} inputMint={} outputMint={} amount={}",
                authorizedUser.userId,
                wallet.address,
                request.inputMint,
                request.outputMint,
                request.amount,
            )
            val quote =
                dFlowService.getOrderQuote(
                    userPublicKey = wallet.address,
                    inputMint = request.inputMint,
                    outputMint = request.outputMint,
                    amount = request.amount,
                    slippageBps = request.slippageBps ?: "auto",
                )
            log.info(
                "DFlow quote received — walletAddress={} inAmount={} outAmount={} mode={}",
                wallet.address,
                quote.inAmount,
                quote.outAmount,
                quote.executionMode,
            )
            val partialTx = swapSponsorService.prepare(quote.transaction)
            log.info("Swap prepare success — walletAddress={}", wallet.address)
            Response.ok(SwapPrepareResponse(partialTx)).build()
        } catch (e: DFlowException) {
            log.error("DFlow execute failed [${e.statusCode}]: ${e.message}")
            Response.status(Response.Status.BAD_GATEWAY)
                .entity(mapOf("error" to "Swap execution failed"))
                .build()
        } catch (e: PrivyWalletException) {
            log.error("Privy wallet error [${e.statusCode}]: ${e.message}")
            Response.status(Response.Status.BAD_GATEWAY).entity(mapOf("error" to "Wallet error")).build()
        } catch (e: Exception) {
            log.error("Swap execute failed: ${e.message}", e)
            Response.status(Response.Status.BAD_GATEWAY)
                .entity(mapOf("error" to "Swap execution failed"))
                .build()
        }
    }

    @POST
    @Path("/submit")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SwapExecuteResponse::class),
            ),
        ],
    )
    fun submit(request: SwapSubmitRequest, @Context requestContext: ContainerRequestContext): Response = try {
        val signature = solanaService.submit(request.signedTransaction)
        log.info("Swap submit success — signature={}", signature)
        Response.ok(SwapExecuteResponse(signature)).build()
    } catch (e: Exception) {
        log.error("Swap submit failed: ${e.message}", e)
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Swap submission failed"))
            .build()
    }

    @GET
    @Path("/quote")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SwapQuoteResponse::class),
            ),
        ],
    )
    fun getQuote(
        @Context requestContext: ContainerRequestContext,
        @QueryParam("userPublicKey") userPublicKey: String,
        @QueryParam("inputMint") inputMint: String,
        @QueryParam("outputMint") outputMint: String,
        @QueryParam("amount") amount: String,
        @QueryParam("slippageBps") slippageBps: String?,
    ): Response = try {
        val quote =
            dFlowService.getOrderQuote(
                userPublicKey = userPublicKey,
                inputMint = inputMint,
                outputMint = outputMint,
                amount = amount,
                slippageBps = slippageBps ?: "auto",
            )
        Response.ok(quote).build()
    } catch (e: DFlowException) {
        log.error("DFlow quote failed [${e.statusCode}]: ${e.message}")
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Failed to get swap quote"))
            .build()
    }

    @GET
    @Path("/tokens")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(type = SchemaType.ARRAY, implementation = TokenBalance::class),
            ),
        ],
    )
    fun getTokens(@Context requestContext: ContainerRequestContext, @QueryParam("walletAddress") walletAddress: String): Response = try {
        val balances = heliusService.getTokenBalances(walletAddress)
        Response.ok(balances).build()
    } catch (e: Exception) {
        log.error("Helius token balances failed: ${e.message}", e)
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Failed to get token balances"))
            .build()
    }

    @GET
    @Path("/output-tokens")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(type = SchemaType.ARRAY, implementation = OutputToken::class),
            ),
        ],
    )
    fun getOutputTokens(@Context requestContext: ContainerRequestContext): Response = try {
        val tokens = jupiterService.getOutputTokens()
        Response.ok(tokens).build()
    } catch (e: Exception) {
        log.error("Jupiter output tokens failed: ${e.message}", e)
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Failed to get output tokens"))
            .build()
    }

    @GET
    @Path("/history")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SwapHistoryResponse::class),
            ),
        ],
    )
    fun getHistory(@Context requestContext: ContainerRequestContext, @QueryParam("paginationToken") paginationToken: String?, @QueryParam("limit") limit: Int?): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser
        return try {
            val wallet = privyServerWalletService.getSolanaWallet(authorizedUser.userId)
            val history = heliusService.getSwapHistory(wallet.address, paginationToken, limit ?: 10)
            Response.ok(history).build()
        } catch (e: Exception) {
            log.error("Swap history failed: ${e.message}", e)
            Response.status(Response.Status.BAD_GATEWAY)
                .entity(mapOf("error" to "Failed to get swap history"))
                .build()
        }
    }

    @GET
    @Path("/status")
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = SwapStatusResponse::class),
            ),
        ],
    )
    fun getStatus(@Context requestContext: ContainerRequestContext, @QueryParam("signature") signature: String, @QueryParam("lastValidBlockHeight") lastValidBlockHeight: Long?): Response = try {
        val status = solanaService.getSignatureStatus(signature)
        log.debug(
            "Swap status poll — signature={} status={} error={}",
            signature,
            status.status,
            status.error,
        )

        if (status.status == "not_found") {
            log.warn(
                "Swap tx not found on chain — signature={} (likely dropped: insufficient fee payer SOL, invalid signatures, or expired blockhash)",
                signature,
            )
        } else if (status.status != "open") {
            log.info(
                "Swap status terminal — signature={} status={} error={}",
                signature,
                status.status,
                status.error,
            )
        }
        Response.ok(status).build()
    } catch (e: DFlowException) {
        log.error("DFlow status failed [${e.statusCode}]: ${e.message}")
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Failed to get swap status"))
            .build()
    } catch (e: Exception) {
        log.error("Swap status failed: ${e.message}", e)
        Response.status(Response.Status.BAD_GATEWAY)
            .entity(mapOf("error" to "Failed to get swap status"))
            .build()
    }
}
