package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ClienteCitaHistorial(
    val id: Int,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    val status: String,
    @SerialName("servicio_nombre") val servicioNombre: String,
    val precio: Double
)

@Serializable
data class ClienteReservaRequest(
    @SerialName("barber_id") val barberoId: Int? = null,
    @SerialName("barber_name") val barberoNombre: String? = null,
    @SerialName("service_id") val servicioId: Int? = null,
    @SerialName("service_name") val servicioNombre: String? = null,
    @SerialName("promotion_id") val promocionId: Int? = null,
    val fecha: String? = null,
    @SerialName("hora_inicio") val horaInicio: String? = null
)
