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
            val accounts = bridgeService.listExternalAccounts(bridgeIdentity.externalId)
            val liquidationAddressByAccountId =
                bridgeService.listLiquidationAddresses(bridgeIdentity.externalId).data?.associate {
                    it.externalAccountId to it.address
                } ?: emptyMap()
            return accounts.data?.map { mapToResponse(it, liquidationAddressByAccountId[it.id]) }
                ?: emptyList()
        } catch (e: BridgeApiException) {
            throw ExternalAccountException(
                "Failed to list external accounts via Bridge API: ${e.message}",
                e,
            )
        }
    }

    private fun mapToResponse(item: com.rytmo.library.bridge.model.ExternalAccountResponse, liquidationAddress: String? = null): ExternalAccountResponse {
        @Suppress("UNCHECKED_CAST")
        val clabeMap = item.clabe as? Map<String, Any?>
        val last4 = clabeMap?.get("last_4") as? String ?: item.last4
        return ExternalAccountResponse(
            id = item.id,
            accountOwnerName = item.accountOwnerName,
            bankName = item.bankName,
            last4 = last4,
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
            liquidationAddress = liquidationAddress,
        )
    }
}
