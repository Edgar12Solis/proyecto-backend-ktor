package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ProductCategoryDTO(
    val id: Int,
    val nombre: String
)

@Serializable
data class ProductDTO(
    val id: Int,
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val sku: String,
    val imagenUrl: String? = null,
    val activo: Boolean,
    val category: ProductCategoryDTO,
    val descripcion: String? = null
)

@Serializable
data class InventoryStats(
    val totalProducts: Int,
    val lowStock: Int,
    val inventoryValue: Double
)

@Serializable
data class ReduceStockResponse(
    val success: Boolean,
    val message: String,
    val newStock: Int? = null
)
