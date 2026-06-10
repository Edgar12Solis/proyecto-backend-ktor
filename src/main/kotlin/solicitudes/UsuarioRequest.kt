package com.example.solicitudes

import kotlinx.serialization.Serializable

@Serializable
data class UsuarioRequest(
    val nombre: String,
    val rol: String
)