package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapStatusResponse(
    @JsonProperty("status") val status: String,
    @JsonProperty("fills") val fills: List<Any>?,
    @JsonProperty("error") val error: String?,
)
