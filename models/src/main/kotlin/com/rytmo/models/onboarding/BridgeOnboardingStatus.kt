package com.rytmo.models.onboarding

import com.rytmo.models.kyc.RejectionReason

data class BridgeOnboardingStatus(
    val kycStatus: String,
    val tosStatus: String,
    val kycLink: String,
    val tosLink: String,
    val rejectionReasons: List<RejectionReason>,
    val createdAt: String?,
)
