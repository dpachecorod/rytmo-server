package com.rytmo.models.customer

import java.time.Instant

data class Customer(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
