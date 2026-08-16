package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BarberStats(
    @SerialName("total_barberos") val totalBarbers: Int,
    @SerialName("barberos_activos") val activeBarbers: Int,
    @SerialName("barberos_off") val offBarbers: Int
)

@Serializable
data class BarberScheduleRequest(
    val config: String // DiaID-Hora,...
)

@Serializable
data class BarberCreateRequest(
    val id: Int? = null,
    @SerialName("nombre_completo") val nombreCompleto: String,
    val telefono: String,
    val email: String,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    val activo: Boolean? = true,
    val bio: String,
    @SerialName("configuracion_horario") val scheduleConfiguration: String? = "",
    @SerialName("especialidades") val specialties: List<String>, // Nombres de categorías
    val password: String? = null // Para creación
)

@Serializable
data class BarberFullProfileResponse(
    val id: Int,
    @SerialName("nombre_completo") val nombreCompleto: String,
    val email: String,
    val telefono: String,
    val bio: String,
    val activo: Boolean,
    @SerialName("configuracion_horario") val scheduleConfiguration: String,
    @SerialName("especialidades") val specialties: List<String>,
    @SerialName("imagen_url") val imagenUrl: String? = null
)
