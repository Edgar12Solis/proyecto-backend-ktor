package com.example.routes

import com.example.data.*
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.reportRoutes() {
    authenticate("auth-jwt") {

        // 1. Estadísticas Generales
        get("/admin/reports/stats") {
            val start = call.request.queryParameters["startDate"] ?: ""
            val end = call.request.queryParameters["endDate"] ?: ""
            
            try {
                val stats = transaction {
                    val query = VentasTable.selectAll()
                    if (start.isNotEmpty()) query.andWhere { VentasTable.date greaterEq start }
                    if (end.isNotEmpty()) query.andWhere { VentasTable.date lessEq end }

                    val totalIncome = query.sumOf { it[VentasTable.totalAmount] }
                    val totalApps = query.count().toInt()

                    // Top Barbero (Simplificado: Barbero con más ventas)
                    val topBarberRow = VentasTable
                        .slice(VentasTable.barberoId, VentasTable.barberoId.count())
                        .selectAll()
                        .groupBy(VentasTable.barberoId)
                        .orderBy(VentasTable.barberoId.count() to SortOrder.DESC)
                        .limit(1)
                        .singleOrNull()

                    val topBarberName = topBarberRow?.let {
                        val bId = it[VentasTable.barberoId]
                        UsuariosTable.selectAll().where { UsuariosTable.id eq bId }.single()[UsuariosTable.nombre]
                    }
                    val topCount = topBarberRow?.get(VentasTable.barberoId.count())?.toInt() ?: 0

                    GeneralStatsResponse(totalApps, totalIncome, topBarberName, topCount)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error en reportes")
            }
        }

        // 2. Productos Vendidos
        get("/admin/reports/sold-products") {
            val start = call.request.queryParameters["startDate"] ?: ""
            val end = call.request.queryParameters["endDate"] ?: ""

            try {
                val sold = transaction {
                    (DetalleVentasTable innerJoin VentasTable)
                        .selectAll()
                        .where { DetalleVentasTable.itemType eq "product" }
                        .let { q ->
                            if (start.isNotEmpty()) q.andWhere { VentasTable.date greaterEq start }
                            if (end.isNotEmpty()) q.andWhere { VentasTable.date lessEq end }
                            q
                        }
                        .map { row ->
                            SoldProductDTO(
                                name = row[DetalleVentasTable.itemName],
                                quantity = row[DetalleVentasTable.quantity],
                                price = row[DetalleVentasTable.price],
                                date = row[VentasTable.date],
                                customer = row[VentasTable.ghostName] ?: "Cliente"
                            )
                        }
                }
                call.respond(sold)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }
        
        // 3. Detalle de Venta
        get("/admin/reports/sale-details/{ventaId}") {
            val vId = call.parameters["ventaId"]?.toIntOrNull()
            if (vId == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@get
            }
            try {
                val details = transaction {
                    DetalleVentasTable.selectAll().where { DetalleVentasTable.ventaId eq vId }.map {
                        SaleDetailDTO(it[DetalleVentasTable.itemName], "$${String.format("%.2f", it[DetalleVentasTable.price])}")
                    }
                }
                call.respond(details)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }
    }
}
