package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class OutputToken(
    @JsonProperty("mint") val mint: String,
    @JsonProperty("symbol") val symbol: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("decimals") val decimals: Int,
    @JsonProperty("logoURI") val logoURI: String?,
)
