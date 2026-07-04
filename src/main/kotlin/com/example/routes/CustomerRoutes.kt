package com.example.routes

import com.example.data.*
import com.example.models.*
import com.example.plugins.PasswordHasher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.customerRoutes() {
    authenticate("auth-jwt") {
        
        get("/customer/dashboard-data") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val data = transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    val customerName = user[UsuariosTable.nombre]
                    
                    val appointments = CitasTable.selectAll()
                        .where { CitasTable.usuarioId eq userId }
                        .orderBy(CitasTable.id to SortOrder.DESC)
                        .limit(5)
                        .map {
                            AppointmentDTO(
                                serviceName = it[CitasTable.serviceName],
                                date = it[CitasTable.date],
                                time = it[CitasTable.time],
                                totalPrice = it[CitasTable.totalPrice],
                                status = it[CitasTable.status]
                            )
                        }
                    
                    DashboardDataResponse(customerName, appointments)
                }
                call.respond(data)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener datos del dashboard")
            }
        }
        
        get("/customer/profile") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val profile = transaction {
                    // Cruce de tablas (Join) entre usuarios y perfiles_clientes
                    (UsuariosTable innerJoin PerfilesClientesTable)
                        .selectAll()
                        .where { UsuariosTable.email eq email }
                        .map { row ->
                            UserProfileResponse(
                                nombres = row[PerfilesClientesTable.nombres],
                                apellidos = row[PerfilesClientesTable.apellidos],
                                email = row[UsuariosTable.email],
                                telefono = row[PerfilesClientesTable.telefono],
                                fechaNacimiento = row[PerfilesClientesTable.fechaNacimiento],
                                direccion = row[PerfilesClientesTable.direccion]
                            )
                        }.singleOrNull()
                }

                if (profile != null) {
                    call.respond(profile)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("mensaje" to "Perfil no encontrado"))
                }
            } catch (e: Exception) {
                println("Error profile: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener perfil")
            }
        }
        
        put("/customer/profile/update") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val req = call.receive<UpdateProfileRequest>()
                
                // Validación básica de campos obligatorios
                if (req.nombres.isBlank() || req.apellidos.isBlank() || req.telefono.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to "Nombres, apellidos y teléfono son obligatorios"))
                    return@put
                }

                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    
                    // 1. Actualizar Usuario (nombre completo y password opcional)
                    UsuariosTable.update({ UsuariosTable.id eq userId }) {
                        it[UsuariosTable.nombre] = "${req.nombres} ${req.apellidos}"
                        if (!req.password.isNullOrBlank()) {
                            it[UsuariosTable.password] = PasswordHasher.hash(req.password)
                        }
                    }
                    
                    // 2. Actualizar Perfil Detallado
                    PerfilesClientesTable.update({ PerfilesClientesTable.usuarioId eq userId }) {
                        it[PerfilesClientesTable.nombres] = req.nombres
                        it[PerfilesClientesTable.apellidos] = req.apellidos
                        it[PerfilesClientesTable.telefono] = req.telefono
                        it[PerfilesClientesTable.fechaNacimiento] = req.fechaNacimiento
                        it[PerfilesClientesTable.direccion] = req.direccion
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Perfil actualizado correctamente"))
            } catch (e: Exception) {
                println("❌ Error al actualizar perfil: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("success" to false, "message" to "Error al actualizar el perfil"))
            }
        }
    }
}
