package com.rytmo.models.externalaccounts

import com.fasterxml.jackson.annotation.JsonProperty

data class ExternalAccountAddress(
    @JsonProperty("streetLine1") val streetLine1: String,
    @JsonProperty("streetLine2") val streetLine2: String? = null,
    @JsonProperty("city") val city: String,
    @JsonProperty("state") val state: String? = null,
    @JsonProperty("postalCode") val postalCode: String? = null,
    @JsonProperty("country") val country: String,
)
