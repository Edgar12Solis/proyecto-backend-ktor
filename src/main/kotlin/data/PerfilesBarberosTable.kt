package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object PerfilesBarberosTable : IntIdTable("perfiles_barberos") {
    val usuarioId = reference("usuario_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val especialidad = varchar("especialidad", 100)
    val biografia = text("biografia")
}
