package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class BarberoCreateRequest(
    val email: String,
    val password: String,
    val nombreCompleto: String,
    val especialidad: String,
    val biografia: String
)
