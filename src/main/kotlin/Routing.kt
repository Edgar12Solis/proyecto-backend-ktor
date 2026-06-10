package com.example

import io.ktor.http.* // <-- Agregado para HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.* // <-- Agregado para call.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioPrueba(val id: Int, val nombre: String, val rol: String)

// --- AQUÍ EMPIEZA LO MIO ---
@Serializable
data class Cliente(
    val id: Int? = null,
    val nombre: String,
    val apellido: String,
    val fecha_cumpleanos: String,
    val telefono: String,
    val correo: String
)
// --- AQUÍ TERMINA LO MIO ---

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

        // --- AQUÍ EMPIEZA MI RUTA ---
        post("/clientes") {
            try {
                // Recibimos los datos del celular y los convertimos al modelo Cliente
                val nuevoCliente = call.receive<Cliente>()

                // Lo imprimimos en la consola del servidor
                println("¡Éxito! El servidor recibió a: ${nuevoCliente.nombre} ${nuevoCliente.apellido}")

                // Confirmamos al celular que todo salió bien (Status 201 Created)
                call.respond(HttpStatusCode.Created, "Cliente registrado correctamente")

            } catch (e: Exception) {
                // Si falta algún dato o hay un error, avisamos al celular (Status 400 Bad Request)
                call.respond(HttpStatusCode.BadRequest, "Error al registrar: ${e.message}")
            }
        }
        // --- AQUÍ TERMINA MI RUTA ---
    }
}