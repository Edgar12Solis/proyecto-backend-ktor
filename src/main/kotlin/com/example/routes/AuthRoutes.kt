package com.example.routes

import com.example.data.PerfilesClientesTable
import com.example.data.UsuariosTable
import com.example.models.*
import com.example.plugins.JwtConfig
import com.example.plugins.PasswordHasher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.authRoutes() {

    post("/login") {
        try {
            val loginReq = call.receive<LoginRequest>()
            println("🔍 Intento de login para: ${loginReq.email}")
            
            val user = transaction {
                UsuariosTable.selectAll().where {
                    UsuariosTable.email eq loginReq.email
                }.singleOrNull()
            }

            if (user != null) {
                val dbPassword = user[UsuariosTable.password]
                if (PasswordHasher.check(loginReq.password, dbPassword)) {
                    val token = JwtConfig.generateToken(loginReq.email)
                    println("✅ Login exitoso: ${loginReq.email}")
                    call.respond(
                        HttpStatusCode.OK,
                        LoginResponse(
                            success = true,
                            message = "¡Bienvenido de nuevo!",
                            rol = user[UsuariosTable.rol],
                            token = token
                        )
                    )
                } else {
                    println("❌ Contraseña incorrecta para: ${loginReq.email}")
                    call.respond(
                        HttpStatusCode.OK,
                        LoginResponse(
                            success = false,
                            message = "Correo o contraseña incorrectos. Por favor, intenta de nuevo."
                        )
                    )
                }
            } else {
                println("❌ Usuario no encontrado: ${loginReq.email}")
                call.respond(
                    HttpStatusCode.OK,
                    LoginResponse(
                        success = false,
                        message = "Correo o contraseña incorrectos. Por favor, intenta de nuevo."
                    )
                )
            }
        } catch (e: Exception) {
            println("❌ ERROR CRÍTICO en login: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    success = false, 
                    message = "Error de conexión con el servidor. Inténtalo más tarde."
                )
            )
        }
    }

    post("/register") {
        try {
            val regReq = call.receive<RegisterRequest>()
            println("📝 Intentando registrar usuario: ${regReq.email}")

            transaction {
                // 1. Crear el usuario con rol CLIENTE y password hasheada
                val userId = UsuariosTable.insertAndGetId {
                    it[UsuariosTable.nombre] = "${regReq.nombres} ${regReq.apellidos}"
                    it[UsuariosTable.email] = regReq.email
                    it[UsuariosTable.password] = PasswordHasher.hash(regReq.password)
                    it[UsuariosTable.rol] = "CLIENTE"
                }

                // 2. Crear el perfil del cliente vinculado al usuario
                PerfilesClientesTable.insert {
                    it[PerfilesClientesTable.usuarioId] = userId
                    it[PerfilesClientesTable.nombres] = regReq.nombres
                    it[PerfilesClientesTable.apellidos] = regReq.apellidos
                    it[PerfilesClientesTable.telefono] = regReq.telefono
                    // Los campos nuevos (fecha_nacimiento y direccion) se inicializan como null si fallara la migracion automatica
                    // Pero los incluimos explícitamente para que se creen si addMissingColumns funcionó
                    it[PerfilesClientesTable.fechaNacimiento] = null
                    it[PerfilesClientesTable.direccion] = null
                }
            }

            println("✅ Registro exitoso para: ${regReq.email}")
            call.respond(
                HttpStatusCode.OK,
                RegisterResponse(success = true, message = "¡Cuenta creada con éxito! Ya puedes iniciar sesión.")
            )
        } catch (e: ExposedSQLException) {
            println("❌ Error de BD en registro: ${e.message}")
            val isDuplicate = e.message?.contains("duplicate", ignoreCase = true) == true || e.sqlState == "23505"
            val message = if (isDuplicate) "Este correo electrónico ya está registrado." 
                          else "Error de base de datos interno: ${e.message}"
            
            call.respond(
                HttpStatusCode.OK,
                RegisterResponse(success = false, message = message)
            )
        } catch (e: Exception) {
            println("❌ Error inesperado en registro: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.OK,
                RegisterResponse(success = false, message = "Ocurrió un error inesperado al crear la cuenta.")
            )
        }
    }

    // 3. Login Biométrico
    post("/login/biometric") {
        try {
            val req = call.receive<BiometricLoginRequest>()
            println("🔑 Intento de login biométrico")

            val user = transaction {
                UsuariosTable.selectAll().where {
                    UsuariosTable.biometricToken eq req.token
                }.singleOrNull()
            }

            if (user != null) {
                val email = user[UsuariosTable.email]
                val token = JwtConfig.generateToken(email)
                println("✅ Login biométrico exitoso para: $email")
                call.respond(
                    HttpStatusCode.OK,
                    LoginResponse(
                        success = true,
                        message = "¡Autenticación biométrica exitosa!",
                        rol = user[UsuariosTable.rol],
                        token = token
                    )
                )
            } else {
                println("❌ Token biométrico no reconocido")
                call.respond(
                    HttpStatusCode.OK,
                    LoginResponse(success = false, message = "Huella no reconocida o dispositivo no vinculado.")
                )
            }
        } catch (e: Exception) {
            println("❌ Error en login biométrico: ${e.message}")
            call.respond(HttpStatusCode.OK, LoginResponse(false, "Error en autenticación biométrica."))
        }
    }
}
