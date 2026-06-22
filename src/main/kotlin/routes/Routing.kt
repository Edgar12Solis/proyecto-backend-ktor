package com.example.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.database.ClientesTable
import com.example.database.UsuariosTable
import com.example.solicitudes.LoginRequest
import com.example.solicitudes.LoginResponse

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

        // PRUEBA DEL SERVIDOR
        get("/") {
            call.respondText("¡Servidor Ktor activo 🚀!")
        }

        post("/login") {
            try {
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
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    LoginResponse(
                        success = false,
                        message = "Error en la solicitud: ${e.message}"
                    )
                )
            }
        }

        get("/test-db") {
            try {
                transaction {
                    // Solo prueba conexión a Railway
                }
                call.respondText("Conexión a PostgreSQL Railway OK")
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}")
            }
        }


        get("/usuarios") {
            val listaUsuarios = listOf(
                UsuarioPrueba(1, "Juan", "Administrador"),
                UsuarioPrueba(2, "Maria", "Desarrolladora")
            )
            call.respond(listaUsuarios)
        }


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