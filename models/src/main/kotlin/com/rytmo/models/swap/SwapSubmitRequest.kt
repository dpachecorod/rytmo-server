package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapSubmitRequest(@JsonProperty("signedTransaction") val signedTransaction: String)
