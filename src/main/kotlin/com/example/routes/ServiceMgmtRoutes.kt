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
import java.time.LocalDate

fun Route.serviceMgmtRoutes() {
    authenticate("auth-jwt") {

        // 1. Estadísticas (Reparación de Contadores)
        get("/admin/services/stats") {
            try {
                val stats = transaction {
                    val totalServices = ServiciosTable.selectAll().count().toInt()
                    val totalPromotions = PromocionesTable.selectAll().count().toInt()
                    ServiceStats(totalServices, totalPromotions)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, mapOf("totalServices" to 0, "totalPromotions" to 0))
            }
        }

        // 2. Categorías de Servicios
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
                    CategoriasServiciosTable.insert { it[nombre] = req.nombre }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Categoría creada"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error"))
            }
        }

        // 3. Servicios (Asegurando imagen_url y snake_case)
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
                                descripcion = row[ServiciosTable.descripcion],
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
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        post("/admin/services") {
            try {
                val multipart = call.receiveMultipart()
                var serviceDTO: ServiceDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "service") {
                            serviceDTO = Json { ignoreUnknownKeys = true }.decodeFromString<ServiceDTO>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (serviceDTO == null) return@post call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl = serviceDTO!!.imagenUrl
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "service_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/services/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val baseUrl = call.request.local.run { "http://$localHost:$localPort" }
                        // En producción railway suele usar HTTPS y un host específico, pero localHost ayuda en desarrollo.
                        // Para producción railway, es mejor usar la URL de la app.
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/services/$fileName"
                    }

                    ServiciosTable.insert {
                        it[nombre] = serviceDTO!!.nombre
                        it[precio] = serviceDTO!!.precio
                        it[duracion] = serviceDTO!!.duracion
                        it[activo] = serviceDTO!!.activo ?: true
                        it[descripcion] = serviceDTO!!.descripcion
                        it[categoriaId] = serviceDTO!!.serviceCategory.id!!
                        it[imagenUrl] = finalImageUrl
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Servicio creado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        put("/admin/services/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val multipart = call.receiveMultipart()
                var serviceDTO: ServiceDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "service") {
                            serviceDTO = Json { ignoreUnknownKeys = true }.decodeFromString<ServiceDTO>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (serviceDTO == null) return@put call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl: String? = null
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "service_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/services/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/services/$fileName"
                    }

                    ServiciosTable.update({ ServiciosTable.id eq id }) {
                        it[nombre] = serviceDTO!!.nombre
                        it[precio] = serviceDTO!!.precio
                        it[duracion] = serviceDTO!!.duracion
                        it[activo] = serviceDTO!!.activo ?: true
                        it[descripcion] = serviceDTO!!.descripcion
                        it[categoriaId] = serviceDTO!!.serviceCategory.id!!
                        if (finalImageUrl != null) it[imagenUrl] = finalImageUrl
                    }
                }
                call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Servicio actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error"))
            }
        }

        delete("/admin/services/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                transaction {
                    ServiciosTable.deleteWhere { ServiciosTable.id eq id }
                }
                call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Servicio eliminado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al eliminar servicio"))
            }
        }

        // 4. Promociones (Validación de Fecha y snake_case)
        get("/admin/promotions") {
            try {
                val promos = transaction {
                    PromocionesTable.selectAll().map { row ->
                        val promoId = row[PromocionesTable.id].value
                        val serviceIds = PromocionServiciosTable
                            .selectAll().where { PromocionServiciosTable.promocionId eq promoId }
                            .map { it[PromocionServiciosTable.servicioId].value }

                        PromotionDTO(
                            id = promoId,
                            nombre = row[PromocionesTable.nombre],
                            descripcion = row[PromocionesTable.descripcion],
                            precioOriginal = row[PromocionesTable.precioOriginal],
                            precioPromocional = row[PromocionesTable.precioPromocional],
                            activo = row[PromocionesTable.activo],
                            fechaInicio = row[PromocionesTable.fechaInicio],
                            fechaFinal = row[PromocionesTable.fechaFinal],
                            imagenUrl = row[PromocionesTable.imagenUrl],
                            selectedServiceIds = serviceIds
                        )
                    }
                }
                call.respond(promos)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        post("/admin/promotions") {
            try {
                val multipart = call.receiveMultipart()
                var promoDTO: PromotionDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "promotion") {
                            promoDTO = Json { ignoreUnknownKeys = true }.decodeFromString<PromotionDTO>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (promoDTO == null) return@post call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                // VALIDACIÓN DE FECHA: No antes de hoy
                val today = LocalDate.now()
                val startDate = LocalDate.parse(promoDTO!!.fechaInicio)
                if (startDate.isBefore(today)) {
                    return@post call.respond(HttpStatusCode.OK, AdminActionResponse(false, "La fecha de inicio no puede ser anterior a hoy"))
                }

                transaction {
                    var finalImageUrl: String? = null
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "promo_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/promos/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/promos/$fileName"
                    }

                    val promoId = PromocionesTable.insertAndGetId {
                        it[nombre] = promoDTO!!.nombre
                        it[descripcion] = promoDTO!!.descripcion
                        it[precioOriginal] = promoDTO!!.precioOriginal
                        it[precioPromocional] = promoDTO!!.precioPromocional
                        it[activo] = promoDTO!!.activo ?: true
                        it[fechaInicio] = promoDTO!!.fechaInicio
                        it[fechaFinal] = promoDTO!!.fechaFinal
                        it[imagenUrl] = finalImageUrl
                    }
                    
                    promoDTO!!.selectedServiceIds?.forEach { sId ->
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

        put("/admin/promotions/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val multipart = call.receiveMultipart()
                var promoDTO: PromotionDTO? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "promotion") {
                            promoDTO = Json { ignoreUnknownKeys = true }.decodeFromString<PromotionDTO>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (promoDTO == null) return@put call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl: String? = null
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "promo_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/promos/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/promos/$fileName"
                    }

                    PromocionesTable.update({ PromocionesTable.id eq id }) {
                        it[nombre] = promoDTO!!.nombre
                        it[descripcion] = promoDTO!!.descripcion
                        it[precioOriginal] = promoDTO!!.precioOriginal
                        it[precioPromocional] = promoDTO!!.precioPromocional
                        it[activo] = promoDTO!!.activo ?: true
                        it[fechaInicio] = promoDTO!!.fechaInicio
                        it[fechaFinal] = promoDTO!!.fechaFinal
                        if (finalImageUrl != null) it[imagenUrl] = finalImageUrl
                    }

                    if (promoDTO!!.selectedServiceIds != null) {
                        PromocionServiciosTable.deleteWhere { PromocionServiciosTable.promocionId eq id }
                        promoDTO!!.selectedServiceIds!!.forEach { sId ->
                            PromocionServiciosTable.insert {
                                it[promocionId] = id
                                it[servicioId] = sId
                            }
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Promoción actualizada"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error"))
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
