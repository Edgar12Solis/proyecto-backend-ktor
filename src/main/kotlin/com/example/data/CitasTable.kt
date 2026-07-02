package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object CitasTable : IntIdTable("citas") {
    val usuarioId = reference("usuario_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val serviceName = varchar("service_name", 100)
    val date = varchar("date", 50)
    val time = varchar("time", 20)
    val totalPrice = double("total_price")
    val status = varchar("status", 20) // 'Programada', 'Completada', 'Cancelada'
}
