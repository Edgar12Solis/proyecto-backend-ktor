package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class BiometricLoginRequest(
    val deviceId: String,
    val email: String
)
