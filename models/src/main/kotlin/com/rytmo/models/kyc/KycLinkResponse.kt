package com.rytmo.models.kyc

data class KycLinkResponse(
    val id: String,
    val kycLink: String,
    val kycStatus: String,
    val tosLink: String,
    val tosStatus: String,
    val createdAt: String?,
)
