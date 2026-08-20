package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DashboardStats(
    val ingresos: Double,
    val citas: Int,
    val equipo: Int,
    val activos: Int,
    @SerialName("porcentaje_meta") val porcentajeMeta: Double
)
