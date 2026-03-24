package com.rytmo.models.send

import com.fasterxml.jackson.annotation.JsonProperty

data class SendSolanaPartialResponse(@JsonProperty("partialTransaction") val partialTransaction: String)
