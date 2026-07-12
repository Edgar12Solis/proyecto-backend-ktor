package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminCustomerInfo(
    val nombre: String,
    val telefono: String
)

@Serializable
data class AdminServiceInfo(
    val nombre: String,
    val precio: Double
)

@Serializable
data class AdminBarberInfo(
    val nombreCompleto: String
)

@Serializable
data class AdminAppointmentResponse(
    val id: Int,
    val customer: AdminCustomerInfo,
    val date: String,
    val startTime: String,
    val status: String,
    val service: AdminServiceInfo,
    val barber: AdminBarberInfo
)
