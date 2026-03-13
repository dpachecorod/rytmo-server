package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapQuoteResponse(
    @JsonProperty("transaction") val transaction: String,
    @JsonProperty("inputMint") val inputMint: String,
    @JsonProperty("outputMint") val outputMint: String,
    @JsonProperty("inAmount") val inAmount: String,
    @JsonProperty("outAmount") val outAmount: String,
    @JsonProperty("executionMode") val executionMode: String,
    @JsonProperty("slippageBps") val slippageBps: Int?,
)
