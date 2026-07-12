package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class CustomerMgmtStats(
    val totalGlobal: Int,
    val activos: Int,
    val inactivos: Int
)

@Serializable
data class CustomerMgmtDetail(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val correo: String,
    val fechaRegistro: String?,
    val estado: String,
    val fecha_cumpleanos: String?,
    val direccion: String?,
    val notas: String?
)
