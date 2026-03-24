package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapHistoryItem(
    @JsonProperty("signature") val signature: String,
    @JsonProperty("timestamp") val timestamp: Long?,
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("confirmationStatus") val confirmationStatus: String?,
)

data class SwapHistoryResponse(
    @JsonProperty("items") val items: List<SwapHistoryItem>,
    @JsonProperty("nextCursor") val nextCursor: String?,
)
