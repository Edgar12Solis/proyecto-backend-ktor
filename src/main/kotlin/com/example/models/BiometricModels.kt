package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class BiometricRegisterRequest(
    val token: String
)

@Serializable
data class BiometricLoginRequest(
    val token: String
)
