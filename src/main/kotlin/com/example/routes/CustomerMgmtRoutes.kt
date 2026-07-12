package com.example.routes

import com.example.data.*
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.customerMgmtRoutes() {
    authenticate("auth-jwt") {

        get("/admin/customers") {
            try {
                val customers = transaction {
                    (UsuariosTable innerJoin PerfilesClientesTable)
                        .selectAll()
                        .where { UsuariosTable.rol eq "CLIENTE" }
                        .map { row ->
                            CustomerMgmtDetail(
                                id = row[UsuariosTable.id].value,
                                nombre = row[PerfilesClientesTable.nombres],
                                apellido = row[PerfilesClientesTable.apellidos],
                                telefono = row[PerfilesClientesTable.telefono],
                                correo = row[UsuariosTable.email],
                                fechaRegistro = row[UsuariosTable.fechaRegistro],
                                estado = row[PerfilesClientesTable.estado],
                                fecha_cumpleanos = row[PerfilesClientesTable.fechaNacimiento],
                                direccion = row[PerfilesClientesTable.direccion],
                                notas = row[PerfilesClientesTable.notas]
                            )
                        }
                }
                call.respond(customers)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        get("/admin/customers/stats") {
            try {
                val stats = transaction {
                    val total = UsuariosTable.selectAll().where { UsuariosTable.rol eq "CLIENTE" }.count().toInt()
                    val active = PerfilesClientesTable.selectAll().where { PerfilesClientesTable.estado eq "active" }.count().toInt()
                    val inactive = total - active
                    CustomerMgmtStats(total, active, inactive)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        post("/admin/customers/{id}/toggle") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id != null) {
                try {
                    transaction {
                        val current = PerfilesClientesTable.selectAll().where { PerfilesClientesTable.usuarioId eq id }
                            .single()[PerfilesClientesTable.estado]
                        val next = if (current == "active") "inactive" else "active"
                        
                        PerfilesClientesTable.update({ PerfilesClientesTable.usuarioId eq id }) {
                            it[estado] = next
                        }
                    }
                    call.respond(AdminActionResponse(true, "Estado de cliente actualizado"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error")
                }
            }
        }

        delete("/admin/customers/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id != null) {
                transaction {
                    UsuariosTable.deleteWhere { UsuariosTable.id eq id }
                }
                call.respond(AdminActionResponse(true, "Cliente eliminado permanentemente"))
            }
        }
    }
}
