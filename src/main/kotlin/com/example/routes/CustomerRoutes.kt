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
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

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
                                direccion = row[PerfilesClientesTable.direccion],
                                imagenUrl = row[UsuariosTable.imagenUrl]
                            )
                        }.singleOrNull()
                }

                if (profile != null) call.respond(profile)
                else call.respond(HttpStatusCode.NotFound, mapOf("mensaje" to "Perfil no encontrado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener perfil")
            }
        }
        
        put("/customer/profile/update") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val req = call.receive<UpdateProfileRequest>()
                
                if (req.nombres.isBlank() || req.apellidos.isBlank() || req.telefono.isBlank()) {
                    call.respond(HttpStatusCode.OK, UpdateProfileResponse(false, "Campos obligatorios vacíos"))
                    return@put
                }

                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    
                    UsuariosTable.update({ UsuariosTable.id eq userId }) {
                        it[UsuariosTable.nombre] = "${req.nombres} ${req.apellidos}"
                        if (!req.password.isNullOrBlank()) it[UsuariosTable.password] = PasswordHasher.hash(req.password)
                    }
                    
                    PerfilesClientesTable.update({ PerfilesClientesTable.usuarioId eq userId }) {
                        it[PerfilesClientesTable.nombres] = req.nombres
                        it[PerfilesClientesTable.apellidos] = req.apellidos
                        it[PerfilesClientesTable.telefono] = req.telefono
                        it[PerfilesClientesTable.fechaNacimiento] = req.fechaNacimiento
                        it[PerfilesClientesTable.direccion] = req.direccion
                    }
                }
                call.respond(HttpStatusCode.OK, UpdateProfileResponse(true, "Perfil actualizado correctamente"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, UpdateProfileResponse(false, "Error al actualizar perfil"))
            }
        }

        post("/customer/profile/photo") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val multipart = call.receiveMultipart()
                var fileBytes: ByteArray? = null
                var fileName = ""

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem && part.name == "image") {
                        fileBytes = part.provider().readRemaining().readByteArray()
                        val extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        fileName = "profile_${System.currentTimeMillis()}.$extension"
                    }
                    part.dispose()
                }

                if (fileBytes != null) {
                    val uploadDir = java.io.File("uploads")
                    if (!uploadDir.exists()) uploadDir.mkdirs()
                    java.io.File(uploadDir, fileName).writeBytes(fileBytes!!)

                    val publicUrl = "https://proyecto-backend-ktor-production.up.railway.app/uploads/$fileName"
                    transaction {
                        UsuariosTable.update({ UsuariosTable.email eq email }) {
                            it[UsuariosTable.imagenUrl] = publicUrl
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "imageUrl" to publicUrl))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf("success" to false, "message" to "No se recibió imagen"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, mapOf("success" to false, "message" to "Error al subir foto"))
            }
        }

        // 5. Agendar Cita (Cliente) - Alineado con BookingRequest y CartItems
        post("/client/booking") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""

            try {
                val req = call.receive<BookingRequest>()
                val today = LocalDate.now().toString()
                
                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]

                    // Procesamos el carrito
                    req.cartItems.forEach { item ->
                        if (item.type == "service") {
                            CitasTable.insert {
                                it[usuarioId] = userId
                                it[barberoId] = EntityID(req.barberId, UsuariosTable)
                                it[serviceName] = item.name
                                it[date] = req.date ?: today
                                it[startTime] = req.startTime ?: "00:00"
                                it[totalPrice] = item.price
                                it[status] = "pending"
                            }
                        }
                        // Si es producto, podríamos registrar una venta asociada aquí también
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("success" to true, "message" to "Cita agendada con éxito"))
            } catch (e: Exception) {
                println("❌ Error booking: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to "Error al agendar: ${e.message}"))
            }
        }

        get("/client/appointments") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""

            try {
                val appointments = transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userId = user[UsuariosTable.id]
                    val barberoAlias = UsuariosTable.alias("barbero")

                    (CitasTable innerJoin barberoAlias)
                        .selectAll()
                        .where { CitasTable.usuarioId eq userId }
                        .orderBy(CitasTable.date to SortOrder.DESC)
                        .map { row ->
                            ClientAppointmentResponse(
                                id = row[CitasTable.id].value,
                                date = row[CitasTable.date],
                                startTime = row[CitasTable.startTime],
                                status = row[CitasTable.status],
                                serviceName = row[CitasTable.serviceName],
                                totalPrice = row[CitasTable.totalPrice],
                                barberName = row[barberoAlias[UsuariosTable.nombre]]
                            )
                        }
                }
                call.respond(appointments)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener citas")
            }
        }
    }
}
