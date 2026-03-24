package com.rytmo.models.externalaccounts

data class ExternalAccountResponse(
    val id: String? = null,
    val accountOwnerName: String? = null,
    val bankName: String? = null,
    val last4: String? = null,
    val active: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val address: ExternalAccountAddress? = null,
    val liquidationAddress: String? = null,
)
