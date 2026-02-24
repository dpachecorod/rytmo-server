package com.rytmo.library.services

import com.rytmo.library.exceptions.BridgeCustomerException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.bridge.BridgeCustomer

class BridgeCustomerService(private val bridgeService: BridgeService, private val customerIdentityService: CustomerIdentityService) {
    companion object {
        const val BRIDGE_PROVIDER = "bridge"
    }

    fun createByExternalId(externalId: String, firstName: String, lastName: String, email: String): BridgeCustomer {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw BridgeCustomerException("Customer not found for external ID: $externalId")

        try {
            val bridgeResponse = bridgeService.createCustomer(firstName, lastName, email)

            customerIdentityService.linkIdentity(
                internalCustomerId = internalCustomerId,
                provider = BRIDGE_PROVIDER,
                externalId = bridgeResponse.id,
            )

            return BridgeCustomer(
                id = bridgeResponse.id ?: "",
                status = bridgeResponse.status?.value,
                firstName = bridgeResponse.firstName,
                lastName = bridgeResponse.lastName,
                email = bridgeResponse.email,
                createdAt = bridgeResponse.createdAt?.toString(),
            )
        } catch (e: BridgeApiException) {
            throw BridgeCustomerException("Failed to create Bridge customer: ${e.message}", e)
        }
    }
}
