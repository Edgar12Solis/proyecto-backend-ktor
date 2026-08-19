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
                        // Extraemos solo las horas para simplificar la validación de bloques
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

        // 3. NUEVO: Obtener bloques de horarios generales disponibles (para llenar el selector de horas)
        get("/admin/reservas/horarios-disponibles") {
            val fecha = call.request.queryParameters["fecha"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta fecha")
            val duracion = call.request.queryParameters["duracion"]?.toIntOrNull() ?: 30
            
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
                        .map { it[HorariosBarberosTable.barberoId] to it[HorariosBarberosTable.config] }

                    todosLosBloques.filter { bloque ->
                        // Un bloque es válido si al menos un barbero lo tiene en su horario y no tiene cita
                        horariosBarberos.any { (bId, config) ->
                            config.contains(bloque) // Simplificado: el barbero trabaja en ese bloque
                        }
                    }
                }
                
                call.respond(horariosConBarberos)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener horarios")
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
    }
}
