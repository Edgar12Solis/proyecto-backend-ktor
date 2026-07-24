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
                                time = it[CitasTable.startTime],
                                totalPrice = it[CitasTable.totalPrice],
                                status = it[CitasTable.status]
                            )
                        }
                    
                    DashboardDataResponse(customerName, appointments)
                }
                call.respond(data)
            } catch (e: Exception) {
                println("Error dashboard: ${e.message}")
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
                
                // Validación básica
                if (req.nombres.isBlank() || req.apellidos.isBlank() || req.telefono.isBlank()) {
                    call.respond(
                        HttpStatusCode.OK, 
                        UpdateProfileResponse(success = false, message = "Nombres, apellidos y teléfono son obligatorios")
                    )
                    return@put
                }

                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    
                    // 1. Actualizar Usuario
                    UsuariosTable.update({ UsuariosTable.id eq userId }) {
                        it[UsuariosTable.nombre] = "${req.nombres} ${req.apellidos}"
                        if (!req.password.isNullOrBlank()) {
                            it[UsuariosTable.password] = PasswordHasher.hash(req.password)
                        }
                    }
                    
                    // 2. Actualizar Perfil
                    PerfilesClientesTable.update({ PerfilesClientesTable.usuarioId eq userId }) {
                        it[PerfilesClientesTable.nombres] = req.nombres
                        it[PerfilesClientesTable.apellidos] = req.apellidos
                        it[PerfilesClientesTable.telefono] = req.telefono
                        it[PerfilesClientesTable.fechaNacimiento] = req.fechaNacimiento
                        it[PerfilesClientesTable.direccion] = req.direccion
                    }
                }
                call.respond(
                    HttpStatusCode.OK, 
                    UpdateProfileResponse(success = true, message = "Perfil actualizado correctamente")
                )
            } catch (e: Exception) {
                println("Error al actualizar perfil: ${e.message}")
                call.respond(
                    HttpStatusCode.OK, 
                    UpdateProfileResponse(success = false, message = "Error al actualizar el perfil")
                )
            }
        }

        // 4. Subir Foto de Perfil (Con URL Absoluta para que se vea de verdad)
        post("/customer/profile/photo") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                // Recibimos los bytes de la imagen directamente
                val bytes = call.receive<ByteArray>()
                if (bytes.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to "No se recibieron datos de imagen"))
                    return@post
                }

                // Generar nombre de archivo único
                val fileName = "profile_${System.currentTimeMillis()}.jpg"
                val uploadDir = java.io.File("uploads/profiles")
                if (!uploadDir.exists()) uploadDir.mkdirs()
                
                val file = java.io.File(uploadDir, fileName)
                file.writeBytes(bytes)

                // IMPORTANTE: Construir la URL completa (Absoluta)
                // Esto permite que la App móvil pueda cargar la foto desde Internet
                val host = call.request.local.serverHost
                val port = call.request.local.serverPort
                val scheme = call.request.local.scheme
                
                // Si estamos en producción (Railway), el puerto no suele ir en la URL
                val publicUrl = if (host.contains("localhost")) {
                    "$scheme://$host:$port/uploads/profiles/$fileName"
                } else {
                    "$scheme://$host/uploads/profiles/$fileName"
                }

                transaction {
                    UsuariosTable.update({ UsuariosTable.email eq email }) {
                        it[imagenUrl] = publicUrl
                    }
                }

                println("✅ Foto guardada de verdad: $publicUrl")
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true, 
                    "message" to "Foto actualizada con éxito", 
                    "imageUrl" to publicUrl
                ))
            } catch (e: Exception) {
                println("❌ Error crítico al guardar foto: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("success" to false, "message" to "Error al guardar la imagen en el servidor"))
            }
        }
    }
}
