package com.example.routes

import com.example.data.*
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Route.ventaExpressRutas() {
    authenticate("auth-jwt") {

        // 1. Finalizar Venta Express
        post("/admin/venta-express") {
            try {
                val req = call.receive<VentaExpressRequest>()
                val ahora = LocalDateTime.now()
                val fechaHoy = ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val horaAhora = ahora.format(DateTimeFormatter.ofPattern("HH:mm"))

                transaction {
                    var finalClienteId = req.usuarioId

                    // A) Si es ocasional, crear registro rápido
                    if (req.esOcasional || finalClienteId == null) {
                        finalClienteId = UsuariosTable.insertAndGetId {
                            it[nombre] = req.clienteNombre ?: "Cliente Venta Rápida"
                            it[email] = "venta_rapida_${System.currentTimeMillis()}@wolf.com"
                            it[password] = "ocasional123"
                            it[rol] = "CLIENTE"
                            it[activo] = true
                            it[fechaRegistro] = fechaHoy
                        }.value
                        
                        PerfilesClientesTable.insert {
                            it[usuarioId] = finalClienteId!!
                            it[nombres] = req.clienteNombre?.split(" ")?.firstOrNull() ?: "Cliente"
                            it[apellidos] = req.clienteNombre?.split(" ")?.drop(1)?.joinToString(" ") ?: "Ocasional"
                            it[estado] = "active"
                        }
                    }

                    // B) Guardar como Cita COMPLETADA (Para historial y reportes)
                    val serviciosTexto = req.serviciosNombres.joinToString(", ")
                    CitasTable.insert {
                        it[usuarioId] = finalClienteId!!
                        it[barberoId] = req.barberoId
                        it[serviceName] = if (serviciosTexto.length > 100) serviciosTexto.substring(0, 97) + "..." else serviciosTexto
                        it[date] = fechaHoy
                        it[startTime] = horaAhora
                        it[duracion] = 30 // Valor por defecto para ventas rápidas
                        it[totalPrice] = req.totalPagar
                        it[status] = "Completada"
                        it[metodoPago] = req.metodoPago
                    }

                    // C) Reducir Stock de Productos automáticamente
                    req.productos.forEach { prod ->
                        val currentStock = ProductosTable.selectAll().where { ProductosTable.id eq prod.id }.single()[ProductosTable.stock]
                        val reduction = prod.cantidad
                        ProductosTable.update({ ProductosTable.id eq prod.id }) {
                            it[stock] = if (currentStock >= reduction) currentStock - reduction else 0
                        }
                    }
                }

                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Venta registrada con éxito y stock actualizado"))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al procesar venta: ${e.message}"))
            }
        }
    }
}
