package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapPrepareResponse(@JsonProperty("partialTransaction") val partialTransaction: String)
