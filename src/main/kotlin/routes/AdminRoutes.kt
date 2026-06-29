package com.example.routes

import com.example.data.PerfilesBarberosTable
import com.example.data.UsuariosTable
import com.example.models.BarberoCreateRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminRoutes() {

    // Nota: Este endpoint debería estar protegido en una app real
    post("/admin/barberos") {
        try {
            val req = call.receive<BarberoCreateRequest>()

            transaction {
                // 1. Crear el usuario con rol BARBERO
                val userId = UsuariosTable.insertAndGetId {
                    it[UsuariosTable.nombre] = req.nombreCompleto
                    it[UsuariosTable.email] = req.email
                    it[UsuariosTable.password] = req.password
                    it[UsuariosTable.rol] = "BARBERO"
                }

                // 2. Crear el perfil del barbero
                PerfilesBarberosTable.insert {
                    it[PerfilesBarberosTable.usuarioId] = userId
                    it[PerfilesBarberosTable.especialidad] = req.especialidad
                    it[PerfilesBarberosTable.biografia] = req.biografia
                }
            }

            call.respond(
                HttpStatusCode.Created,
                mapOf("success" to true, "message" to "Barbero registrado con éxito")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("success" to false, "message" to "Error al registrar barbero: ${e.message}")
            )
        }
    }
}
