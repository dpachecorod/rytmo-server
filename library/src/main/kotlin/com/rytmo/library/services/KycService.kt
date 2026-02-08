package com.rytmo.library.services

import com.rytmo.library.exceptions.KycLinkCreationException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.customers.CustomerService
import com.rytmo.models.kyc.KycLinkResponse
import org.slf4j.LoggerFactory

class KycService(private val bridgeService: BridgeService, private val customerIdentityService: CustomerIdentityService, private val customerService: CustomerService) {
    companion object {
        const val BRIDGE_PROVIDER = "bridge"
        private val logger = LoggerFactory.getLogger(KycService::class.java)
    }

    fun createKycLinkByExternalId(externalId: String, fullName: String, email: String): KycLinkResponse {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw KycLinkCreationException("Customer not found for external ID: $externalId")

        return createKycLink(internalCustomerId, fullName, email)
    }

    fun createKycLink(internalCustomerId: String, fullName: String, email: String): KycLinkResponse {
        val customer =
            customerService.get(internalCustomerId)
                ?: throw KycLinkCreationException("Customer not found: $internalCustomerId")

        val existingBridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)

        try {
            val bridgeResponse = bridgeService.createKycLink(fullName, email)

            if (existingBridgeIdentity == null) {
                customerIdentityService.linkIdentity(
                    internalCustomerId = internalCustomerId,
                    provider = BRIDGE_PROVIDER,
                    externalId = bridgeResponse.id,
                )
            } else if (existingBridgeIdentity.externalId != bridgeResponse.id) {
                logger.warn(
                    "Bridge customer ID changed for customer {}: {} -> {}",
                    internalCustomerId,
                    existingBridgeIdentity.externalId,
                    bridgeResponse.id,
                )
            }

            return KycLinkResponse(
                id = bridgeResponse.id,
                kycLink = bridgeResponse.kycLink,
                kycStatus = bridgeResponse.kycStatus,
                tosLink = bridgeResponse.tosLink,
                tosStatus = bridgeResponse.tosStatus,
                createdAt = bridgeResponse.createdAt,
            )
        } catch (e: BridgeApiException) {
            throw KycLinkCreationException("Failed to create KYC link via Bridge API: ${e.message}", e)
        }
    }

    fun listKycLinksByExternalId(externalId: String): List<KycLinkResponse> {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw KycLinkCreationException("Customer not found for external ID: $externalId")

        return listKycLinks(internalCustomerId)
    }

    fun listKycLinks(internalCustomerId: String): List<KycLinkResponse> {
        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw KycLinkCreationException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        try {
            val bridgeResponse = bridgeService.getKycLinks(bridgeIdentity.externalId)
            return bridgeResponse.data?.map { item ->
                KycLinkResponse(
                    id = item.id,
                    kycLink = item.kycLink,
                    kycStatus = item.kycStatus,
                    tosLink = item.tosLink,
                    tosStatus = item.tosStatus,
                    createdAt = item.createdAt,
                )
            } ?: emptyList()
        } catch (e: BridgeApiException) {
            throw KycLinkCreationException("Failed to get KYC links via Bridge API: ${e.message}", e)
        }
    }
}
