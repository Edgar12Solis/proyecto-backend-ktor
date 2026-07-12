package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class BarberStats(
    val totalBarbers: Int,
    val activeBarbers: Int
)

@Serializable
data class BarberScheduleRequest(
    val config: String // DiaID-Hora,...
)

@Serializable
data class BarberFullProfileResponse(
    val id: Int,
    val nombreCompleto: String,
    val email: String,
    val telefono: String,
    val bio: String,
    val activo: Boolean,
    val specialties: List<String>
)
