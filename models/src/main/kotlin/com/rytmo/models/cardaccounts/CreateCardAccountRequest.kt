package com.rytmo.models.cardaccounts

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateCardAccountRequest(
    @JsonProperty("currency") val currency: String,
    @JsonProperty("chain") val chain: String,
    @JsonProperty("walletAddress") val walletAddress: String,
)
