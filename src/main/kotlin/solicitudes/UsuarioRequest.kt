package com.example.solicitudes

import kotlinx.serialization.Serializable

@Serializable
data class UsuarioRequest(
    val nombre: String,
    val email: String,
    val password: String,
    val rol: String
)