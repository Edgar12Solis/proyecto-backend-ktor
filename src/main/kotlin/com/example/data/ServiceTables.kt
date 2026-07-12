package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable

object CategoriasServiciosTable : IntIdTable("categorias_servicios") {
    val nombre = varchar("nombre", 100)
}

object ServiciosTable : IntIdTable("servicios") {
    val nombre = varchar("nombre", 100)
    val precio = double("precio")
    val duracion = integer("duracion") // en minutos
    val activo = bool("activo").default(true)
    val imagenUrl = varchar("imagen_url", 255).nullable()
    val categoriaId = reference("categoria_id", CategoriasServiciosTable)
}
