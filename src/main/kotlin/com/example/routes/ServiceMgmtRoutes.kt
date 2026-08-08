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
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.serviceMgmtRoutes() {
    authenticate("auth-jwt") {

        // 1. Categorías de Servicios
        get("/admin/service-categories") {
            try {
                val categories = transaction {
                    CategoriasServiciosTable.selectAll().map {
                        ServiceCategoryDTO(it[CategoriasServiciosTable.id].value, it[CategoriasServiciosTable.nombre])
                    }
                }
                call.respond(categories)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener categorías")
            }
        }

        post("/admin/service-categories") {
            try {
                val req = call.receive<CategoryCreateRequest>()
                transaction {
                    CategoriasServiciosTable.insert {
                        it[nombre] = req.nombre
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Categoría creada con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 2. Servicios
        get("/admin/services") {
            try {
                val services = transaction {
                    (ServiciosTable innerJoin CategoriasServiciosTable)
                        .selectAll()
                        .map { row ->
                            ServiceDTO(
                                id = row[ServiciosTable.id].value,
                                nombre = row[ServiciosTable.nombre],
                                precio = row[ServiciosTable.precio],
                                duracion = row[ServiciosTable.duracion],
                                activo = row[ServiciosTable.activo],
                                imagenUrl = row[ServiciosTable.imagenUrl],
                                serviceCategory = ServiceCategoryDTO(
                                    id = row[CategoriasServiciosTable.id].value,
                                    nombre = row[CategoriasServiciosTable.nombre]
                                )
                            )
                        }
                }
                call.respond(services)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al listar servicios")
            }
        }

        // POST /admin/services - ALINEADO CON MULTIPART + IMAGEN
        post("/admin/services") {
            try {
                val multipart = call.receiveMultipart()
                var serviceDTO: ServiceDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "service") {
                                // Decodificar el JSON que viene como String en la parte "service"
                                serviceDTO = Json.decodeFromString<ServiceDTO>(part.value)
                            }
                        }
                        is PartData.FileItem -> {
                            if (part.name == "image") {
                                imageBytes = part.provider().readRemaining().readByteArray()
                                extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (serviceDTO == null) {
                    call.respond(HttpStatusCode.OK, AdminActionResponse(false, "No se recibió la información del servicio"))
                    return@post
                }

                var finalImageUrl: String? = null

                // 1. Guardar la imagen físicamente si existe
                if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                    val uploadDir = java.io.File("uploads/services")
                    if (!uploadDir.exists()) uploadDir.mkdirs()
                    
                    val fileName = "service_${System.currentTimeMillis()}.$extension"
                    val file = java.io.File(uploadDir, fileName)
                    file.writeBytes(imageBytes!!)
                    
                    finalImageUrl = "https://proyecto-backend-ktor-production.up.railway.app/uploads/services/$fileName"
                }

                // 2. Insertar en la Base de Datos
                transaction {
                    ServiciosTable.insert {
                        it[nombre] = serviceDTO!!.nombre
                        it[precio] = serviceDTO!!.precio
                        it[duracion] = serviceDTO!!.duracion
                        it[activo] = serviceDTO!!.activo
                        it[categoriaId] = serviceDTO!!.serviceCategory.id!!
                        it[imagenUrl] = finalImageUrl ?: serviceDTO!!.imagenUrl
                    }
                }

                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Servicio creado con éxito"))

            } catch (e: Exception) {
                println("❌ Error creando servicio multipart: ${e.message}")
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al crear el servicio: ${e.message}"))
            }
        }
        
        put("/admin/services/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val req = call.receive<ServiceDTO>()
                transaction {
                    ServiciosTable.update({ ServiciosTable.id eq id }) {
                        it[nombre] = req.nombre
                        it[precio] = req.precio
                        it[duracion] = req.duracion
                        it[activo] = req.activo
                        it[categoriaId] = req.serviceCategory.id!!
                    }
                }
                call.respond(AdminActionResponse(true, "Servicio actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 3. Promociones
        get("/admin/promotions") {
            try {
                val promos = transaction {
                    PromocionesTable.selectAll().map { row ->
                        PromotionDTO(
                            id = row[PromocionesTable.id].value,
                            nombre = row[PromocionesTable.nombre],
                            descripcion = row[PromocionesTable.descripcion],
                            precioOriginal = row[PromocionesTable.precioOriginal],
                            precioPromocional = row[PromocionesTable.precioPromocional],
                            activo = row[PromocionesTable.activo],
                            fechaInicio = row[PromocionesTable.fechaInicio],
                            fechaFinal = row[PromocionesTable.fechaFinal]
                        )
                    }
                }
                call.respond(promos)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al listar promociones")
            }
        }

        post("/admin/promotions") {
            try {
                val req = call.receive<PromotionDTO>()
                transaction {
                    val promoId = PromocionesTable.insertAndGetId {
                        it[nombre] = req.nombre
                        it[descripcion] = req.descripcion
                        it[precioOriginal] = req.precioOriginal
                        it[precioPromocional] = req.precioPromocional
                        it[activo] = req.activo
                        it[fechaInicio] = req.fechaInicio
                        it[fechaFinal] = req.fechaFinal
                    }
                    
                    req.selectedServiceIds?.forEach { sId ->
                        PromocionServiciosTable.insert {
                            it[promocionId] = promoId
                            it[servicioId] = sId
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Promoción creada"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        post("/admin/promotions/{id}/toggle") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                transaction {
                    val current = PromocionesTable.selectAll().where { PromocionesTable.id eq id }.single()[PromocionesTable.activo]
                    PromocionesTable.update({ PromocionesTable.id eq id }) {
                        it[activo] = !current
                    }
                }
                call.respond(AdminActionResponse(true, "Estado cambiado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error"))
            }
        }

        delete("/admin/promotions/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id != null) {
                transaction { PromocionesTable.deleteWhere { PromocionesTable.id eq id } }
                call.respond(AdminActionResponse(true, "Promoción eliminada"))
            }
        }
    }
}
