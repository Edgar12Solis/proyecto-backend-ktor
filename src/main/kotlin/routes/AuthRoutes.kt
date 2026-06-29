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
                        message = "¡Bienvenido de nuevo!",
                        rol = user[UsuariosTable.rol]
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    LoginResponse(
                        success = false,
                        message = "Correo o contraseña incorrectos. Por favor, intenta de nuevo."
                    )
                )
            }
        } catch (e: Exception) {
            println("❌ Error en login: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                LoginResponse(
                    success = false, 
                    message = "Hubo un problema de conexión con el servidor. Inténtalo más tarde."
                )
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
                RegisterResponse(success = true, message = "¡Cuenta creada con éxito! Ya puedes iniciar sesión.")
            )
        } catch (e: ExposedSQLException) {
            // Log the error for debugging in Railway
            println("❌ Error de base de datos en registro: ${e.message}")
            println("SQL State: ${e.sqlState}")

            // Manejar errores de base de datos como emails duplicados (SQL State 23505)
            val isDuplicate = e.message?.contains("duplicate", ignoreCase = true) == true || e.sqlState == "23505"
            val message = if (isDuplicate) {
                "Este correo electrónico ya está registrado."
            } else {
                "No pudimos crear tu cuenta en este momento. Inténtalo de nuevo."
            }
            
            call.respond(
                HttpStatusCode.Conflict,
                RegisterResponse(success = false, message = message)
            )
        } catch (e: Exception) {
            println("❌ Error inesperado en registro: ${e.message}")
            e.printStackTrace()

            call.respond(
                HttpStatusCode.InternalServerError,
                RegisterResponse(
                    success = false, 
                    message = "Ocurrió un error inesperado. Por favor, inténtalo más tarde."
                )
            )
        }
    }
}
