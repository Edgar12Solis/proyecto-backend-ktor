package com.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
// Como todos están en 'com.example', no necesitas más imports de paquetes

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port) {
        DatabaseFactory.init()
        // Llamamos a las funciones directamente
        configureSerialization()
        configureRouting()
    }.start(wait = true)
}
