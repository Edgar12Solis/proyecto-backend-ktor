package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class VentaExpressProducto(
    val id: Int,
    val cantidad: Int
)

@Serializable
data class VentaExpressRequest(
    @SerialName("usuario_id") val usuarioId: Int? = null, // null si es ocasional
    @SerialName("barbero_id") val barberoId: Int,
    @SerialName("servicios_nombres") val serviciosNombres: List<String>,
    @SerialName("productos") val productos: List<VentaExpressProducto>,
    @SerialName("total_pagar") val totalPagar: Double,
    @SerialName("metodo_pago") val metodoPago: String,
    @SerialName("es_ocasional") val esOcasional: Boolean = false,
    @SerialName("cliente_nombre") val clienteNombre: String? = null // Solo si es ocasional
)
