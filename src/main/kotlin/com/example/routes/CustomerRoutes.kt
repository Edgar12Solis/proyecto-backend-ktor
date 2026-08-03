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
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
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

        // 4. Subir Foto de Perfil (Multipart - Versión Robusta con Logs)
        post("/customer/profile/photo") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            println("📸 Iniciando subida de foto para: $email")
            
            try {
                val multipart = call.receiveMultipart()
                var fileName = ""
                var fileBytes: ByteArray? = null

                multipart.forEachPart { part ->
                    println("📦 Procesando parte: ${part.name}")
                    if (part is PartData.FileItem && part.name == "image") {
                        fileBytes = part.provider().readRemaining().readByteArray()
                        val extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        fileName = "profile_${System.currentTimeMillis()}.$extension"
                        println("📄 Archivo detectado: $fileName (${fileBytes?.size} bytes)")
                    }
                    part.dispose()
                }

                if (fileBytes == null || fileBytes!!.isEmpty()) {
                    println("❌ Error: No se recibió ningún archivo en la parte 'image'")
                    call.respond(HttpStatusCode.OK, UpdateProfileResponse(false, "No se seleccionó ninguna imagen"))
                    return@post
                }

                // Guardar archivo físicamente
                val uploadDir = java.io.File("uploads/profiles")
                if (!uploadDir.exists()) {
                    val created = uploadDir.mkdirs()
                    println("📂 Creando directorio de subidas: $created")
                }
                
                val file = java.io.File(uploadDir, fileName)
                file.writeBytes(fileBytes!!)
                println("💾 Archivo guardado físicamente en: ${file.absolutePath}")

                // Generar URL para el Frontend
                val publicUrl = "https://proyecto-backend-ktor-production.up.railway.app/uploads/profiles/$fileName"

                transaction {
                    UsuariosTable.update({ UsuariosTable.email eq email }) {
                        it[UsuariosTable.imagenUrl] = publicUrl
                    }
                }

                println("✅ Base de datos actualizada con: $publicUrl")
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true, 
                    "message" to "Foto de perfil actualizada correctamente", 
                    "imageUrl" to publicUrl
                ))

            } catch (e: Exception) {
                println("❌ ERROR CRÍTICO en subida de foto: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.OK, UpdateProfileResponse(false, "Error técnico al guardar la foto"))
            }
        }
    }
}
