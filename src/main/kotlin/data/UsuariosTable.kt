package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable

object UsuariosTable : IntIdTable("usuarios") {
    val nombre = varchar("nombre", 100)
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 100)
    val rol = varchar("rol", 50)
}
