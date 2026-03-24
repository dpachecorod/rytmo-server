package com.rytmo.models.yield

import com.fasterxml.jackson.annotation.JsonProperty

data class YieldStrategiesResponse(
    @JsonProperty("strategies") val strategies: List<YieldStrategy>,
    @JsonProperty("totalDocs") val totalDocs: Int,
    @JsonProperty("limit") val limit: Int,
    @JsonProperty("page") val page: Int,
    @JsonProperty("totalPages") val totalPages: Int,
    @JsonProperty("hasNextPage") val hasNextPage: Boolean,
    @JsonProperty("hasPrevPage") val hasPrevPage: Boolean,
)
