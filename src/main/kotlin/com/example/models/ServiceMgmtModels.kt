package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ServiceCategoryDTO(
    val id: Int? = null,
    val nombre: String
)

@Serializable
data class ServiceDTO(
    val id: Int? = null,
    val nombre: String,
    val precio: Double,
    val duracion: Int,
    val activo: Boolean? = true,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    val serviceCategory: ServiceCategoryDTO
)

@Serializable
data class ServiceStats(
    val totalServices: Int,
    val totalPromotions: Int
)

@Serializable
data class PromotionDTO(
    val id: Int? = null,
    val nombre: String,
    val descripcion: String? = null,
    val precioOriginal: Double,
    val precioPromocional: Double,
    val activo: Boolean? = true,
    val fechaInicio: String,
    val fechaFinal: String,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    val selectedServiceIds: List<Int>? = null
)
