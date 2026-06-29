package com.example.routes

import com.example.data.PerfilesClientesTable
import com.example.data.UsuariosTable
import com.example.models.LoginRequest
import com.example.models.LoginResponse
import com.example.models.RegisterRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.authRoutes() {

    post("/login") {
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
                mapOf("success" to true, "message" to "Cuenta creada con éxito")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("success" to false, "message" to "Error al registrar: ${e.message}")
            )
        }
    }
}
