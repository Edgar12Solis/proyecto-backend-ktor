package com.example.routes

import com.example.data.*
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.inventoryRoutes() {
    authenticate("auth-jwt") {

        // 1. Listar Productos
        get("/admin/products") {
            try {
                val products = transaction {
                    (ProductosTable innerJoin CategoriasProductosTable)
                        .selectAll()
                        .map { row ->
                            ProductDTO(
                                id = row[ProductosTable.id].value,
                                nombre = row[ProductosTable.nombre],
                                precio = row[ProductosTable.precio],
                                stock = row[ProductosTable.stock],
                                sku = row[ProductosTable.sku],
                                imagenUrl = row[ProductosTable.imagenUrl],
                                activo = row[ProductosTable.activo],
                                category = ProductCategoryDTO(
                                    id = row[CategoriasProductosTable.id].value,
                                    nombre = row[CategoriasProductosTable.nombre]
                                ),
                                descripcion = row[ProductosTable.descripcion]
                            )
                        }
                }
                call.respond(products)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al listar productos")
            }
        }

        // 2. Listar Categorías de Productos
        get("/admin/product-categories") {
            try {
                val categories = transaction {
                    CategoriasProductosTable.selectAll().map {
                        ProductCategoryDTO(it[CategoriasProductosTable.id].value, it[CategoriasProductosTable.nombre])
                    }
                }
                call.respond(categories)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener categorías")
            }
        }

        // ALINEADO CON PROMPT MAESTRO
        post("/admin/product-categories") {
            try {
                val req = call.receive<CategoryCreateRequest>()
                transaction {
                    CategoriasProductosTable.insert {
                        it[nombre] = req.nombre
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Categoría de producto creada"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al crear categoría"))
            }
        }

        // 3. Crear Producto (Manejo de Nulos e ID de Categoría)
        post("/admin/products") {
            try {
                val req = call.receive<ProductDTO>()
                transaction {
                    ProductosTable.insert {
                        it[nombre] = req.nombre
                        it[precio] = req.precio
                        it[stock] = req.stock
                        it[sku] = req.sku
                        it[imagenUrl] = req.imagenUrl
                        it[activo] = req.activo
                        it[categoriaId] = req.category.id!!
                        it[descripcion] = req.descripcion
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Producto creado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: \${e.message}"))
            }
        }

        // 4. Editar Producto
        put("/admin/products/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val req = call.receive<ProductDTO>()
                transaction {
                    ProductosTable.update({ ProductosTable.id eq id }) {
                        it[nombre] = req.nombre
                        it[precio] = req.precio
                        it[stock] = req.stock
                        it[sku] = req.sku
                        it[imagenUrl] = req.imagenUrl
                        it[activo] = req.activo
                        it[categoriaId] = req.category.id!!
                        it[descripcion] = req.descripcion
                    }
                }
                call.respond(AdminActionResponse(true, "Producto actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: \${e.message}"))
            }
        }

        // 5. Estadísticas y Reducción de Stock
        get("/admin/inventory/stats") {
            try {
                val stats = transaction {
                    val total = ProductosTable.selectAll().count().toInt()
                    val lowStock = ProductosTable.selectAll().where { ProductosTable.stock less 5 }.count().toInt()
                    val value = ProductosTable.selectAll().sumOf { it[ProductosTable.precio] * it[ProductosTable.stock] }
                    InventoryStats(total, lowStock, value)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        post("/admin/products/{id}/reduce-stock") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id != null) {
                try {
                    val newStock = transaction {
                        val current = ProductosTable.selectAll().where { ProductosTable.id eq id }.single()[ProductosTable.stock]
                        if (current > 0) {
                            ProductosTable.update({ ProductosTable.id eq id }) {
                                it.update(ProductosTable.stock, ProductosTable.stock minus 1)
                            }
                            current - 1
                        } else current
                    }
                    call.respond(ReduceStockResponse(true, "Stock reducido", newStock))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.OK, ReduceStockResponse(false, "Error al reducir stock"))
                }
            }
        }
    }
}
