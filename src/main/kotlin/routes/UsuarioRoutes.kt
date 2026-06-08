package com.example.routes

import com.example.database.UsuariosTable
import com.example.solicitudes.UsuarioRequest
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.solicitudes.UsuarioResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
fun Application.usuarioRoutes() {

    routing {

        // 🔥 INSERTAR USUARIO
        post("/usuarios") {

            val usuario = call.receive<UsuarioRequest>()

            transaction {
                UsuariosTable.insert {
                    it[nombre] = usuario.nombre
                    it[rol] = usuario.rol
                }
            }

            call.respond(mapOf("mensaje" to "Usuario insertado"))
        }

        get("/usuarios") {

            val lista = transaction {
                UsuariosTable.selectAll().map {
                    UsuarioResponse(
                        id = it[UsuariosTable.id],
                        nombre = it[UsuariosTable.nombre],
                        rol = it[UsuariosTable.rol]
                    )
                }
            }

            call.respond(lista)
        }
        delete("/usuarios/{id}") {

            val id = call.parameters["id"]?.toIntOrNull()

            if (id == null) {
                call.respondText("ID inválido")
                return@delete
            }

            transaction {
                UsuariosTable.deleteWhere {
                    UsuariosTable.id eq id
                }
            }

            call.respondText("Usuario eliminado")
        }
    }
}