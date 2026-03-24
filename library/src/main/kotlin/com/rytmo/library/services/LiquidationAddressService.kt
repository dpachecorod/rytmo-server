package com.rytmo.library.services

import com.rytmo.library.exceptions.LiquidationAddressException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.liquidationaddresses.LiquidationAddressResponse
import org.slf4j.LoggerFactory

class LiquidationAddressService(private val bridgeService: BridgeService, private val returnAddress: String, private val customerIdentityService: CustomerIdentityService) {
    companion object {
        const val CURRENCY = "usdc"
        const val CHAIN = "solana"
        const val BRIDGE_PROVIDER = "bridge"
        val log = LoggerFactory.getLogger(LiquidationAddressService::class.java)

        fun destinationPaymentRail(currency: String?) = if (currency == "mxn") "spei" else "ach"

        fun destinationCurrency(currency: String?) = if (currency == "mxn") "mxn" else "usd"
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
                    "Missing customer_id in external_account.created event",
                )

        val externalAccountId =
            eventObject["id"] as? String
                ?: throw LiquidationAddressException(
                    "Missing id in external_account.created event",
                )

        try {
            val externalAccount = bridgeService.getExternalAccount(customerId, externalAccountId)
            val accountCurrency = externalAccount.currency?.toString()?.lowercase()
            bridgeService.createLiquidationAddress(
                customerId = customerId,
                currency = CURRENCY,
                chain = CHAIN,
                externalAccountId = externalAccountId,
                destinationPaymentRail = destinationPaymentRail(accountCurrency),
                destinationCurrency = destinationCurrency(accountCurrency),
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
