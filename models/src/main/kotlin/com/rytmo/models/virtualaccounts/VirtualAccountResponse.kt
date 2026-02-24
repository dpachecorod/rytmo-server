package com.rytmo.models.virtualaccounts

import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(name = "VirtualAccount")
data class VirtualAccountResponse(
    val id: String,
    val status: String?,
    val sourceCurrency: String?,
    val sourcePaymentRail: String?,
    val destinationCurrency: String?,
    val destinationPaymentRail: String?,
    val sourceDepositInstructions: SourceDepositInstructions?,
    val destination: VirtualAccountDestination?,
)

@Schema(name = "SpeiDepositInstructions")
data class SourceDepositInstructions(
    val paymentRail: String?,
    val currency: String?,
    val bankName: String?,
    val bankAddress: String?,
    val bankRoutingNumber: String?,
    val bankAccountNumber: String?,
    val clabe: String?,
    val bankCode: String?,
    val beneficiaryName: String?,
    val depositMessage: String?,
)

data class VirtualAccountDestination(
    val currency: String?,
    val paymentRail: String?,
    val address: String?,
)
