package com.rytmo.server

import com.rytmo.library.exceptions.CardAccountException
import com.rytmo.library.services.CardAccountService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.cardaccounts.CardAccountResponse
import com.rytmo.models.cardaccounts.CreateCardAccountRequest
import com.rytmo.models.cardaccounts.PaginatedCardTransactionsResponse
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
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

@Path("/customers/me/card-accounts")
class CardAccountResource {
    @Inject lateinit var cardAccountService: CardAccountService

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    fun provisionCardAccount(request: CreateCardAccountRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val cardAccount =
                cardAccountService.provisionByExternalId(
                    externalId = authorizedUser.userId,
                    request = request,
                )
            Response.status(Response.Status.CREATED).entity(cardAccount).build()
        } catch (e: CardAccountException) {
            if (e.message.contains("not found")) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to provision card account"))
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
                    implementation = CardAccountResponse::class,
                ),
            ),
        ],
    )
    fun listCardAccounts(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val cardAccounts = cardAccountService.listByExternalId(authorizedUser.userId)
            Response.ok(cardAccounts).build()
        } catch (e: CardAccountException) {
            if (e.message.contains("not found")) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to list card accounts"))
                    .build()
            }
        }
    }

    @GET
    @Path("/{cardAccountId}/transactions")
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = PaginatedCardTransactionsResponse::class),
            ),
        ],
    )
    fun getCardTransactions(
        @PathParam("cardAccountId") cardAccountId: String,
        @QueryParam("limit") limit: Int?,
        @QueryParam("starting_time") startingTime: String?,
        @QueryParam("ending_time") endingTime: String?,
        @QueryParam("page_size") pageSize: String?,
        @QueryParam("page") page: String?,
        @QueryParam("status") status: List<String>?,
        @QueryParam("pagination_token") paginationToken: String?,
        @QueryParam("category_family") categoryFamily: String?,
        @Context requestContext: ContainerRequestContext,
    ): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val transactions =
                cardAccountService.getTransactionsByExternalId(
                    externalId = authorizedUser.userId,
                    cardAccountId = cardAccountId,
                    limit = limit,
                    startingTime = startingTime,
                    endingTime = endingTime,
                    pageSize = pageSize,
                    page = page,
                    status = status,
                    paginationToken = paginationToken,
                    categoryFamily = categoryFamily,
                )
            Response.ok(transactions).build()
        } catch (e: CardAccountException) {
            if (e.message.contains("not found")) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to get card transactions"))
                    .build()
            }
        }
    }
}
