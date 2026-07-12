package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val id: Int,
    val type: String, // 'service', 'product', 'promotion'
    val name: String,
    val price: Double,
    val duration: Int? = null
)

@Serializable
data class GhostSaleRequest(
    val barberId: Int,
    val paymentMethod: String,
    val amountReceived: Double,
    val ghostName: String? = null,
    val cartItems: List<CartItem>
)
