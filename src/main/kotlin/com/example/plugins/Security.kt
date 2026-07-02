package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import at.favre.lib.crypto.bcrypt.BCrypt

fun Application.configureSecurity() {
    
    val jwtSecret = System.getenv("JWT_SECRET") ?: "secret-key-123"
    val jwtDomain = "https://jwt-provider-domain/"
    val jwtAudience = "jwt-audience"
    val jwtRealm = "ktor sample app"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtDomain)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Token no válido o expirado")
            }
        }
    }
}

object JwtConfig {
    private const val secret = "secret-key-123" // Debería coincidir con el de arriba
    private const val issuer = "https://jwt-provider-domain/"
    private const val audience = "jwt-audience"
    
    fun generateToken(email: String): String = JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("email", email)
        .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET") ?: secret))
}

object PasswordHasher {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(12, password.toCharArray())
    
    fun check(password: String, hashed: String): Boolean = 
        BCrypt.verifyer().verify(password.toCharArray(), hashed.toCharArray()).verified
}
