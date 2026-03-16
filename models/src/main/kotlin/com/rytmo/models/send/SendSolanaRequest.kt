package com.rytmo.models.send

import com.fasterxml.jackson.annotation.JsonProperty

data class SendSolanaRequest(
    @JsonProperty("recipientAddress") val recipientAddress: String,
    @JsonProperty("amountUsdc") val amountUsdc: Double,
)
