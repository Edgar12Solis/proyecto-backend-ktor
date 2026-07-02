package com.example.routes

import com.example.data.UsuariosTable
import com.example.models.UsuarioRequest
import com.example.models.UsuarioResponse
import com.example.plugins.PasswordHasher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.usuarioRoutes() {

    post("/usuarios") {
        try {
            val usuario = call.receive<UsuarioRequest>()
            transaction {
                UsuariosTable.insert {
                    it[UsuariosTable.nombre] = usuario.nombre
                    it[UsuariosTable.email] = usuario.email
                    it[UsuariosTable.password] = PasswordHasher.hash(usuario.password)
                    it[UsuariosTable.rol] = usuario.rol
                }
            }
            call.respond(HttpStatusCode.Created, mapOf("mensaje" to "Usuario insertado con éxito"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("mensaje" to "Error al insertar usuario: \${e.message}"))
        }
    }

    get("/usuarios") {
        try {
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
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("mensaje" to "Error al obtener usuarios"))
        }
    }

    delete("/usuarios/{id}") {
        val idStr = call.parameters["id"]
        val id = idStr?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, "ID inválido")
            return@delete
        }
        try {
            transaction {
                UsuariosTable.deleteWhere { UsuariosTable.id eq id }
            }
            call.respond(HttpStatusCode.OK, "Usuario eliminado")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Error al eliminar usuario")
        }
    }
}
