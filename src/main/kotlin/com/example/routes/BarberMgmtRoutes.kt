package com.example.routes

import com.example.data.*
import com.example.models.*
import com.example.plugins.PasswordHasher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.barberMgmtRoutes() {
    authenticate("auth-jwt") {

        // 1. Listar Barberos con Especialidades y Horario
        get("/admin/barbers") {
            try {
                val barbers = transaction {
                    UsuariosTable
                        .selectAll()
                        .where { UsuariosTable.rol eq "BARBERO" }
                        .map { row ->
                            val bId = row[UsuariosTable.id].value
                            
                            // Obtener nombres de especialidades uniendo con CategoriasServiciosTable
                            val specs = (BarberoEspecialidadesTable innerJoin CategoriasServiciosTable)
                                .selectAll()
                                .where { BarberoEspecialidadesTable.usuarioId eq bId }
                                .map { it[CategoriasServiciosTable.nombre] }

                            BarberFullProfileResponse(
                                id = bId,
                                nombreCompleto = row[UsuariosTable.nombre],
                                email = row[UsuariosTable.email],
                                telefono = "", // Podría agregarse una tabla de perfil si es necesario
                                bio = row[UsuariosTable.bio] ?: "",
                                activo = row[UsuariosTable.activo],
                                scheduleConfiguration = row[UsuariosTable.scheduleConfig] ?: "",
                                specialties = specs
                            )
                        }
                }
                call.respond(barbers)
            } catch (e: Exception) {
                println("❌ Error listing barbers: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error al listar barberos")
            }
        }

        // 2. Crear Nuevo Barbero
        post("/admin/barbers") {
            try {
                val req = call.receive<BarberCreateRequest>()
                transaction {
                    val userId = UsuariosTable.insertAndGetId {
                        it[nombre] = req.nombreCompleto
                        it[email] = req.email
                        it[password] = PasswordHasher.hash("barber123") // Password por defecto
                        it[rol] = "BARBERO"
                        it[bio] = req.bio
                        it[scheduleConfig] = req.scheduleConfiguration
                        it[activo] = req.activo
                    }

                    // Vincular especialidades por nombre
                    req.specialties.forEach { specName ->
                        val catId = CategoriasServiciosTable
                            .selectAll()
                            .where { CategoriasServiciosTable.nombre eq specName }
                            .singleOrNull()?.get(CategoriasServiciosTable.id)

                        if (catId != null) {
                            BarberoEspecialidadesTable.insert {
                                it[usuarioId] = userId
                                it[categoriaId] = catId
                            }
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Barbero creado con éxito"))
            } catch (e: Exception) {
                println("❌ Error creating barber: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 3. Editar Barbero Existente
        put("/admin/barbers/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@put
            }
            try {
                val req = call.receive<BarberCreateRequest>()
                transaction {
                    // Actualizar datos básicos
                    UsuariosTable.update({ UsuariosTable.id eq id }) {
                        it[nombre] = req.nombreCompleto
                        it[email] = req.email
                        it[bio] = req.bio
                        it[scheduleConfig] = req.scheduleConfiguration
                        it[activo] = req.activo
                    }

                    // Actualizar especialidades: Borrar antiguas y poner las nuevas
                    BarberoEspecialidadesTable.deleteWhere { usuarioId eq id }
                    
                    req.specialties.forEach { specName ->
                        val catId = CategoriasServiciosTable
                            .selectAll()
                            .where { CategoriasServiciosTable.nombre eq specName }
                            .singleOrNull()?.get(CategoriasServiciosTable.id)

                        if (catId != null) {
                            BarberoEspecialidadesTable.insert {
                                it[usuarioId] = id
                                it[categoriaId] = catId
                            }
                        }
                    }
                }
                call.respond(AdminActionResponse(true, "Barbero actualizado correctamente"))
            } catch (e: Exception) {
                println("❌ Error editing barber: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error al editar barbero"))
            }
        }

        // 4. Estadísticas rápidas
        get("/admin/barbers/stats") {
            try {
                val stats = transaction {
                    val total = UsuariosTable.selectAll().where { UsuariosTable.rol eq "BARBERO" }.count().toInt()
                    val active = UsuariosTable.selectAll().where { (UsuariosTable.rol eq "BARBERO") and (UsuariosTable.activo eq true) }.count().toInt()
                    BarberStats(total, active)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }
    }
}
