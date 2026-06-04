package com.example

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioPrueba(val id: Int, val nombre: String, val rol: String)

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("¡Servidor Ktor activo!")
        }

        get("/usuarios") {
            val listaUsuarios = listOf(
                UsuarioPrueba(1, "Juan", "Administrador"),
                UsuarioPrueba(2, "Maria", "Desarrolladora")
            )
            call.respond(listaUsuarios)
        }
    }
}