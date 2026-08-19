package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DisponibilidadRequest(
    val fecha: String, // YYYY-MM-DD
    @SerialName("hora_inicio") val horaInicio: String, // HH:mm
    val duracion: Int // En minutos
)

@Serializable
data class BarberoDisponibleDTO(
    val id: Int,
    val nombre: String,
    @SerialName("imagen_url") val imagenUrl: String? = null
)

@Serializable
data class ReservaCreateRequest(
    @SerialName("usuario_id") val usuarioId: Int? = null, // null si es ocasional
    @SerialName("es_ocasional") val esOcasional: Boolean = false,
    @SerialName("cliente_nombre") val clienteNombre: String? = null, // Para ocasional
    @SerialName("cliente_telefono") val clienteTelefono: String? = null, // Para ocasional
    @SerialName("barbero_id") val barberoId: Int,
    @SerialName("servicio_nombre") val servicioNombre: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    val duracion: Int,
    val precio: Double,
    @SerialName("metodo_pago") val metodoPago: String
)

@Serializable
data class CitaDetalleDTO(
    val id: Int,
    @SerialName("cliente_nombre") val clienteNombre: String,
    @SerialName("barbero_nombre") val barberoNombre: String,
    @SerialName("servicio_nombre") val servicioNombre: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    val duracion: Int,
    val precio: Double,
    val estado: String,
    @SerialName("metodo_pago") val metodoPago: String?
)

@Serializable
data class CitaReprogramarRequest(
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("barbero_id") val barberoId: Int? = null
)

