package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class TokenBalance(
    @JsonProperty("mint") val mint: String,
    @JsonProperty("symbol") val symbol: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("balance") val balance: String,
    @JsonProperty("decimals") val decimals: Int,
    @JsonProperty("priceUsd") val priceUsd: Double?,
    @JsonProperty("imageUrl") val imageUrl: String?,
)
