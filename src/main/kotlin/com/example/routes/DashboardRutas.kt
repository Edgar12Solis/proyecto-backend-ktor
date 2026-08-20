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
import java.time.LocalDate

fun Route.dashboardRutas() {
    authenticate("auth-jwt") {

        get("/admin/dashboard/stats") {
            try {
                val hoy = LocalDate.now().toString()
                val inicioMes = LocalDate.now().withDayOfMonth(1).toString()

                val stats = transaction {
                    // 1. Ingresos Totales (Citas Completadas)
                    val ingresos = CitasTable
                        .selectAll()
                        .where { CitasTable.status eq "Completada" }
                        .sumOf { it[CitasTable.totalPrice] }

                    // 2. Citas Totales de Hoy
                    val citasHoy = CitasTable
                        .selectAll()
                        .where { CitasTable.date eq hoy }
                        .count().toInt()

                    // 3. Equipo Total (Barberos)
                    val equipoTotal = UsuariosTable
                        .selectAll()
                        .where { UsuariosTable.rol eq "BARBERO" }
                        .count().toInt()

                    // 4. Barberos Activos
                    val barberosActivos = UsuariosTable
                        .selectAll()
                        .where { (UsuariosTable.rol eq "BARBERO") and (UsuariosTable.activo eq true) }
                        .count().toInt()

                    // 5. Rendimiento (Ejemplo: basado en una meta de $10,000 al mes)
                    val metaMensual = 10000.0
                    val ingresosMes = CitasTable
                        .selectAll()
                        .where { (CitasTable.status eq "Completada") and (CitasTable.date greaterEq inicioMes) }
                        .sumOf { it[CitasTable.totalPrice] }
                    
                    val porcentaje = if (metaMensual > 0) (ingresosMes / metaMensual) * 100 else 0.0

                    DashboardStats(
                        ingresos = ingresos,
                        citas = citasHoy,
                        equipo = equipoTotal,
                        activos = barberosActivos,
                        porcentajeMeta = if (porcentaje > 100) 100.0 else porcentaje
                    )
                }

                call.respond(stats)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error al cargar dashboard")
            }
        }
    }
}
