package com.rytmo.models.yield

import com.fasterxml.jackson.annotation.JsonProperty

data class YieldBytecodeMetadata(
    @JsonProperty("isCrossChain") @get:JsonProperty("isCrossChain") val isCrossChain: Boolean,
    @JsonProperty("isSameChainSwap") @get:JsonProperty("isSameChainSwap") val isSameChainSwap: Boolean,
    @JsonProperty("crossChainQuoteId") val crossChainQuoteId: String?,
)
