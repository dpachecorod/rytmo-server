package com.rytmo.models.send

import com.fasterxml.jackson.annotation.JsonProperty

data class SendSolanaRequest(
    @JsonProperty("recipientAddress") val recipientAddress: String,
    @JsonProperty("mintAddress") val mintAddress: String,
    @JsonProperty("amount") val amount: String,
    @JsonProperty("decimals") val decimals: Int,
)
