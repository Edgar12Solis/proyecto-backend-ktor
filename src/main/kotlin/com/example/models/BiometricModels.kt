package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class BiometricRegisterRequest(
    val biometricToken: String
)

@Serializable
data class BiometricLoginRequest(
    val biometricToken: String
)
