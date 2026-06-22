package com.example.database

import org.jetbrains.exposed.sql.Table

object UsuariosTable : Table("usuarios") {

    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 100)
    val rol = varchar("rol", 50)

    override val primaryKey = PrimaryKey(id)
}