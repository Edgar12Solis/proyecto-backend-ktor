package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AdminCustomerInfo(
    val nombre: String,
    val apellido: String,
    val telefono: String
)

@Serializable
data class AdminServiceInfo(
    val nombre: String,
    val precio: Double
)

@Serializable
data class AdminBarberInfo(
    @SerialName("nombre_completo") val nombreCompleto: String
)

@Serializable
data class AdminAppointmentResponse(
    val id: Int,
    val customer: AdminCustomerInfo,
    val date: String,
    @SerialName("start_time") val startTime: String,
    val status: String,
    val service: AdminServiceInfo,
    val barber: AdminBarberInfo
)

@Serializable
data class ClientAppointmentResponse(
    val id: Int,
    val date: String,
    @SerialName("start_time") val startTime: String,
    val status: String,
    @SerialName("service_name") val serviceName: String,
    @SerialName("total_price") val totalPrice: Double,
    @SerialName("barber_name") val barberName: String
)
