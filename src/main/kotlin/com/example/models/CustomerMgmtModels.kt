package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class CustomerMgmtStats(
    @SerialName("total_global") val totalGlobal: Int,
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
    @SerialName("fecha_registro") val fechaRegistro: String?,
    val estado: String,
    @SerialName("fecha_cumpleanos") val fecha_cumpleanos: String?,
    val direccion: String?,
    val notas: String?,
    val password: String? = null // Solo para creación
)
