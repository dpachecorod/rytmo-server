package com.rytmo.models.yield

import com.fasterxml.jackson.annotation.JsonProperty

data class YieldStrategy(
    @JsonProperty("id") val id: String,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("protocol") val protocol: String,
    @JsonProperty("asset") val asset: String,
    @JsonProperty("assetName") val assetName: String,
    @JsonProperty("network") val network: String,
    @JsonProperty("networkId") val networkId: String,
    @JsonProperty("apy") val apy: Double?,
    @JsonProperty("paused") val paused: Boolean,
    @JsonProperty("availableActions") val availableActions: List<String>,
    @JsonProperty("logourl") @get:JsonProperty("logourl") val logoUrl: String?,
    @JsonProperty("fee") val fee: String?,
)
