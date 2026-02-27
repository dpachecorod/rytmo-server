package com.rytmo.models.cardaccounts

import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(name = "CardAccount")
data class CardAccountResponse(
    val id: String,
    val status: String?,
    val cardholderName: CardholderNameResponse?,
    val cardDetails: CardDetailsResponse?,
    val balances: CardBalancesResponse?,
    val fundingInstructions: CardFundingInstructionsResponse?,
)

data class CardholderNameResponse(
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
)

data class CardDetailsResponse(
    val last4: String?,
    val expiry: String?,
    val bin: String?,
    val pinStatus: String?,
)

data class CardBalancesResponse(
    val available: String?,
    val hold: String?,
)

data class CardFundingInstructionsResponse(
    val currency: String?,
    val chain: String?,
    val address: String?,
)
