package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ServiceCategoryDTO(
    val id: Int,
    val nombre: String
)

@Serializable
data class ServiceDTO(
    val id: Int,
    val nombre: String,
    val precio: Double,
    val duracion: Int,
    val activo: Boolean,
    val imagenUrl: String? = null,
    val serviceCategory: ServiceCategoryDTO
)

@Serializable
data class ServiceStats(
    val totalServices: Int, // Agregado por lógica
    val activeServices: Int // Agregado por lógica
)

@Serializable
data class PromotionDTO(
    val id: Int,
    val nombre: String,
    val descripcion: String? = null,
    val precioOriginal: Double,
    val precioPromocional: Double,
    val activo: Boolean,
    val fechaInicio: String,
    val fechaFinal: String,
    val selectedServiceIds: List<Int>? = null // Para creación/edición
)
