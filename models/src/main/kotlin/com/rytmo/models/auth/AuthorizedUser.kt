package com.rytmo.models.auth

data class AuthorizedUser(
    val issuer: String,
    val userId: String,
    val sessionId: String,
    val issuedAt: Number,
    val expiration: Number
)
