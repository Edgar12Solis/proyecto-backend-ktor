package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object HorariosBarberosTable : IntIdTable("horarios_barberos") {
    val barberoId = reference("barbero_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val config = text("config") // Formato: "1-10:00,1-11:00..."
}

object BarberoEspecialidadesTable : Table("barbero_especialidades") {
    val usuarioId = reference("usuario_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val categoriaId = reference("categoria_id", CategoriasServiciosTable, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(usuarioId, categoriaId)
}
