package com.example.routes

import com.example.data.*
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Route.clienteRutas() {
    authenticate("auth-jwt") {

        // 1. Historial de Citas del Cliente (Iniciada Sesión)
        get("/client/appointments") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)

            try {
                val citas = transaction {
                    val userId = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()[UsuariosTable.id].value
                    
                    CitasTable.selectAll().where { CitasTable.usuarioId eq userId }
                        .orderBy(CitasTable.date to SortOrder.DESC, CitasTable.startTime to SortOrder.DESC)
                        .map { row ->
                            ClienteCitaHistorial(
                                id = row[CitasTable.id].value,
                                fecha = row[CitasTable.date],
                                horaInicio = row[CitasTable.startTime],
                                status = row[CitasTable.status],
                                servicioNombre = row[CitasTable.serviceName],
                                precio = row[CitasTable.totalPrice]
                            )
                        }
                }
                call.respond(citas)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener historial")
            }
        }

        // 2. Disponibilidad de un Barbero Específico (Cuadritos de Horas)
        get("/client/barbers/{id}/availability") {
            val barberoId = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "ID inválido")
            val fecha = call.request.queryParameters["date"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta fecha")
            
            try {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val bloquesPosibles = listOf(
                    "10:00", "10:30", "11:00", "11:30", "12:00", "12:30",
                    "13:00", "13:30", "14:00", "14:30", "15:00", "15:30",
                    "16:00", "16:30", "17:00", "17:30", "18:00", "18:30",
                    "19:00", "19:30"
                )

                val horasLibres = transaction {
                    val config = HorariosBarberosTable.selectAll()
                        .where { HorariosBarberosTable.barberoId eq barberoId }
                        .singleOrNull()?.get(HorariosBarberosTable.config) ?: ""

                    val citasDelDia = CitasTable.selectAll()
                        .where { (CitasTable.barberoId eq barberoId) and (CitasTable.date eq fecha) and (CitasTable.status eq "Programada") }
                        .map { 
                            val start = LocalTime.parse(it[CitasTable.startTime], formatter)
                            val end = start.plusMinutes(it[CitasTable.duracion].toLong())
                            start to end
                        }

                    bloquesPosibles.filter { bloque ->
                        val currentBlock = LocalTime.parse(bloque, formatter)
                        // Trabaja en este bloque?
                        if (!config.contains(bloque)) return@filter false
                        // Choca con cita?
                        citasDelDia.none { (start, end) ->
                            !currentBlock.isBefore(start) && currentBlock.isBefore(end)
                        }
                    }
                }
                call.respond(horasLibres)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        // 3. Crear Reserva (Guardar en tabla de admin)
        post("/client/booking") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            try {
                val req = call.receive<ClienteReservaRequest>()
                
                transaction {
                    val userId = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()[UsuariosTable.id].value
                    
                    var nombreServicio = req.servicioNombre ?: "Servicio Especial"
                    var precioServicio = 0.0
                    var duracionServicio = 30
                    var finalBarberoId = req.barberoId

                    // A) Resolver ID de Barbero si solo mandan el nombre
                    if (finalBarberoId == null && req.barberoNombre != null) {
                        finalBarberoId = UsuariosTable.selectAll()
                            .where { (UsuariosTable.nombre eq req.barberoNombre!!) and (UsuariosTable.rol eq "BARBERO") }
                            .singleOrNull()?.get(UsuariosTable.id)?.value
                    }

                    // B) Resolver datos del servicio
                    if (req.servicioId != null) {
                        val s = ServiciosTable.selectAll().where { ServiciosTable.id eq req.servicioId }.singleOrNull()
                        if (s != null) {
                            nombreServicio = s[ServiciosTable.nombre]
                            precioServicio = s[ServiciosTable.precio]
                            duracionServicio = s[ServiciosTable.duracion]
                        }
                    } else if (req.promocionId != null) {
                        val p = PromocionesTable.selectAll().where { PromocionesTable.id eq req.promocionId }.singleOrNull()
                        if (p != null) {
                            nombreServicio = p[PromocionesTable.nombre]
                            precioServicio = p[PromocionesTable.precioPromocional]
                            duracionServicio = 60
                        }
                    }

                    if (finalBarberoId == null) throw Exception("No se pudo identificar al barbero")

                    CitasTable.insert {
                        it[usuarioId] = userId
                        it[barberoId] = finalBarberoId!!
                        it[serviceName] = nombreServicio
                        it[date] = req.fecha ?: java.time.LocalDate.now().toString()
                        it[startTime] = req.horaInicio ?: "10:00"
                        it[duracion] = duracionServicio
                        it[totalPrice] = precioServicio
                        it[status] = "pending"
                        it[metodoPago] = "Efectivo" // Por defecto como pediste
                    }
                }
                call.respond(AdminActionResponse(true, "Cita agendada correctamente"))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al agendar: ${e.message}"))
            }
        }
    }
}
