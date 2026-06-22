package com.example.database

import org.jetbrains.exposed.sql.Table

object ClientesTable : Table("clientes") {
    val id = integer("id").autoIncrement()
    val nombre = text("nombre")
    val apellido = text("apellido")
    val fechaCumpleanos = text("fecha_cumpleanos")
    val telefono = text("telefono")
    val correo = text("correo")

    override val primaryKey = PrimaryKey(id)
}