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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

fun Route.terminalRoutes() {
    authenticate("auth-jwt") {

        // 1. Listar Servicios (Formato simplificado para Terminal)
        get("/admin/terminal/services") {
            try {
                val services = transaction {
                    ServiciosTable.selectAll().where { ServiciosTable.activo eq true }.map {
                        ServiceDTO(
                            id = it[ServiciosTable.id].value,
                            nombre = it[ServiciosTable.nombre],
                            precio = it[ServiciosTable.precio],
                            duracion = it[ServiciosTable.duracion],
                            activo = it[ServiciosTable.activo],
                            imagenUrl = it[ServiciosTable.imagenUrl],
                            serviceCategory = ServiceCategoryDTO(0, "") // Simplificado
                        )
                    }
                }
                call.respond(services)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al listar servicios")
            }
        }

        // 2. Procesar Venta Express (Ghost Sale)
        post("/admin/ghost-sale") {
            try {
                val req = call.receive<GhostSaleRequest>()
                val today = LocalDate.now().toString()
                
                val totalAmount = req.cartItems.sumOf { it.price }

                transaction {
                    // 1. Registrar Venta
                    val ventaId = VentasTable.insertAndGetId {
                        it[barberoId] = req.barberId
                        it[paymentMethod] = req.paymentMethod
                        it[amountReceived] = req.amountReceived
                        it[VentasTable.totalAmount] = totalAmount
                        it[ghostName] = req.ghostName
                        it[date] = today
                        it[isGhostSale] = true
                    }

                    // 2. Detalle y Stock
                    req.cartItems.forEach { item ->
                        DetalleVentasTable.insert {
                            it[DetalleVentasTable.ventaId] = ventaId
                            it[itemType] = item.type
                            it[itemId] = item.id
                            it[itemName] = item.name
                            it[price] = item.price
                        }

                        if (item.type == "product") {
                            ProductosTable.update({ ProductosTable.id eq item.id }) {
                                it.update(ProductosTable.stock, ProductosTable.stock minus 1)
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Venta procesada con éxito"))
            } catch (e: Exception) {
                println("❌ Ghost Sale Error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, AdminActionResponse(false, "Error al procesar venta: ${e.message}"))
            }
        }
    }
}
