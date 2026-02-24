package com.rytmo.models.bridge

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateBridgeCustomerRequest(
    @JsonProperty("firstName") val firstName: String,
    @JsonProperty("lastName") val lastName: String,
    @JsonProperty("email") val email: String,
)
