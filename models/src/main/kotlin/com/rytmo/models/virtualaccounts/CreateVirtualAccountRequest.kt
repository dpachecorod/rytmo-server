package com.rytmo.models.virtualaccounts

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateVirtualAccountRequest(
    @JsonProperty("walletId")
    val walletId: String,
    @JsonProperty("sourceCurrency")
    val sourceCurrency: String,
    @JsonProperty("sourcePaymentRail")
    val sourcePaymentRail: String,
    @JsonProperty("destinationCurrency")
    val destinationCurrency: String,
    @JsonProperty("destinationPaymentRail")
    val destinationPaymentRail: String,
)
