package com.rytmo.models.kyc

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateKycLinkRequest(
    @JsonProperty("fullName")
    val fullName: String,
    @JsonProperty("email")
    val email: String,
)
