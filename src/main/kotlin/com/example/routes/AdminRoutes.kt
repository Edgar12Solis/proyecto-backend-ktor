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

fun Route.adminRoutes() {

    // Rutas protegidas para administradores
    authenticate("auth-jwt") {

        // 1. Obtener Mi Perfil de Admin (Con JOIN para el teléfono)
        get("/admin/profile") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""

            try {
                val adminData = transaction {
                    // Usamos leftJoin para que, si no hay registro en PerfilesAdminsTable,
                    // aún así devuelva los datos básicos de UsuariosTable.
                    (UsuariosTable leftJoin PerfilesAdminsTable)
                        .selectAll()
                        .where { UsuariosTable.email eq email }
                        .map { row ->
                            // Si el JOIN es nulo, usamos valores por defecto o el nombre completo del usuario
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

                if (adminData != null) {
                    call.respond(adminData)
                } else {
                    call.respond(HttpStatusCode.NotFound, AdminActionResponse(false, "Administrador no encontrado"))
                }
            } catch (e: Exception) {
                println("❌ Error profile admin: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al obtener perfil"))
            }
        }

        // 2. Actualizar Perfil de Admin
        put("/admin/profile/update") {
            val principal = call.principal<JWTPrincipal>()
            val emailFromToken = principal?.payload?.getClaim("email")?.asString() ?: ""
            
            try {
                val req = call.receive<UpdateAdminProfileRequest>()
                
                transaction {
                    val user = UsuariosTable.selectAll().where { UsuariosTable.email eq emailFromToken }.single()
                    val userId = user[UsuariosTable.id]
                    
                    // Actualizar Tabla Usuarios (nombre, email y password si viene)
                    UsuariosTable.update({ UsuariosTable.id eq userId }) {
                        it[nombre] = "${req.nombres} ${req.apellidos}"
                        it[email] = req.email
                        if (!req.password.isNullOrBlank()) {
                            it[password] = PasswordHasher.hash(req.password)
                        }
                    }
                    
                    // 2. Actualizar o Insertar Tabla PerfilesAdmins
                    val profileExists = PerfilesAdminsTable.selectAll().where { PerfilesAdminsTable.usuarioId eq userId }.count() > 0
                    if (profileExists) {
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
                println("Error update admin profile: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al actualizar el perfil"))
            }
        }

        // 3. Listar Todos los Administradores
        get("/admin/list") {
            try {
                val admins = transaction {
                    UsuariosTable.selectAll().where { UsuariosTable.rol eq "ADMIN" }.map { row ->
                        val nombreCompleto = row[UsuariosTable.nombre]
                        val partes = nombreCompleto.split(" ")
                        val nombres = partes.firstOrNull() ?: ""
                        val apellidos = if (partes.size > 1) partes.drop(1).joinToString(" ") else ""

                        AdminListItem(
                            id = row[UsuariosTable.id].value,
                            nombres = nombres,
                            apellidos = apellidos,
                            email = row[UsuariosTable.email]
                        )
                    }
                }
                call.respond(admins)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al listar administradores"))
            }
        }

        // 4. Crear Nuevo Administrador
        post("/admin/add") {
            try {
                val req = call.receive<CreateAdminRequest>()
                
                transaction {
                    val userId = UsuariosTable.insertAndGetId {
                        it[nombre] = "${req.nombres} ${req.apellidos}"
                        it[email] = req.email
                        it[password] = PasswordHasher.hash(req.password)
                        it[rol] = "ADMIN"
                    }
                    
                    PerfilesAdminsTable.insert {
                        it[usuarioId] = userId
                        it[nombres] = req.nombres
                        it[apellidos] = req.apellidos
                        it[telefono] = req.telefono
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Administrador creado correctamente"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error al crear administrador: ${e.message}"))
            }
        }

        // 5. Registro de Barberos
        post("/admin/barberos") {
            try {
                val req = call.receive<BarberoCreateRequest>()

                transaction {
                    val userId = UsuariosTable.insertAndGetId {
                        it[nombre] = req.nombreCompleto
                        it[email] = req.email
                        it[password] = PasswordHasher.hash(req.password)
                        it[rol] = "BARBERO"
                    }

                    PerfilesBarberosTable.insert {
                        it[usuarioId] = userId
                        it[especialidad] = req.especialidad
                        it[biografia] = req.biografia
                    }
                }

                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Barbero registrado con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error al registrar barbero: ${e.message}"))
            }
        }

        // 8. Registro de Huella (Vincular dispositivo actual)
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
                call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Huella vinculada con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al vincular huella"))
            }
        }

        // 6. Obtener Citas por Fecha
        get("/admin/appointments") {
            val dateParam = call.request.queryParameters["date"]
            if (dateParam == null) {
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Falta el parámetro 'date'"))
                return@get
            }

            try {
                val appointments = transaction {
                    // Joins complejos: Citas -> Usuarios (Cliente) -> PerfilesClientes y Citas -> Usuarios (Barbero)
                    val clienteAlias = UsuariosTable.alias("cliente")
                    val barberoAlias = UsuariosTable.alias("barbero")

                    CitasTable
                        .join(clienteAlias, JoinType.INNER, additionalConstraint = { CitasTable.usuarioId eq clienteAlias[UsuariosTable.id] })
                        .join(PerfilesClientesTable, JoinType.LEFT, additionalConstraint = { clienteAlias[UsuariosTable.id] eq PerfilesClientesTable.usuarioId })
                        .join(barberoAlias, JoinType.INNER, additionalConstraint = { CitasTable.barberoId eq barberoAlias[UsuariosTable.id] })
                        .selectAll()
                        .where { CitasTable.date eq dateParam }
                        .map { row ->
                            AdminAppointmentResponse(
                                id = row[CitasTable.id].value,
                                customer = AdminCustomerInfo(
                                    nombre = row[clienteAlias[UsuariosTable.nombre]],
                                    telefono = row.getOrNull(PerfilesClientesTable.telefono) ?: ""
                                ),
                                date = row[CitasTable.date],
                                startTime = row[CitasTable.startTime],
                                status = row[CitasTable.status],
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
                println("❌ Error fetching appointments: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al obtener citas"))
            }
        }

        // 7. Cancelar Cita
        post("/admin/appointments/{id}/cancel") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "ID de cita inválido"))
                return@post
            }

            try {
                val updated = transaction {
                    CitasTable.update({ CitasTable.id eq id }) {
                        it[status] = "Cancelada"
                    }
                }

                if (updated > 0) {
                    call.respond(HttpStatusCode.OK, AdminActionResponse(true, "Cita cancelada con éxito"))
                } else {
                    call.respond(HttpStatusCode.NotFound, AdminActionResponse(false, "Cita no encontrada"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al cancelar cita"))
            }
        }
    }
}
