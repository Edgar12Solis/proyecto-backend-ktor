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
                    (UsuariosTable innerJoin PerfilesAdminsTable)
                        .selectAll()
                        .where { UsuariosTable.email eq email }
                        .map { row ->
                            AdminProfileResponse(
                                nombres = row[PerfilesAdminsTable.nombres],
                                apellidos = row[PerfilesAdminsTable.apellidos],
                                email = row[UsuariosTable.email],
                                telefono = row[PerfilesAdminsTable.telefono],
                                rol = row[UsuariosTable.rol]
                            )
                        }.singleOrNull()
                }

                if (adminData != null) {
                    call.respond(adminData)
                } else {
                    call.respond(HttpStatusCode.NotFound, AdminActionResponse(false, "Perfil de administrador no encontrado"))
                }
            } catch (e: Exception) {
                println("Error profile admin: ${e.message}")
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
                    
                    // Actualizar Tabla PerfilesAdmins (teléfono, nombres, apellidos)
                    PerfilesAdminsTable.update({ PerfilesAdminsTable.usuarioId eq userId }) {
                        it[nombres] = req.nombres
                        it[apellidos] = req.apellidos
                        it[telefono] = req.telefono
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
    }
}
