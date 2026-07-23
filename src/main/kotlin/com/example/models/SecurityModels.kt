package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class VerifyPasswordRequest(
    val password: String
)

@Serializable
data class ChangePasswordRequest(
    val newPassword: String
)

@Serializable
data class SecurityActionResponse(
    val success: Boolean,
    val message: String
)
