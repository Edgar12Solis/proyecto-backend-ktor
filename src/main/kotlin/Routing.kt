package com.example// Revisa que este sea tu paquete correcto

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import io.ktor.http.HttpStatusCode

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.database.ClientesTable

@Serializable
data class UsuarioPrueba(val id: Int, val nombre: String, val rol: String)

@Serializable
data class Cliente(
    val id: Int? = null,
    val nombre: String,
    val apellido: String,
    val fecha_cumpleanos: String,
    val telefono: String,
    val correo: String
)

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

        // --- RUTA PARA GUARDAR CLIENTES ---
        post("/clientes") {
            try {
                // 1. Recibimos los datos del celular
                val nuevoCliente = call.receive<Cliente>()

                // 2. Abrimos conexión a Postgres y hacemos el INSERT
                transaction {
                    ClientesTable.insert {
                        it[ClientesTable.nombre] = nuevoCliente.nombre
                        it[ClientesTable.apellido] = nuevoCliente.apellido
                        it[ClientesTable.fechaCumpleanos] = nuevoCliente.fecha_cumpleanos
                        it[ClientesTable.telefono] = nuevoCliente.telefono
                        it[ClientesTable.correo] = nuevoCliente.correo
                    }
                }

                // 3. Confirmamos en consola y al celular
                println("¡Éxito! Cliente guardado en la BD: ${nuevoCliente.nombre}")
                call.respond(HttpStatusCode.Created, "Cliente registrado exitosamente")

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error al registrar: ${e.message}")
            }
        }
    }
}