package com.example.models

import kotlinx.serialization.Serializable

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
    val imagenUrl: String? = null,
    val serviceCategory: ServiceCategoryDTO
)

@Serializable
data class ServiceStats(
    val totalServices: Int,
    val activeServices: Int
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
    val selectedServiceIds: List<Int>? = null
)
