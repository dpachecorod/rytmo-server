package com.rytmo.models.devicetoken

import com.fasterxml.jackson.annotation.JsonProperty

data class DeviceTokenResponse(
    @JsonProperty("expoToken") val expoToken: String,
    @JsonProperty("createdAt") val createdAt: String?,
    @JsonProperty("updatedAt") val updatedAt: String?,
)
