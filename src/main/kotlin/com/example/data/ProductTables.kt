package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable

object CategoriasProductosTable : IntIdTable("categorias_productos") {
    val nombre = varchar("nombre", 100)
}

object ProductosTable : IntIdTable("productos") {
    val nombre = varchar("nombre", 100)
    val precio = double("precio")
    val stock = integer("stock").default(0)
    val sku = varchar("sku", 50).uniqueIndex()
    val imagenUrl = varchar("imagen_url", 255).nullable()
    val activo = bool("activo").default(true)
    val categoriaId = reference("categoria_id", CategoriasProductosTable)
    val descripcion = text("descripcion").nullable()
}
