package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object PerfilesClientesTable : IntIdTable("perfiles_clientes") {
    val usuarioId = reference("usuario_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val nombres = varchar("nombres", 100)
    val apellidos = varchar("apellidos", 100)
    val telefono = varchar("telefono", 20)
}
