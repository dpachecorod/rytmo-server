package com.rytmo.models.yield

import com.fasterxml.jackson.annotation.JsonProperty

data class YieldBytecodeStep(
    @JsonProperty("chainId") val chainId: Int,
    @JsonProperty("to") val to: String,
    @JsonProperty("data") val data: String,
    @JsonProperty("value") val value: String?,
    @JsonProperty("from") val from: String?,
)
