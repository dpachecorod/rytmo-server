package com.rytmo.models.customer

import com.fasterxml.jackson.annotation.JsonProperty

data class OnboardRequest(
    @JsonProperty("email")
    val email: String,
    @JsonProperty("name")
    val name: String,
)
