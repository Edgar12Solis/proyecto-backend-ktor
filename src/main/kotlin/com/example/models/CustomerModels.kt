package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class AppointmentDTO(
    val serviceName: String,
    val date: String,
    val time: String,
    val totalPrice: Double,
    val status: String
)

@Serializable
data class DashboardDataResponse(
    val customerName: String,
    val recentAppointments: List<AppointmentDTO>
)

@Serializable
data class UserProfileResponse(
    val nombres: String,
    val apellidos: String,
    val email: String,
    val telefono: String,
    val fechaNacimiento: String?,
    val direccion: String?
)

@Serializable
data class UpdateProfileRequest(
    val nombres: String,
    val apellidos: String,
    val telefono: String,
    val fechaNacimiento: String?,
    val direccion: String?,
    val password: String? = null
)

@Serializable
data class UpdateProfileResponse(
    val success: Boolean,
    val message: String
)
