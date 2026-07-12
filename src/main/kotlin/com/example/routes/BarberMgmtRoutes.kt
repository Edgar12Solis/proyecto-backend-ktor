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
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.barberMgmtRoutes() {
    authenticate("auth-jwt") {

        // 1. Listar Barberos y Estadísticas
        get("/admin/barbers") {
            try {
                val barbers = transaction {
                    (UsuariosTable leftJoin PerfilesBarberosTable)
                        .selectAll()
                        .where { UsuariosTable.rol eq "BARBERO" }
                        .map { row ->
                            BarberFullProfileResponse(
                                id = row[UsuariosTable.id].value,
                                nombreCompleto = row[UsuariosTable.nombre],
                                email = row[UsuariosTable.email],
                                telefono = "", // Podríamos agregarlo a PerfilesBarberos si fuera necesario
                                bio = row.getOrNull(PerfilesBarberosTable.biografia) ?: "",
                                activo = row[UsuariosTable.activo],
                                specialties = listOf(row.getOrNull(PerfilesBarberosTable.especialidad) ?: "")
                            )
                        }
                }
                call.respond(barbers)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al listar barberos")
            }
        }

        get("/admin/barbers/stats") {
            try {
                val stats = transaction {
                    val total = UsuariosTable.selectAll().where { UsuariosTable.rol eq "BARBERO" }.count().toInt()
                    val active = UsuariosTable.selectAll().where { (UsuariosTable.rol eq "BARBERO") and (UsuariosTable.activo eq true) }.count().toInt()
                    BarberStats(total, active)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener estadísticas")
            }
        }

        // 2. Cambiar Estado (Activar/Desactivar)
        post("/admin/barbers/{id}/status") {
            val id = call.parameters["id"]?.toIntOrNull()
            val active = call.request.queryParameters["active"]?.toBoolean() ?: true
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@post
            }

            try {
                transaction {
                    UsuariosTable.update({ UsuariosTable.id eq id }) {
                        it[activo] = active
                    }
                }
                call.respond(AdminActionResponse(true, "Estado actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al actualizar estado")
            }
        }

        // 3. Actualizar Horario
        put("/admin/barbers/{id}/schedule") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@put
            }
            try {
                val req = call.receive<BarberScheduleRequest>()
                transaction {
                    val exists = HorariosBarberosTable.selectAll().where { HorariosBarberosTable.barberoId eq id }.count() > 0
                    if (exists) {
                        HorariosBarberosTable.update({ HorariosBarberosTable.barberoId eq id }) {
                            it[config] = req.config
                        }
                    } else {
                        HorariosBarberosTable.insert {
                            it[barberoId] = id
                            it[config] = req.config
                        }
                    }
                }
                call.respond(AdminActionResponse(true, "Horario actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error al actualizar horario")
            }
        }

        // 4. Editar Perfil de Barbero
        put("/admin/barbers/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@put
            }
            try {
                val req = call.receive<BarberFullProfileResponse>()
                transaction {
                    UsuariosTable.update({ UsuariosTable.id eq id }) {
                        it[nombre] = req.nombreCompleto
                        it[email] = req.email
                        it[activo] = req.activo
                    }
                    
                    val pExists = PerfilesBarberosTable.selectAll().where { PerfilesBarberosTable.usuarioId eq id }.count() > 0
                    if (pExists) {
                        PerfilesBarberosTable.update({ PerfilesBarberosTable.usuarioId eq id }) {
                            it[especialidad] = req.specialties.firstOrNull() ?: ""
                            it[biografia] = req.bio
                        }
                    } else {
                        PerfilesBarberosTable.insert {
                            it[usuarioId] = id
                            it[especialidad] = req.specialties.firstOrNull() ?: ""
                            it[biografia] = req.bio
                        }
                    }
                }
                call.respond(AdminActionResponse(true, "Perfil de barbero actualizado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error al editar perfil")
            }
        }
    }
}
