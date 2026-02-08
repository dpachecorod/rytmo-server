package com.rytmo.models.webhooks

import com.fasterxml.jackson.annotation.JsonProperty

data class WebhookEvent(
    @JsonProperty("api_version")
    val apiVersion: String,
    @JsonProperty("event_id")
    val eventId: String,
    @JsonProperty("event_category")
    val eventCategory: String,
    @JsonProperty("event_type")
    val eventType: String,
    @JsonProperty("event_object")
    val eventObject: Map<String, Any>,
    @JsonProperty("event_object_changes")
    val eventObjectChanges: Map<String, Any>?,
    @JsonProperty("event_created_at")
    val eventCreatedAt: String,
)
