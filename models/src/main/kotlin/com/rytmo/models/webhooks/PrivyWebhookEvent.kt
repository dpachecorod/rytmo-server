package com.rytmo.models.webhooks

import com.fasterxml.jackson.annotation.JsonProperty

data class PrivyWebhookEvent(
    @JsonProperty("type") val type: String,
    @JsonProperty("message") val message: Map<String, Any>,
    @JsonProperty("idempotency_key") val idempotencyKey: String?,
)
