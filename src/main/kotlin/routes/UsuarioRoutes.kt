package com.example.routes

import com.example.database.UsuariosTable
import com.example.solicitudes.UsuarioRequest
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

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
        get("/debug") {
            val data = transaction {
                UsuariosTable.selectAll().map {
                    mapOf(
                        "id" to it[UsuariosTable.id],
                        "nombre" to it[UsuariosTable.nombre],
                        "rol" to it[UsuariosTable.rol]
                    )
                }
            }

            call.respond(data)
        }
        // 🔥 LISTAR USUARIOS
        get("/usuarios") {

            val lista = transaction {
                UsuariosTable.selectAll().map {
                    mapOf(
                        "id" to it[UsuariosTable.id],
                        "nombre" to it[UsuariosTable.nombre],
                        "rol" to it[UsuariosTable.rol]
                    )
                }
            }

            call.respond(lista)
        }
    }
}