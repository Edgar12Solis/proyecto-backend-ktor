package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object HorariosBarberosTable : IntIdTable("horarios_barberos") {
    val barberoId = reference("barbero_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val config = text("config") // Formato: "1-10:00,1-11:00..."
}
