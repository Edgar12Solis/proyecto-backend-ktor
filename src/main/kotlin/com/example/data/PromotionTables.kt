package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object PromocionesTable : IntIdTable("promociones") {
    val nombre = varchar("nombre", 100)
    val descripcion = text("descripcion").nullable()
    val precioOriginal = double("precio_original")
    val precioPromocional = double("precio_promocional")
    val activo = bool("activo").default(true)
    val fechaInicio = varchar("fecha_inicio", 50)
    val fechaFinal = varchar("fecha_final", 50)
}

object PromocionServiciosTable : IntIdTable("promocion_servicios") {
    val promocionId = reference("promocion_id", PromocionesTable, onDelete = ReferenceOption.CASCADE)
    val servicioId = reference("servicio_id", ServiciosTable, onDelete = ReferenceOption.CASCADE)
}
