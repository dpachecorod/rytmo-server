package com.rytmo.models.yield

import com.fasterxml.jackson.annotation.JsonProperty

data class YieldQuoteResponse(
    @JsonProperty("expectedReturn") val expectedReturn: String,
    @JsonProperty("fee") val fee: String,
    @JsonProperty("apy") val apy: Double,
)
