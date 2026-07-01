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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.customerRoutes() {
    authenticate("auth-jwt") {
        
        get("/customer/dashboard-data") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal!!.payload.getClaim("email").asString()
            
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
            val email = principal!!.payload.getClaim("email").asString()
            
            try {
                val profile = transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    val profileEntry = PerfilesClientesTable.selectAll().where { PerfilesClientesTable.usuarioId eq userId }.single()
                    
                    UserProfileResponse(
                        nombres = profileEntry[PerfilesClientesTable.nombres],
                        apellidos = profileEntry[PerfilesClientesTable.apellidos],
                        email = user[UsuariosTable.email],
                        telefono = profileEntry[PerfilesClientesTable.telefono],
                        fechaNacimiento = profileEntry[PerfilesClientesTable.fechaNacimiento],
                        direccion = profileEntry[PerfilesClientesTable.direccion]
                    )
                }
                call.respond(profile)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener perfil")
            }
        }
        
        post("/customer/update-profile") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal!!.payload.getClaim("email").asString()
            val req = call.receive<UpdateProfileRequest>()
            
            try {
                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    
                    // Actualizar Usuario (nombre y password si viene)
                    UsuariosTable.update({ UsuariosTable.id eq userId }) {
                        it[nombre] = "${req.nombres} ${req.apellidos}"
                        if (!req.password.isNullOrBlank()) {
                            it[password] = PasswordHasher.hash(req.password)
                        }
                    }
                    
                    // Actualizar Perfil
                    PerfilesClientesTable.update({ PerfilesClientesTable.usuarioId eq userId }) {
                        it[nombres] = req.nombres
                        it[apellidos] = req.apellidos
                        it[telefono] = req.telefono
                        it[fechaNacimiento] = req.fechaNacimiento
                        it[direccion] = req.direccion
                    }
                }
                call.respond(mapOf("success" to true, "message" to "Perfil actualizado con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("success" to false, "message" to "Error al actualizar perfil"))
            }
        }
    }
}
