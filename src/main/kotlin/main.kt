package com.example

import com.example.database.DatabaseFactory
import com.example.routes.configureRouting
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import com.example.routes.usuarioRoutes
// Como todos están en 'com.example', no necesitas más imports de paquetes

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port) {
        DatabaseFactory.init()

        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
        }

        // Llamamos a las funciones directamente
        configureSerialization()
        configureRouting()
        usuarioRoutes()
    }.start(wait = true)
}
