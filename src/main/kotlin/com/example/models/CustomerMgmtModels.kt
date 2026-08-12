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
    val id: Int? = null,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val correo: String,
    @SerialName("fecha_registro") val fechaRegistro: String? = null,
    val estado: String? = "active",
    @SerialName("fecha_cumpleanos") val fecha_cumpleanos: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
    val password: String? = null, // Solo para creación
    @SerialName("imagen_url") val imagenUrl: String? = null
)
