package com.rytmo.models.swap

import com.fasterxml.jackson.annotation.JsonProperty

data class SwapExecuteResponse(@JsonProperty("signature") val signature: String)
