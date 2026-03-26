package com.rytmo.models.devicetoken

import com.fasterxml.jackson.annotation.JsonProperty

data class DeleteDeviceTokenRequest(
    @JsonProperty("expoToken") val expoToken: String,
)
