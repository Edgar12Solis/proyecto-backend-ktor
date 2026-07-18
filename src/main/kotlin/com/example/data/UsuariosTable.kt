package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable

object UsuariosTable : IntIdTable("usuarios") {
    val nombre = varchar("nombre", 100)
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 100)
    val rol = varchar("rol", 50)
    val bio = text("bio").nullable()
    val scheduleConfig = text("schedule_config").nullable()
    val activo = bool("activo").default(true)
    val fechaRegistro = varchar("fecha_registro", 50).nullable()
    val biometricToken = varchar("biometric_token", 255).nullable()
    val imagenUrl = varchar("imagen_url", 255).nullable()
}
