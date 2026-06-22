package com.example.solicitudes

import kotlinx.serialization.Serializable

@Serializable
data class UsuarioResponse(
    val id: Int,
    val nombre: String,
    val email: String,
    val rol: String
)
