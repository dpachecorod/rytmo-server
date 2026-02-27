package com.rytmo.library.services

import com.rytmo.library.exceptions.CardAccountException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.cardaccounts.CardAccountResponse
import com.rytmo.models.cardaccounts.CardBalancesResponse
import com.rytmo.models.cardaccounts.CardDetailsResponse
import com.rytmo.models.cardaccounts.CardFundingInstructionsResponse
import com.rytmo.models.cardaccounts.CardTransactionResponse
import com.rytmo.models.cardaccounts.CardholderNameResponse
import com.rytmo.models.cardaccounts.CreateCardAccountRequest
import com.rytmo.models.cardaccounts.PaginatedCardTransactionsResponse
import org.slf4j.LoggerFactory

class CardAccountService(private val bridgeService: BridgeService, private val customerIdentityService: CustomerIdentityService) {
    companion object {
        const val BRIDGE_PROVIDER = "bridge"
        val log = LoggerFactory.getLogger(this::class.java.name)
    }

    fun provisionByExternalId(externalId: String, request: CreateCardAccountRequest): CardAccountResponse {
        val bridgeCustomerId = resolveBridgeCustomerId(externalId)

        try {
            val bridgeResponse =
                bridgeService.provisionCardAccount(
                    customerId = bridgeCustomerId,
                    currency = request.currency,
                    chain = request.chain,
                    walletAddress = request.walletAddress,
                )
            return mapToResponse(bridgeResponse)
        } catch (e: BridgeApiException) {
            log.error("Failed to provision card account: ${e.message}", e)
            throw CardAccountException("Failed to provision card account via Bridge API: ${e.message}", e)
        }
    }

    fun listByExternalId(externalId: String): List<CardAccountResponse> {
        val bridgeCustomerId = resolveBridgeCustomerId(externalId)

        try {
            val bridgeResponse = bridgeService.listCardAccounts(bridgeCustomerId)
            return bridgeResponse.data?.map { mapToResponse(it) } ?: emptyList()
        } catch (e: BridgeApiException) {
            throw CardAccountException("Failed to list card accounts via Bridge API: ${e.message}", e)
        }
    }

    fun getTransactionsByExternalId(
        externalId: String,
        cardAccountId: String,
        limit: Int? = null,
        startingTime: String? = null,
        endingTime: String? = null,
        pageSize: String? = null,
        page: String? = null,
        status: List<String>? = null,
        paginationToken: String? = null,
        categoryFamily: String? = null,
    ): PaginatedCardTransactionsResponse {
        val bridgeCustomerId = resolveBridgeCustomerId(externalId)

        try {
            val bridgeResponse =
                bridgeService.getCardTransactions(
                    bridgeCustomerId,
                    cardAccountId,
                    limit,
                    startingTime,
                    endingTime,
                    pageSize,
                    page,
                    status,
                    paginationToken,
                    categoryFamily,
                )
            return PaginatedCardTransactionsResponse(
                page = bridgeResponse.page,
                paginationToken = bridgeResponse.paginationToken,
                count = bridgeResponse.count,
                totalPages = bridgeResponse.totalPages,
                totalCount = bridgeResponse.totalCount,
                data = bridgeResponse.data?.map { mapToTransaction(it) } ?: emptyList(),
            )
        } catch (e: BridgeApiException) {
            throw CardAccountException("Failed to get card transactions via Bridge API: ${e.message}", e)
        }
    }

    private fun resolveBridgeCustomerId(externalId: String): String {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw CardAccountException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw CardAccountException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        return bridgeIdentity.externalId
    }

    private fun mapToResponse(item: com.rytmo.library.bridge.model.CardAccount): CardAccountResponse = CardAccountResponse(
        id = item.id ?: "",
        status = item.status?.value,
        cardholderName =
        item.cardholderName?.let {
            CardholderNameResponse(
                firstName = it.firstName,
                middleName = it.middleName,
                lastName = it.lastName,
            )
        },
        cardDetails =
        item.cardDetails?.let {
            CardDetailsResponse(
                last4 = it.last4,
                expiry = it.expiry,
                bin = it.bin,
                pinStatus = it.pinStatus?.value,
            )
        },
        balances =
        item.balances?.let {
            CardBalancesResponse(
                available = it.available?.amount,
                hold = it.hold?.amount,
            )
        },
        fundingInstructions =
        item.fundingInstructions?.let {
            CardFundingInstructionsResponse(
                currency = it.currency?.value,
                chain = it.chain?.value,
                address = it.address,
            )
        },
    )

    private fun mapToTransaction(item: com.rytmo.library.bridge.model.CardTransaction): CardTransactionResponse = CardTransactionResponse(
        id = item.id ?: "",
        cardAccountId = item.cardAccountId,
        category = item.category?.value,
        amount = item.amount,
        billingAmount = item.billingAmount,
        currency = item.currency?.value,
        merchantName = item.merchantName,
        merchantLocation = item.merchantLocation,
        merchantCategoryCode = item.merchantCategoryCode,
        description = item.description,
        postedAt = item.postedAt,
        authorizedAt = item.authorizedAt,
        status = item.status?.value,
    )
}
