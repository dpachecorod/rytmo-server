package com.rytmo.models.externalaccounts

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateExternalAccountRequest(
    @JsonProperty("accountType") val accountType: String,
    @JsonProperty("accountOwnerName") val accountOwnerName: String,
    @JsonProperty("routingNumber") val routingNumber: String? = null,
    @JsonProperty("accountNumber") val accountNumber: String,
    @JsonProperty("address") val address: ExternalAccountAddress,
)
