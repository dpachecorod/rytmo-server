package com.rytmo.library.services

import com.rytmo.library.exceptions.VirtualAccountException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.models.virtualaccounts.CreateVirtualAccountRequest
import com.rytmo.models.virtualaccounts.SourceDepositInstructions
import com.rytmo.models.virtualaccounts.VirtualAccountActivity
import com.rytmo.models.virtualaccounts.VirtualAccountActivitySource
import com.rytmo.models.virtualaccounts.VirtualAccountDestination
import com.rytmo.models.virtualaccounts.VirtualAccountResponse
import org.slf4j.LoggerFactory

class VirtualAccountService(private val bridgeService: BridgeService, private val privyService: PrivyService, private val customerIdentityService: CustomerIdentityService) {
    companion object {
        const val BRIDGE_PROVIDER = "bridge"
        val log = LoggerFactory.getLogger(this::class.java.name)
    }

    fun createByExternalId(externalId: String, request: CreateVirtualAccountRequest): VirtualAccountResponse {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw VirtualAccountException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw VirtualAccountException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        val wallet = privyService.getWallet(request.walletId)

        log.info("Creating virtual account for customer {}", internalCustomerId)
        try {
            val bridgeResponse =
                bridgeService.createVirtualAccount(
                    customerId = bridgeIdentity.externalId,
                    sourceCurrency = request.sourceCurrency,
                    destinationCurrency = request.destinationCurrency,
                    destinationPaymentRail = request.destinationPaymentRail,
                    destinationAddress = wallet.address,
                )

            log.info("Virtual account created successfully for customer {}", internalCustomerId)
            return mapToResponse(bridgeResponse)
        } catch (e: BridgeApiException) {
            log.error("Failed to create virtual account: ${e.message}", e)
            throw VirtualAccountException(
                "Failed to create virtual account via Bridge API: ${e.message}",
                e,
            )
        }
    }

    fun listByExternalId(externalId: String): List<VirtualAccountResponse> {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw VirtualAccountException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw VirtualAccountException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        try {
            val bridgeResponse = bridgeService.listVirtualAccounts(bridgeIdentity.externalId)
            return bridgeResponse.data?.map { mapToResponse(it) } ?: emptyList()
        } catch (e: BridgeApiException) {
            throw VirtualAccountException(
                "Failed to list virtual accounts via Bridge API: ${e.message}",
                e,
            )
        }
    }

    fun getActivityByExternalId(externalId: String, virtualAccountId: String): List<VirtualAccountActivity> {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw VirtualAccountException("Customer not found for external ID: $externalId")

        val bridgeIdentity =
            customerIdentityService.getIdentity(internalCustomerId, BRIDGE_PROVIDER)
                ?: throw VirtualAccountException(
                    "Bridge identity not found for customer: $internalCustomerId",
                )

        try {
            val bridgeResponse =
                bridgeService.getVirtualAccountActivity(bridgeIdentity.externalId, virtualAccountId)
            return bridgeResponse.data?.map { mapToActivity(it) } ?: emptyList()
        } catch (e: BridgeApiException) {
            throw VirtualAccountException(
                "Failed to get virtual account activity via Bridge API: ${e.message}",
                e,
            )
        }
    }

    private fun mapToActivity(item: com.rytmo.library.bridge.model.VirtualAccountEvent): VirtualAccountActivity = VirtualAccountActivity(
        id = item.id,
        type = item.type?.value,
        virtualAccountId = item.virtualAccountId,
        amount = item.amount,
        currency = item.currency?.value,
        developerFeeAmount = item.developerFeeAmount,
        exchangeFeeAmount = item.exchangeFeeAmount,
        subtotalAmount = item.subtotalAmount,
        gasFee = item.gasFee,
        depositId = item.depositId,
        destinationTxHash = item.destinationTxHash,
        source =
        item.source?.let {
            VirtualAccountActivitySource(
                paymentRail = it.paymentRail?.value,
                description = it.description,
                senderName = it.senderName,
                senderBankRoutingNumber = it.senderBankRoutingNumber,
            )
        },
        createdAt = item.createdAt?.toString(),
    )

    private fun mapToResponse(item: com.rytmo.library.bridge.model.VirtualAccountResponse): VirtualAccountResponse {
        log.info(
            "Mapping virtual account id={} status={} sourceDepositInstructions type={} raw={}",
            item.id,
            item.status,
            item.sourceDepositInstructions?.javaClass?.simpleName,
            item.sourceDepositInstructions,
        )
        val mxDeposit =
            try {
                item.sourceDepositInstructions?.getVirtualAccountSourceDepositInstructionsMx()
            } catch (e: ClassCastException) {
                log.info(
                    "Virtual account id={} sourceDepositInstructions is not MX type: {}",
                    item.id,
                    e.message,
                )
                null
            }
        val usDeposit =
            if (mxDeposit == null) {
                try {
                    item.sourceDepositInstructions?.getVirtualAccountSourceDepositInstructionsUs()
                } catch (e: ClassCastException) {
                    log.info(
                        "Virtual account id={} sourceDepositInstructions is not US type: {}",
                        item.id,
                        e.message,
                    )
                    null
                }
            } else {
                null
            }
        if (mxDeposit == null && usDeposit == null) {
            log.info(
                "Virtual account id={} has null sourceDepositInstructions after mapping (raw was {})",
                item.id,
                item.sourceDepositInstructions,
            )
        }
        val sourceDepositInstructions =
            mxDeposit?.let {
                SourceDepositInstructions(
                    paymentRail = it.paymentRails?.firstOrNull()?.value,
                    currency = it.currency?.value,
                    bankName = it.bankName ?: "STP",
                    bankAddress = it.bankAddress,
                    bankRoutingNumber = null,
                    bankAccountNumber = null,
                    clabe = it.clabe,
                    bankCode = null,
                    beneficiaryName = it.accountHolderName,
                    depositMessage = null,
                )
            }
                ?: usDeposit?.let {
                    SourceDepositInstructions(
                        paymentRail = it.paymentRail?.value,
                        currency = it.currency?.value,
                        bankName = it.bankName ?: "STP",
                        bankAddress = it.bankAddress,
                        bankRoutingNumber = it.bankRoutingNumber,
                        bankAccountNumber = it.bankAccountNumber,
                        clabe = null,
                        bankCode = null,
                        beneficiaryName = null,
                        depositMessage = null,
                    )
                }
        return VirtualAccountResponse(
            id = item.id ?: "",
            status = item.status?.value,
            sourceCurrency = mxDeposit?.currency?.value ?: usDeposit?.currency?.value,
            sourcePaymentRail =
            mxDeposit?.paymentRails?.firstOrNull()?.value ?: usDeposit?.paymentRail?.value,
            destinationCurrency = item.destination?.currency?.value,
            destinationPaymentRail = item.destination?.paymentRail?.value,
            sourceDepositInstructions = sourceDepositInstructions,
            destination =
            item.destination?.let {
                VirtualAccountDestination(
                    currency = it.currency?.value,
                    paymentRail = it.paymentRail?.value,
                    address = it.address,
                )
            },
        )
    }
}
