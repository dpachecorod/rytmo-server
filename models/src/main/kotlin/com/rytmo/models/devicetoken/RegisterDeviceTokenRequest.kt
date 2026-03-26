package com.rytmo.models.devicetoken

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterDeviceTokenRequest(
    @JsonProperty("expoToken") val expoToken: String,
)
