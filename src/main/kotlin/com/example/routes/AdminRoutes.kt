package com.example.routes

import com.example.data.PerfilesBarberosTable
import com.example.data.UsuariosTable
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

        // 1. Obtener Mi Perfil de Admin
        get("/admin/profile") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString() ?: ""

            try {
                val adminData = transaction {
                    UsuariosTable.selectAll().where { UsuariosTable.email eq email }.map { row ->
                        val nombreCompleto = row[UsuariosTable.nombre]
                        val partes = nombreCompleto.split(" ")
                        val nombres = partes.firstOrNull() ?: ""
                        val apellidos = if (partes.size > 1) partes.drop(1).joinToString(" ") else ""
                        
                        AdminProfileResponse(
                            nombres = nombres,
                            apellidos = apellidos,
                            email = row[UsuariosTable.email],
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
                call.respond(HttpStatusCode.InternalServerError, AdminActionResponse(false, "Error al obtener perfil"))
            }
        }

        // 2. Listar Todos los Administradores
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

        // 3. Crear Nuevo Administrador
        post("/admin/add") {
            try {
                val req = call.receive<CreateAdminRequest>()
                
                transaction {
                    UsuariosTable.insert {
                        it[nombre] = "${req.nombres} ${req.apellidos}"
                        it[email] = req.email
                        it[password] = PasswordHasher.hash(req.password)
                        it[rol] = "ADMIN"
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Administrador creado correctamente"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error al crear administrador: ${e.message}"))
            }
        }

        // Registro de Barberos (Mantenemos la funcionalidad anterior bajo protección)
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
