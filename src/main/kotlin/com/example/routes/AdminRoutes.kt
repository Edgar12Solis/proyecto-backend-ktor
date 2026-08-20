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

fun Route.adminRoutes() {

    authenticate("auth-jwt") {

        // 1. Perfil de Admin
        get("/admin/profile") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""

            try {
                val adminData = transaction {
                    (UsuariosTable leftJoin PerfilesAdminsTable)
                        .selectAll()
                        .where { UsuariosTable.email eq email }
                        .map { row ->
                            val nombreCompleto = row[UsuariosTable.nombre]
                            val partes = nombreCompleto.split(" ")
                            
                            AdminProfileResponse(
                                nombres = row.getOrNull(PerfilesAdminsTable.nombres) ?: partes.firstOrNull() ?: "",
                                apellidos = row.getOrNull(PerfilesAdminsTable.apellidos) ?: if (partes.size > 1) partes.drop(1).joinToString(" ") else "",
                                email = row[UsuariosTable.email],
                                telefono = row.getOrNull(PerfilesAdminsTable.telefono) ?: "",
                                rol = row[UsuariosTable.rol]
                            )
                        }.singleOrNull()
                }

                if (adminData != null) call.respond(adminData)
                else call.respond(HttpStatusCode.NotFound, AdminActionResponse(false, "No encontrado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error"))
            }
        }

        // 2. Actualizar Perfil Admin
        put("/admin/profile/update") {
            val principal = call.principal<JWTPrincipal>()
            val emailFromToken = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val req = call.receive<UpdateAdminProfileRequest>()
                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq emailFromToken }.single()
                    val userId = user[UsuariosTable.id]
                    
                    UsuariosTable.update({ UsuariosTable.id eq userId }) {
                        it[nombre] = "${req.nombres} ${req.apellidos}"
                        it[email] = req.email
                        if (!req.password.isNullOrBlank()) it[password] = PasswordHasher.hash(req.password)
                    }
                    
                    val exists = PerfilesAdminsTable.selectAll().where { PerfilesAdminsTable.usuarioId eq userId }.count() > 0
                    if (exists) {
                        PerfilesAdminsTable.update({ PerfilesAdminsTable.usuarioId eq userId }) {
                            it[nombres] = req.nombres
                            it[apellidos] = req.apellidos
                            it[telefono] = req.telefono
                        }
                    } else {
                        PerfilesAdminsTable.insert {
                            it[usuarioId] = userId
                            it[nombres] = req.nombres
                            it[apellidos] = req.apellidos
                            it[telefono] = req.telefono
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Perfil actualizado con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error"))
            }
        }

        // 3. Obtener Citas con filtrado por rol (Admin o Barbero) - ALINEADO CON PROMPT MAESTRO
        get("/admin/appointments") {
            val dateParam = call.request.queryParameters["date"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta date")
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val appointments = transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userRole = user[UsuariosTable.rol]
                    val userId = user[UsuariosTable.id]

                    val clienteAlias = UsuariosTable.alias("cliente")
                    val barberoAlias = UsuariosTable.alias("barbero")

                    var query = CitasTable
                        .join(clienteAlias, JoinType.INNER, additionalConstraint = { CitasTable.usuarioId eq clienteAlias[UsuariosTable.id] })
                        .join(PerfilesClientesTable, JoinType.LEFT, additionalConstraint = { clienteAlias[UsuariosTable.id] eq PerfilesClientesTable.usuarioId })
                        .join(barberoAlias, JoinType.INNER, additionalConstraint = { CitasTable.barberoId eq barberoAlias[UsuariosTable.id] })
                        .selectAll()
                        .where { CitasTable.date eq dateParam }

                    if (userRole == "BARBERO") {
                        query = query.andWhere { CitasTable.barberoId eq userId }
                    }

                    query.map { row ->
                        AdminAppointmentResponse(
                            id = row[CitasTable.id].value,
                            customer = AdminCustomerInfo(
                                nombre = row.getOrNull(PerfilesClientesTable.nombres) ?: row[clienteAlias[UsuariosTable.nombre]],
                                apellido = row.getOrNull(PerfilesClientesTable.apellidos) ?: "",
                                telefono = row.getOrNull(PerfilesClientesTable.telefono) ?: ""
                            ),
                            fecha = row[CitasTable.date],
                            horaInicio = row[CitasTable.startTime],
                            status = row[CitasTable.status],
                            totalPrecio = row[CitasTable.totalPrice],
                            service = AdminServiceInfo(
                                nombre = row[CitasTable.serviceName],
                                precio = row[CitasTable.totalPrice]
                            ),
                            barber = AdminBarberInfo(
                                nombreCompleto = row[barberoAlias[UsuariosTable.nombre]]
                            )
                        )
                    }
                }
                call.respond(appointments)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        // 4. Actualizar Estado de Cita (Validación de Propiedad para Barbero)
        post("/admin/appointments/{id}/status") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "ID inválido")
            val req = call.receive<Map<String, String>>()
            val newStatus = req["status"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta status")
            
            val allowedStatuses = listOf("confirmed", "completed", "cancelled", "pending")
            if (!allowedStatuses.contains(newStatus)) {
                return@post call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Estado no permitido"))
            }

            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""

            try {
                val result = transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userRole = user[UsuariosTable.rol]
                    val userId = user[UsuariosTable.id].value

                    val appointment = CitasTable.selectAll().where { CitasTable.id eq id }.singleOrNull()
                    if (appointment == null) return@transaction "NOT_FOUND"

                    // Validar si es Barbero y la cita es suya
                    if (userRole == "BARBERO" && appointment[CitasTable.barberoId].value != userId) {
                        return@transaction "FORBIDDEN"
                    }

                    CitasTable.update({ CitasTable.id eq id }) {
                        it[status] = newStatus
                    }
                    "OK"
                }

                when (result) {
                    "OK" -> call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Estado de cita actualizado"))
                    "FORBIDDEN" -> call.respond(HttpStatusCode.Forbidden, AdminActionResponse(false, "No tienes permiso para modificar esta cita"))
                    "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, AdminActionResponse(false, "Cita no encontrada"))
                    else -> call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error desconocido"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 5. Vincular Huella
        post("/admin/biometric/register") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            try {
                val req = call.receive<BiometricRegisterRequest>()
                transaction {
                    UsuariosTable.update({ UsuariosTable.email eq email }) {
                        it[biometricToken] = req.token
                    }
                }
                call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Huella vinculada"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error"))
            }
        }

        // 6. Listar Clientes (Permitir a BARBERO y ADMIN)
        get("/admin/customers") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val customers = transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq email }.single()
                    val userRole = user[UsuariosTable.rol]

                    if (userRole != "ADMIN" && userRole != "BARBERO") {
                        throw Exception("No autorizado")
                    }

                    (UsuariosTable innerJoin PerfilesClientesTable)
                        .selectAll()
                        .where { UsuariosTable.rol eq "CLIENTE" }
                        .map { row ->
                            CustomerMgmtDetail(
                                id = row[UsuariosTable.id].value,
                                nombre = row[PerfilesClientesTable.nombres],
                                apellido = row[PerfilesClientesTable.apellidos],
                                telefono = row[PerfilesClientesTable.telefono],
                                correo = row[UsuariosTable.email],
                                fechaRegistro = row[UsuariosTable.fechaRegistro],
                                estado = row[PerfilesClientesTable.estado],
                                fecha_cumpleanos = row[PerfilesClientesTable.fechaNacimiento],
                                direccion = row[PerfilesClientesTable.direccion],
                                notas = row[PerfilesClientesTable.notas]
                            )
                        }
                }
                call.respond(customers)
            } catch (e: Exception) {
                if (e.message == "No autorizado") call.respond(HttpStatusCode.Forbidden, "No tienes permiso")
                else call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }
    }
}
