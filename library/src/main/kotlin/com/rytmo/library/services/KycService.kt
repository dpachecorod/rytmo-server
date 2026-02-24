package com.rytmo.library.services

import com.rytmo.library.exceptions.ExternalAccountException
import com.rytmo.library.exceptions.KycLinkCreationException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.customers.CustomerService
import com.rytmo.models.kyc.KycLinkResponse
import com.rytmo.models.kyc.RejectionReason
import com.rytmo.models.onboarding.BridgeOnboardingStatus
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

        var existingBridgeIdentity: CustomerIdentityDynamoDbBean? = null
        try {
            existingBridgeIdentity =
                customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
        } catch (ignore: ExternalAccountException) {}

        try {
            val bridgeResponse = bridgeService.createKycLink(fullName, email)

            if (existingBridgeIdentity == null) {
                customerIdentityService.linkIdentity(
                    internalCustomerId = internalCustomerId,
                    provider = BRIDGE_PROVIDER,
                    externalId = bridgeResponse.customerId,
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
                id = bridgeResponse.id ?: "",
                kycLink = bridgeResponse.kycLink ?: "",
                kycStatus = bridgeResponse.kycStatus?.value ?: "",
                tosLink = bridgeResponse.tosLink ?: "",
                tosStatus = bridgeResponse.tosStatus?.value ?: "",
                createdAt = bridgeResponse.createdAt?.toString(),
            )
        } catch (e: BridgeApiException) {
            throw KycLinkCreationException("Failed to create KYC link via Bridge API: ${e.message}", e)
        }
    }

    fun getOnboardingStatusByExternalId(externalId: String): BridgeOnboardingStatus? {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId) ?: return null

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER) ?: return null

        logger.info("Getting KYC links for customer {}", bridgeIdentity.externalId)
        val kycLink = bridgeService.getKycLinks(bridgeIdentity.externalId)
        logger.info("KYC links for customer {}: {}", bridgeIdentity.externalId, kycLink)
        return BridgeOnboardingStatus(
            kycStatus = kycLink.kycStatus?.value ?: "unknown",
            tosStatus = kycLink.tosStatus?.value ?: "unknown",
            kycLink = kycLink.kycLink ?: "",
            tosLink = kycLink.tosLink ?: "",
            rejectionReasons =
            kycLink.rejectionReasons?.map { reason ->
                RejectionReason(
                    developerReason = reason.developerReason,
                    reason = reason.reason,
                    createdAt = reason.createdAt,
                )
            } ?: emptyList(),
            createdAt = kycLink.createdAt?.toString(),
        )
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
            logger.info("Getting KYC links for customer {}", internalCustomerId)
            val kycLink = bridgeService.getKycLinks(bridgeIdentity.externalId)
            return listOf(
                KycLinkResponse(
                    id = kycLink.id ?: "",
                    kycLink = kycLink.kycLink ?: "",
                    kycStatus = kycLink.kycStatus?.value ?: "",
                    tosLink = kycLink.tosLink ?: "",
                    tosStatus = kycLink.tosStatus?.value ?: "",
                    createdAt = kycLink.createdAt?.toString(),
                ),
            )
        } catch (e: BridgeApiException) {
            throw KycLinkCreationException("Failed to get KYC links via Bridge API: ${e.message}", e)
        }
    }
}
