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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Route.reservaRutas() {
    authenticate("auth-jwt") {

        // 1. Obtener barberos disponibles para un horario y duración específica
        post("/admin/reservas/disponibilidad") {
            try {
                val req = call.receive<DisponibilidadRequest>()
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val requestedStart = LocalTime.parse(req.horaInicio, formatter)
                val requestedEnd = requestedStart.plusMinutes(req.duracion.toLong())

                val barberosLibres = transaction {
                    // Obtener todos los barberos activos
                    val todosBarberos = UsuariosTable
                        .selectAll()
                        .where { (UsuariosTable.rol eq "BARBERO") and (UsuariosTable.activo eq true) }
                        .toList()

                    todosBarberos.filter { bRow ->
                        val bId = bRow[UsuariosTable.id].value
                        
                        // A) Validar si el barbero trabaja en ese rango
                        val horarioConfig = HorariosBarberosTable
                            .selectAll().where { HorariosBarberosTable.barberoId eq bId }
                            .singleOrNull()?.get(HorariosBarberosTable.config) ?: ""
                        
                        // Si no hay configuración de horario, no está disponible
                        if (horarioConfig.isEmpty()) return@filter false

                        // El formato de horarioConfig es "1-10:00,2-10:30,..." (DiaID-Hora)
                        val bloquesDisponibles = horarioConfig.split(",").map { it.substringAfter("-") }
                        
                        // El barbero debe tener el bloque de inicio disponible
                        if (!bloquesDisponibles.contains(req.horaInicio)) return@filter false

                        // B) Validar colisiones con citas existentes
                        val citasHoy = CitasTable
                            .selectAll()
                            .where { (CitasTable.barberoId eq bId) and (CitasTable.date eq req.fecha) and (CitasTable.status eq "Programada") }
                            .toList()

                        citasHoy.none { cRow ->
                            val citaStart = LocalTime.parse(cRow[CitasTable.startTime], formatter)
                            val citaEnd = citaStart.plusMinutes(cRow[CitasTable.duracion].toLong())
                            
                            // Lógica de colisión: (inicio1 < fin2) AND (inicio2 < fin1)
                            requestedStart.isBefore(citaEnd) && citaStart.isBefore(requestedEnd)
                        }
                    }.map { bRow ->
                        BarberoDisponibleDTO(
                            id = bRow[UsuariosTable.id].value,
                            nombre = bRow[UsuariosTable.nombre],
                            imagenUrl = bRow[UsuariosTable.imagenUrl]
                        )
                    }
                }
                call.respond(barberosLibres)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error al calcular disponibilidad")
            }
        }

        // 2. Crear la reserva final
        post("/admin/reservas") {
            try {
                val req = call.receive<ReservaCreateRequest>()
                
                val result = transaction {
                    var finalClienteId = req.usuarioId

                    // A) Si es ocasional, crear un registro básico
                    if (req.esOcasional || finalClienteId == null) {
                        finalClienteId = UsuariosTable.insertAndGetId {
                            it[nombre] = req.clienteNombre ?: "Cliente Ocasional"
                            it[email] = "ocasional_${System.currentTimeMillis()}@wolf.com"
                            it[password] = "ocasional123"
                            it[rol] = "CLIENTE"
                            it[activo] = true
                            it[fechaRegistro] = java.time.LocalDate.now().toString()
                            it[bio] = "Cliente ocasional registrado por admin. Tel: ${req.clienteTelefono}"
                        }.value
                        
                        PerfilesClientesTable.insert {
                            it[usuarioId] = finalClienteId!!
                            it[nombres] = req.clienteNombre?.split(" ")?.firstOrNull() ?: "Cliente"
                            it[apellidos] = req.clienteNombre?.split(" ")?.drop(1)?.joinToString(" ") ?: "Ocasional"
                            it[telefono] = req.clienteTelefono ?: "0000000000"
                            it[estado] = "active"
                        }
                    }

                    // B) Insertar la Cita
                    CitasTable.insert {
                        it[usuarioId] = finalClienteId!!
                        it[barberoId] = req.barberoId
                        it[serviceName] = req.servicioNombre
                        it[date] = req.fecha
                        it[startTime] = req.horaInicio
                        it[duracion] = req.duracion
                        it[totalPrice] = req.precio
                        it[status] = "Programada"
                        it[metodoPago] = req.metodoPago
                    }
                    true
                }
                
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Reserva agendada con éxito"))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al agendar: ${e.message}"))
            }
        }

        // 3. Listar bloques de horarios generales disponibles
        get("/admin/reservas/horarios-disponibles") {
            val fecha = call.request.queryParameters["fecha"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta fecha")
            
            try {
                val todosLosBloques = listOf(
                    "10:00", "10:30", "11:00", "11:30", "12:00", "12:30",
                    "13:00", "13:30", "14:00", "14:30", "15:00", "15:30",
                    "16:00", "16:30", "17:00", "17:30", "18:00", "18:30",
                    "19:00", "19:30", "20:00"
                )

                val horariosConBarberos = transaction {
                    val barberosActivos = UsuariosTable.selectAll()
                        .where { (UsuariosTable.rol eq "BARBERO") and (UsuariosTable.activo eq true) }
                        .map { it[UsuariosTable.id].value }

                    val horariosBarberos = HorariosBarberosTable.selectAll()
                        .where { HorariosBarberosTable.barberoId inList barberosActivos }
                        .map { it[HorariosBarberosTable.config] }

                    todosLosBloques.filter { bloque ->
                        horariosBarberos.any { config -> config.contains(bloque) }
                    }
                }
                call.respond(horariosConBarberos)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        // 4. Listar citas por día (Vista Agenda)
        get("/admin/citas/dia") {
            val fecha = call.request.queryParameters["fecha"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta fecha")
            try {
                val citas = transaction {
                    val clienteAlias = UsuariosTable.alias("cliente")
                    val barberoAlias = UsuariosTable.alias("barbero")

                    CitasTable
                        .join(clienteAlias, JoinType.INNER, additionalConstraint = { CitasTable.usuarioId eq clienteAlias[UsuariosTable.id] })
                        .join(barberoAlias, JoinType.INNER, additionalConstraint = { CitasTable.barberoId eq barberoAlias[UsuariosTable.id] })
                        .selectAll()
                        .where { CitasTable.date eq fecha }
                        .map { row ->
                            CitaDetalleDTO(
                                id = row[CitasTable.id].value,
                                clienteNombre = row[clienteAlias[UsuariosTable.nombre]],
                                barberoNombre = row[barberoAlias[UsuariosTable.nombre]],
                                servicioNombre = row[CitasTable.serviceName],
                                fecha = row[CitasTable.date],
                                horaInicio = row[CitasTable.startTime],
                                duracion = row[CitasTable.duracion],
                                precio = row[CitasTable.totalPrice],
                                estado = row[CitasTable.status],
                                metodoPago = row[CitasTable.metodoPago]
                            )
                        }
                }
                println("📅 Citas encontradas para $fecha: ${citas.size}")
                call.respond(citas)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener citas: ${e.message}")
            }
        }

        // 5. Pendientes de hoy (Panel Inferior)
        get("/admin/citas/pendientes-hoy") {
            val hoy = java.time.LocalDate.now().toString()
            try {
                val citas = transaction {
                    val clienteAlias = UsuariosTable.alias("cliente")
                    val barberoAlias = UsuariosTable.alias("barbero")

                    CitasTable
                        .join(clienteAlias, JoinType.INNER, additionalConstraint = { CitasTable.usuarioId eq clienteAlias[UsuariosTable.id] })
                        .join(barberoAlias, JoinType.INNER, additionalConstraint = { CitasTable.barberoId eq barberoAlias[UsuariosTable.id] })
                        .selectAll()
                        .where { (CitasTable.date eq hoy) and (CitasTable.status.lowerCase() eq "programada") }
                        .map { row ->
                            CitaDetalleDTO(
                                id = row[CitasTable.id].value,
                                clienteNombre = row[clienteAlias[UsuariosTable.nombre]],
                                barberoNombre = row[barberoAlias[UsuariosTable.nombre]],
                                servicioNombre = row[CitasTable.serviceName],
                                fecha = row[CitasTable.date],
                                horaInicio = row[CitasTable.startTime],
                                duracion = row[CitasTable.duracion],
                                precio = row[CitasTable.totalPrice],
                                estado = row[CitasTable.status],
                                metodoPago = row[CitasTable.metodoPago]
                            )
                        }
                }
                call.respond(citas)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        // 6. Cambiar Estado (Completar/Cancelar)
        post("/admin/citas/{id}/estado") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "ID inválido")
            val req = call.receive<Map<String, String>>()
            val nuevoEstado = req["estado"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta estado")

            try {
                transaction {
                    CitasTable.update({ CitasTable.id eq id }) {
                        it[status] = nuevoEstado
                    }
                }
                call.respond(AdminActionResponse(true, "Estado actualizado: $nuevoEstado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error"))
            }
        }

        // 7. Reprogramar Cita
        put("/admin/citas/{id}/reprogramar") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val req = call.receive<CitaReprogramarRequest>()
                transaction {
                    CitasTable.update({ CitasTable.id eq id }) {
                        it[date] = req.fecha
                        it[startTime] = req.horaInicio
                        if (req.barberoId != null) it[barberoId] = req.barberoId
                        it[status] = "Programada"
                    }
                }
                call.respond(AdminActionResponse(true, "Cita reprogramada con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error"))
            }
        }
    }
}
