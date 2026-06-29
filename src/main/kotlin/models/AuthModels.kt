package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val rol: String? = null
)

@Serializable
data class RegisterRequest(
    val nombres: String,
    val apellidos: String,
    val email: String,
    val telefono: String,
    val password: String
)

@Serializable
data class RegisterResponse(
    val success: Boolean,
    val message: String
)
