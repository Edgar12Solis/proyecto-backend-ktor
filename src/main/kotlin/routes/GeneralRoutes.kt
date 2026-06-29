package com.example.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.generalRoutes() {
    get("/") {
        call.respondText("¡Servidor Ktor activo 🚀!")
    }

    get("/test-db") {
        try {
            transaction {
                // Solo prueba conexión
            }
            call.respondText("Conexión a PostgreSQL Railway OK")
        } catch (e: Exception) {
            call.respondText("Error: \${e.message}")
        }
    }
}
