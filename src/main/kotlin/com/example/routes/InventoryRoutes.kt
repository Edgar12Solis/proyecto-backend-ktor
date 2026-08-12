package com.example.routes

import com.example.data.*
import com.example.models.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
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

        // 3. Crear Producto (Multipart)
        post("/admin/products") {
            try {
                val multipart = call.receiveMultipart()
                var productDTO: ProductDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "product") {
                            productDTO = Json { ignoreUnknownKeys = true }.decodeFromString<ProductDTO>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (productDTO == null) return@post call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl = productDTO!!.imagenUrl
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "prod_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/products/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/products/$fileName"
                    }

                    ProductosTable.insert {
                        it[nombre] = productDTO!!.nombre
                        it[precio] = productDTO!!.precio
                        it[stock] = productDTO!!.stock ?: 0
                        it[sku] = productDTO!!.sku ?: "WL-SKU-${System.currentTimeMillis()}"
                        it[imagenUrl] = finalImageUrl
                        it[activo] = productDTO!!.activo ?: true
                        it[categoriaId] = productDTO!!.category.id!!
                        it[descripcion] = productDTO!!.descripcion
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Producto creado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 4. Editar Producto (Multipart)
        put("/admin/products/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val multipart = call.receiveMultipart()
                var productDTO: ProductDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "product") {
                            productDTO = Json { ignoreUnknownKeys = true }.decodeFromString<ProductDTO>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (productDTO == null) return@put call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl: String? = null
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "prod_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/products/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/products/$fileName"
                    }

                    ProductosTable.update({ ProductosTable.id eq id }) {
                        it[nombre] = productDTO!!.nombre
                        it[precio] = productDTO!!.precio
                        it[stock] = productDTO!!.stock ?: 0
                        it[sku] = productDTO!!.sku ?: "WL-SKU-$id"
                        it[activo] = productDTO!!.activo ?: true
                        it[categoriaId] = productDTO!!.category.id!!
                        it[descripcion] = productDTO!!.descripcion
                        if (finalImageUrl != null) it[imagenUrl] = finalImageUrl
                    }
                }
                call.respond(AdminActionResponse(true, "Producto actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 5. Eliminar Producto
        delete("/admin/products/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                transaction {
                    ProductosTable.deleteWhere { ProductosTable.id eq id }
                }
                call.respond(AdminActionResponse(true, "Producto eliminado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al eliminar"))
            }
        }

        // 6. Estadísticas y Reducción de Stock
        get("/admin/inventory/stats") {
            try {
                val stats = transaction {
                    val total = ProductosTable.selectAll().count().toInt()
                    // Stock bajo definido como 3 o menos (según requerimiento)
                    val lowStock = ProductosTable.selectAll().where { ProductosTable.stock lessEq 3 }.count().toInt()
                    val value = ProductosTable.selectAll().sumOf { it[ProductosTable.precio] * it[ProductosTable.stock] }
                    InventoryStats(total, lowStock, value)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        post("/admin/products/{id}/reduce-stock") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val req = try { call.receive<ReduceStockRequest>() } catch (e: Exception) { ReduceStockRequest(1) }
                val newStock = transaction {
                    val current = ProductosTable.selectAll().where { ProductosTable.id eq id }.single()[ProductosTable.stock]
                    val reduction = req.cantidad
                    if (current >= reduction) {
                        ProductosTable.update({ ProductosTable.id eq id }) {
                            it.update(ProductosTable.stock, ProductosTable.stock minus reduction)
                        }
                        current - reduction
                    } else {
                        // Opcional: reducir a 0 si no alcanza
                        ProductosTable.update({ ProductosTable.id eq id }) {
                            it[stock] = 0
                        }
                        0
                    }
                }
                call.respond(ReduceStockResponse(true, "Stock reducido", newStock))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, ReduceStockResponse(false, "Error: ${e.message}"))
            }
        }
    }
}
