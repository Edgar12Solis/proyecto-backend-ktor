package com.example.routes

import com.example.data.UsuariosTable
import com.example.models.UsuarioRequest
import com.example.models.UsuarioResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.usuarioRoutes() {

    post("/usuarios") {
        val usuario = call.receive<UsuarioRequest>()
        transaction {
            UsuariosTable.insert {
                it[UsuariosTable.nombre] = usuario.nombre
                it[UsuariosTable.email] = usuario.email
                it[UsuariosTable.password] = usuario.password
                it[UsuariosTable.rol] = usuario.rol
            }
        }
        call.respond(mapOf("mensaje" to "Usuario insertado"))
    }

    get("/usuarios") {
        val lista = transaction {
            UsuariosTable.selectAll().map {
                UsuarioResponse(
                    id = it[UsuariosTable.id].value,
                    nombre = it[UsuariosTable.nombre],
                    email = it[UsuariosTable.email],
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
