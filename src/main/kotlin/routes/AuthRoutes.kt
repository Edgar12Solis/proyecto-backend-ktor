package com.example.routes

import com.example.data.PerfilesClientesTable
import com.example.data.UsuariosTable
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.authRoutes() {

    post("/login") {
        try {
            val loginReq = call.receive<LoginRequest>()
            val user = transaction {
                UsuariosTable.selectAll().where {
                    (UsuariosTable.email eq loginReq.email) and (UsuariosTable.password eq loginReq.password)
                }.singleOrNull()
            }

            if (user != null) {
                call.respond(
                    LoginResponse(
                        success = true,
                        message = "Login exitoso",
                        rol = user[UsuariosTable.rol]
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    LoginResponse(
                        success = false,
                        message = "Email o contraseña incorrectos"
                    )
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                LoginResponse(success = false, message = "Error en login: ${e.message}")
            )
        }
    }

    post("/register") {
        try {
            val regReq = call.receive<RegisterRequest>()

            transaction {
                // 1. Crear el usuario con rol CLIENTE
                val userId = UsuariosTable.insertAndGetId {
                    it[UsuariosTable.nombre] = "${regReq.nombres} ${regReq.apellidos}"
                    it[UsuariosTable.email] = regReq.email
                    it[UsuariosTable.password] = regReq.password
                    it[UsuariosTable.rol] = "CLIENTE"
                }

                // 2. Crear el perfil del cliente vinculado al usuario
                PerfilesClientesTable.insert {
                    it[PerfilesClientesTable.usuarioId] = userId
                    it[PerfilesClientesTable.nombres] = regReq.nombres
                    it[PerfilesClientesTable.apellidos] = regReq.apellidos
                    it[PerfilesClientesTable.telefono] = regReq.telefono
                }
            }

            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(success = true, message = "Cuenta creada con éxito")
            )
        } catch (e: ExposedSQLException) {
            // Manejar errores de base de datos como emails duplicados
            val message = if (e.message?.contains("duplicate key") == true) {
                "El correo electrónico ya está registrado"
            } else {
                "Error de base de datos: ${e.message}"
            }
            call.respond(
                HttpStatusCode.Conflict,
                RegisterResponse(success = false, message = message)
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                RegisterResponse(success = false, message = "Error al registrar: ${e.message}")
            )
        }
    }
}
