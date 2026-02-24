package com.rytmo.library.services

import com.rytmo.library.exceptions.ExternalAccountException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.externalaccounts.CreateExternalAccountRequest
import com.rytmo.models.externalaccounts.ExternalAccountAddress
import com.rytmo.models.externalaccounts.ExternalAccountResponse

class ExternalAccountService(private val bridgeService: BridgeService, private val customerIdentityService: CustomerIdentityService) {
    companion object {
        const val BRIDGE_PROVIDER = "bridge"
    }

    fun createByExternalId(externalId: String, request: CreateExternalAccountRequest): ExternalAccountResponse {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw ExternalAccountException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw ExternalAccountException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        try {
            val bridgeResponse =
                bridgeService.createExternalAccount(
                    customerId = bridgeIdentity.externalId,
                    accountType = request.accountType,
                    accountOwnerName = request.accountOwnerName,
                    routingNumber = request.routingNumber,
                    accountNumber = request.accountNumber,
                    address = request.address,
                )
            return mapToResponse(bridgeResponse)
        } catch (e: BridgeApiException) {
            throw ExternalAccountException(
                "Failed to create external account via Bridge API: ${e.message}",
                e,
            )
        }
    }

    fun listByExternalId(externalId: String): List<ExternalAccountResponse> {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw ExternalAccountException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw ExternalAccountException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        try {
            val bridgeResponse = bridgeService.listExternalAccounts(bridgeIdentity.externalId)
            return bridgeResponse.data?.map { mapToResponse(it) } ?: emptyList()
        } catch (e: BridgeApiException) {
            throw ExternalAccountException(
                "Failed to list external accounts via Bridge API: ${e.message}",
                e,
            )
        }
    }

    private fun mapToResponse(item: com.rytmo.library.bridge.model.ExternalAccountResponse): ExternalAccountResponse = ExternalAccountResponse(
        id = item.id,
        accountOwnerName = item.accountOwnerName,
        bankName = item.bankName,
        last4 = item.last4,
        active = item.active?.toString(),
        createdAt = item.createdAt?.toString(),
        updatedAt = item.updatedAt?.toString(),
        address =
        item.address?.let {
            ExternalAccountAddress(
                streetLine1 = it.streetLine1,
                streetLine2 = it.streetLine2,
                city = it.city,
                state = it.state,
                postalCode = it.postalCode,
                country = it.country,
            )
        },
    )
}
