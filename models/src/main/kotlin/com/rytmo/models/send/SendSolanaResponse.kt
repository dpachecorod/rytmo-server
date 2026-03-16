package com.rytmo.models.send

import com.fasterxml.jackson.annotation.JsonProperty

data class SendSolanaResponse(@JsonProperty("signature") val signature: String)
