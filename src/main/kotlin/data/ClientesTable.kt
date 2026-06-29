package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable

object ClientesTable : IntIdTable("clientes") {
    val nombre = text("nombre")
    val apellido = text("apellido")
    val fechaCumpleanos = text("fecha_cumpleanos")
    val telefono = text("telefono")
    val correo = text("correo")
}
