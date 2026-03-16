package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapExecuteRequest(
    @JsonProperty("inputMint") val inputMint: String,
    @JsonProperty("outputMint") val outputMint: String,
    @JsonProperty("amount") val amount: String,
    @JsonProperty("slippageBps") val slippageBps: String?,
)
