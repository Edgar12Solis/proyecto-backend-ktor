package com.example

import com.example.data.DatabaseFactory
import com.example.routes.*
import com.example.plugins.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import io.ktor.server.routing.*

fun main() {
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

        configureSerialization()
        configureSecurity()
        
        routing {
            generalRoutes()
            authRoutes()
            adminRoutes()
            usuarioRoutes()
            customerRoutes()
            terminalRoutes()
            inventoryRoutes()
            barberMgmtRoutes()
            serviceMgmtRoutes()
            reportRoutes()
            customerMgmtRoutes()
        }
    }.start(wait = true)
}
