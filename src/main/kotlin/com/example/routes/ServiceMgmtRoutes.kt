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
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.serviceMgmtRoutes() {
    authenticate("auth-jwt") {

        // 1. Servicios y Categorías
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

        post("/admin/services") {
            try {
                val req = call.receive<ServiceDTO>()
                transaction {
                    ServiciosTable.insert {
                        it[nombre] = req.nombre
                        it[precio] = req.precio
                        it[duracion] = req.duracion
                        it[activo] = req.activo
                        it[categoriaId] = req.serviceCategory.id
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Servicio creado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 2. Promociones
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
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        post("/admin/promotions/{id}/toggle") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@post
            }
            try {
                transaction {
                    val current = PromocionesTable.selectAll().where { PromocionesTable.id eq id }.single()[PromocionesTable.activo]
                    PromocionesTable.update({ PromocionesTable.id eq id }) {
                        it[activo] = !current
                    }
                }
                call.respond(AdminActionResponse(true, "Estado de promoción cambiado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
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
