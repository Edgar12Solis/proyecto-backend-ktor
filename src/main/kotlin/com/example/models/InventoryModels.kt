package com.example.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ProductCategoryDTO(
    val id: Int? = null,
    val nombre: String
)

@Serializable
data class ProductDTO(
    val id: Int? = null,
    val nombre: String,
    val precio: Double,
    val stock: Int? = 0,
    val sku: String? = null,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    val activo: Boolean? = true,
    @SerialName("product_category") val category: ProductCategoryDTO,
    val descripcion: String? = null
)

@Serializable
data class InventoryStats(
    @SerialName("total_products") val totalProducts: Int,
    @SerialName("low_stock") val lowStock: Int,
    @SerialName("inventory_value") val inventoryValue: Double
)

@Serializable
data class ReduceStockRequest(
    val cantidad: Int = 1
)

@Serializable
data class ReduceStockResponse(
    val success: Boolean,
    val message: String,
    @SerialName("new_stock") val newStock: Int? = null
)
