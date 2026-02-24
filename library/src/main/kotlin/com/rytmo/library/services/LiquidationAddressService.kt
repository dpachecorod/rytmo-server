package com.rytmo.library.services

import com.rytmo.library.exceptions.LiquidationAddressException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.liquidationaddresses.LiquidationAddressResponse

class LiquidationAddressService(private val bridgeService: BridgeService, private val returnAddress: String, private val customerIdentityService: CustomerIdentityService) {
    companion object {
        const val CURRENCY = "usdc"
        const val CHAIN = "base"
        const val DESTINATION_PAYMENT_RAIL = "ach"
        const val DESTINATION_CURRENCY = "usd"
        const val BRIDGE_PROVIDER = "bridge"
    }

    fun listByExternalId(externalId: String): List<LiquidationAddressResponse> {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw LiquidationAddressException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw LiquidationAddressException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        try {
            val response = bridgeService.listLiquidationAddresses(bridgeIdentity.externalId)
            return response.data?.map { item ->
                LiquidationAddressResponse(
                    id = item.id,
                    currency = item.currency?.value,
                    chain = item.chain?.value,
                    externalAccountId = item.externalAccountId,
                    address = item.address,
                    createdAt = item.createdAt?.toString(),
                    updatedAt = item.updatedAt?.toString(),
                )
            } ?: emptyList()
        } catch (e: BridgeApiException) {
            throw LiquidationAddressException(
                "Failed to list liquidation addresses via Bridge API: ${e.message}",
                e,
            )
        }
    }

    fun handleAddressCreatedEvent(eventObject: Map<String, Any>) {
        val customerId =
            eventObject["customer_id"] as? String
                ?: throw LiquidationAddressException(
                    "Missing customer_id in event_address.created event",
                )

        val externalAccountId =
            eventObject["external_account_id"] as? String
                ?: throw LiquidationAddressException(
                    "Missing external_account_id in event_address.created event",
                )

        try {
            bridgeService.createLiquidationAddress(
                customerId = customerId,
                currency = CURRENCY,
                chain = CHAIN,
                externalAccountId = externalAccountId,
                destinationPaymentRail = DESTINATION_PAYMENT_RAIL,
                destinationCurrency = DESTINATION_CURRENCY,
                returnAddress = returnAddress,
            )
        } catch (e: BridgeApiException) {
            throw LiquidationAddressException(
                "Failed to create liquidation address via Bridge API: ${e.message}",
                e,
            )
        }
    }
}
