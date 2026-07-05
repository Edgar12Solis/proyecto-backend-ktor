package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class BarberoCreateRequest(
    val email: String,
    val password: String,
    val nombreCompleto: String,
    val especialidad: String,
    val biografia: String
)

@Serializable
data class AdminProfileResponse(
    val nombres: String,
    val apellidos: String,
    val email: String,
    val telefono: String,
    val rol: String
)

@Serializable
data class AdminListItem(
    val id: Int,
    val nombres: String,
    val apellidos: String,
    val email: String
)

@Serializable
data class CreateAdminRequest(
    val nombres: String,
    val apellidos: String,
    val email: String,
    val telefono: String,
    val password: String
)

@Serializable
data class AdminActionResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class UpdateAdminProfileRequest(
    val nombres: String,
    val apellidos: String,
    val telefono: String,
    val email: String,
    val password: String? = null
)
