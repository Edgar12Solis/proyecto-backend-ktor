package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class UsuarioRequest(
    val nombre: String,
    val email: String,
    val password: String,
    val rol: String
)

@Serializable
data class UsuarioResponse(
    val id: Int,
    val nombre: String,
    val email: String,
    val rol: String
)
