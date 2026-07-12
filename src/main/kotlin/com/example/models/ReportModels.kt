package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class GeneralStatsResponse(
    val totalApps: Int,
    val totalIncome: Double,
    val topBarberName: String?,
    val topBarberCount: Int
)

@Serializable
data class SoldProductDTO(
    val name: String,
    val quantity: Int,
    val price: Double,
    val date: String,
    val customer: String
)

@Serializable
data class SaleDetailDTO(
    val name: String,
    val price: String // Formato "$200.00"
)
