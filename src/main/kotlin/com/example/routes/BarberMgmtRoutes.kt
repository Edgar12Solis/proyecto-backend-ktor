package com.example.routes

import com.example.data.*
import com.example.models.*
import com.example.plugins.PasswordHasher
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.barberMgmtRoutes() {
    authenticate("auth-jwt") {

        // 1. Listar Barberos
        get("/admin/barbers") {
            try {
                val barbers = transaction {
                    UsuariosTable
                        .selectAll()
                        .where { UsuariosTable.rol eq "BARBERO" }
                        .map { row ->
                            val bId = row[UsuariosTable.id].value
                            
                            val specs = (BarberoEspecialidadesTable innerJoin CategoriasServiciosTable)
                                .selectAll()
                                .where { BarberoEspecialidadesTable.usuarioId eq bId }
                                .map { it[CategoriasServiciosTable.nombre] }
                            
                            val profile = PerfilesBarberosTable.selectAll().where { PerfilesBarberosTable.usuarioId eq bId }.singleOrNull()

                            BarberFullProfileResponse(
                                id = bId,
                                nombreCompleto = row[UsuariosTable.nombre],
                                email = row[UsuariosTable.email],
                                telefono = profile?.get(PerfilesBarberosTable.telefono) ?: "",
                                bio = profile?.get(PerfilesBarberosTable.biografia) ?: "",
                                activo = row[UsuariosTable.activo],
                                scheduleConfiguration = row[UsuariosTable.scheduleConfig] ?: "",
                                specialties = specs,
                                imagenUrl = row[UsuariosTable.imagenUrl]
                            )
                        }
                }
                call.respond(barbers)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al listar barberos")
            }
        }

        // 2. Crear Barbero (Multipart)
        post("/admin/barbers") {
            try {
                val multipart = call.receiveMultipart()
                var barberDTO: BarberCreateRequest? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "barber") {
                            barberDTO = Json { ignoreUnknownKeys = true }.decodeFromString<BarberCreateRequest>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (barberDTO == null) return@post call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl = barberDTO!!.imagenUrl
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "barber_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/barbers/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/barbers/$fileName"
                    }

                    // 1. Insertar en UsuariosTable
                    val userId = UsuariosTable.insertAndGetId {
                        it[nombre] = barberDTO!!.nombreCompleto
                        it[email] = barberDTO!!.email
                        it[password] = PasswordHasher.hash(barberDTO!!.password ?: "barber123")
                        it[rol] = "BARBERO"
                        it[bio] = barberDTO!!.bio
                        it[scheduleConfig] = barberDTO!!.scheduleConfiguration ?: ""
                        it[activo] = barberDTO!!.activo ?: true
                        it[imagenUrl] = finalImageUrl
                    }

                    PerfilesBarberosTable.insert {
                        it[usuarioId] = userId
                        it[telefono] = barberDTO!!.telefono
                        it[especialidad] = barberDTO!!.specialties.firstOrNull() ?: "General"
                        it[biografia] = barberDTO!!.bio
                    }

                    // 3. Insertar Especialidades (Si existen)
                    barberDTO!!.specialties.forEach { specName ->
                        val catRow = CategoriasServiciosTable
                            .selectAll().where { CategoriasServiciosTable.nombre eq specName }
                            .singleOrNull()

                        if (catRow != null) {
                            BarberoEspecialidadesTable.insert {
                                it[usuarioId] = userId
                                it[categoriaId] = catRow[CategoriasServiciosTable.id]
                            }
                        }
                    }
                    
                    // 4. Inicializar Horario
                    val horario = barberDTO!!.scheduleConfiguration ?: ""
                    if (horario.isNotEmpty()) {
                        HorariosBarberosTable.insert {
                            it[barberoId] = userId
                            it[config] = horario
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, AdminActionResponse(true, "Barbero creado con éxito"))
            } catch (e: Exception) {
                e.printStackTrace() // Ver el error real en la consola de Railway
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al guardar: ${e.message}"))
            }
        }

        // 3. Editar Barbero (Multipart)
        put("/admin/barbers/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val multipart = call.receiveMultipart()
                var barberDTO: BarberCreateRequest? = null
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "barber") {
                            barberDTO = Json { ignoreUnknownKeys = true }.decodeFromString<BarberCreateRequest>(part.value)
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.provider().readRemaining().readByteArray()
                            extension = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (barberDTO == null) return@put call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Falta info"))

                transaction {
                    var finalImageUrl: String? = null
                    if (imageBytes != null && imageBytes!!.isNotEmpty()) {
                        val fileName = "barber_${System.currentTimeMillis()}.$extension"
                        val file = java.io.File("uploads/barbers/$fileName")
                        file.parentFile.mkdirs()
                        file.writeBytes(imageBytes!!)
                        val host = call.request.headers["Host"] ?: "localhost:8080"
                        val scheme = if (host.contains("localhost")) "http" else "https"
                        finalImageUrl = "$scheme://$host/uploads/barbers/$fileName"
                    }

                    UsuariosTable.update({ UsuariosTable.id eq id }) {
                        it[nombre] = barberDTO!!.nombreCompleto
                        it[email] = barberDTO!!.email
                        it[bio] = barberDTO!!.bio
                        it[activo] = barberDTO!!.activo ?: true
                        if (finalImageUrl != null) it[imagenUrl] = finalImageUrl
                    }

                    PerfilesBarberosTable.update({ PerfilesBarberosTable.usuarioId eq id }) {
                        it[telefono] = barberDTO!!.telefono
                        it[especialidad] = barberDTO!!.specialties.firstOrNull() ?: "General"
                        it[biografia] = barberDTO!!.bio
                    }

                    BarberoEspecialidadesTable.deleteWhere { BarberoEspecialidadesTable.usuarioId eq id }
                    barberDTO!!.specialties.forEach { specName ->
                        val catId = CategoriasServiciosTable
                            .selectAll()
                            .where { CategoriasServiciosTable.nombre eq specName }
                            .singleOrNull()?.get(CategoriasServiciosTable.id)

                        if (catId != null) {
                            BarberoEspecialidadesTable.insert {
                                it[usuarioId] = id
                                it[categoriaId] = catId
                            }
                        }
                    }
                }
                call.respond(AdminActionResponse(true, "Barbero actualizado correctamente"))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error: ${e.message}"))
            }
        }

        // 4. Actualizar Horario Específico
        post("/admin/barbers/{id}/horario") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                val req = call.receive<BarberScheduleRequest>()
                transaction {
                    val exists = HorariosBarberosTable.selectAll().where { HorariosBarberosTable.barberoId eq id }.count() > 0
                    if (exists) {
                        HorariosBarberosTable.update({ HorariosBarberosTable.barberoId eq id }) {
                            it[config] = req.config
                        }
                    } else {
                        HorariosBarberosTable.insert {
                            it[barberoId] = id
                            it[config] = req.config
                        }
                    }
                    // También actualizar en UsuariosTable para consistencia
                    UsuariosTable.update({ UsuariosTable.id eq id }) {
                        it[scheduleConfig] = req.config
                    }
                }
                call.respond(AdminActionResponse(true, "Horario actualizado con éxito"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al actualizar horario"))
            }
        }

        // 5. Eliminar Barbero (Despido)
        delete("/admin/barbers/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, "ID inválido")
            try {
                transaction {
                    UsuariosTable.deleteWhere { UsuariosTable.id eq id }
                }
                call.respond(AdminActionResponse(true, "Barbero eliminado permanentemente"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AdminActionResponse(false, "Error al eliminar"))
            }
        }
        
        // 6. Estadísticas
        get("/admin/barbers/stats") {
            try {
                val stats = transaction {
                    val total = UsuariosTable.selectAll().where { UsuariosTable.rol eq "BARBERO" }.count().toInt()
                    val active = UsuariosTable.selectAll().where { (UsuariosTable.rol eq "BARBERO") and (UsuariosTable.activo eq true) }.count().toInt()
                    val off = total - active
                    BarberStats(total, active, off)
                }
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
    }
}
