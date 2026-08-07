package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AppointmentDTO(
    @SerialName("service_name") val serviceName: String,
    val date: String,
    val time: String,
    @SerialName("total_price") val totalPrice: Double,
    val status: String
)

@Serializable
data class DashboardDataResponse(
    @SerialName("customer_name") val customerName: String,
    @SerialName("recent_appointments") val recentAppointments: List<AppointmentDTO>
)

@Serializable
data class UserProfileResponse(
    val nombres: String,
    val apellidos: String,
    val email: String,
    val telefono: String,
    @SerialName("fecha_nacimiento") val fechaNacimiento: String?,
    val direccion: String?,
    @SerialName("imagen_url") val imagenUrl: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val nombres: String,
    val apellidos: String,
    val telefono: String,
    @SerialName("fecha_nacimiento") val fechaNacimiento: String?,
    val direccion: String?,
    val password: String? = null
)

@Serializable
data class UpdateProfileResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class BookingRequest(
    @SerialName("barber_id") val barberId: Int,
    @SerialName("service_id") val serviceId: Int,
    val date: String,
    @SerialName("start_time") val startTime: String
)
