package com.example.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
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

        // 🟢 PRUEBA DEL SERVIDOR
        get("/") {
            call.respondText("¡Servidor Ktor activo 🚀!")
        }

        // 🧪 PRUEBA DE CONEXIÓN A BD
        get("/test-db") {
            try {
                transaction {
                    // Solo prueba conexión a Railway
                }
                call.respondText("✅ Conexión a PostgreSQL Railway OK")
            } catch (e: Exception) {
                call.respondText("❌ Error: ${e.message}")
            }
        }

        // 👥 RUTA DE USUARIOS
        get("/usuarios") {
            val listaUsuarios = listOf(
                UsuarioPrueba(1, "Juan", "Administrador"),
                UsuarioPrueba(2, "Maria", "Desarrolladora")
            )
            call.respond(listaUsuarios)
        }

        // 📥 TU RUTA REAL PARA GUARDAR CLIENTES
        post("/clientes") {
            try {
                val nuevoCliente = call.receive<Cliente>()

                transaction {
                    ClientesTable.insert {
                        it[ClientesTable.nombre] = nuevoCliente.nombre
                        it[ClientesTable.apellido] = nuevoCliente.apellido
                        it[ClientesTable.fechaCumpleanos] = nuevoCliente.fecha_cumpleanos
                        it[ClientesTable.telefono] = nuevoCliente.telefono
                        it[ClientesTable.correo] = nuevoCliente.correo
                    }
                }

                println("¡Éxito! Cliente guardado en la BD: ${nuevoCliente.nombre}")
                call.respond(HttpStatusCode.Created, "Cliente registrado exitosamente")

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error al registrar: ${e.message}")
            }
        }
    }
}