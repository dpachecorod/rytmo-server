package com.rytmo.models.virtualaccounts

data class VirtualAccountActivity(
    val id: String?,
    val type: String?,
    val virtualAccountId: String?,
    val amount: String?,
    val currency: String?,
    val developerFeeAmount: String?,
    val exchangeFeeAmount: String?,
    val subtotalAmount: String?,
    val gasFee: String?,
    val depositId: String?,
    val destinationTxHash: String?,
    val source: VirtualAccountActivitySource?,
    val createdAt: String?,
)

data class VirtualAccountActivitySource(
    val paymentRail: String?,
    val description: String?,
    val senderName: String?,
    val senderBankRoutingNumber: String?,
)
