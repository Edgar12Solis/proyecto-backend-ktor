package com.example.data

import com.example.plugins.PasswordHasher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init() {
        val url = "jdbc:postgresql://${System.getenv("PGHOST")}:${System.getenv("PGPORT")}/${System.getenv("PGDATABASE")}"
        val user = System.getenv("PGUSER")
        val password = System.getenv("PGPASSWORD")

        Database.connect(
            url = url,
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )

        transaction {
            // Un solo comando robusto para todo. 
            SchemaUtils.createMissingTablesAndColumns(
                UsuariosTable, 
                ClientesTable,
                PerfilesClientesTable, 
                PerfilesBarberosTable,
                PerfilesAdminsTable,
                CitasTable,
                CategoriasServiciosTable,
                ServiciosTable,
                CategoriasProductosTable,
                ProductosTable,
                PromocionesTable,
                PromocionServiciosTable,
                VentasTable,
                DetalleVentasTable,
                HorariosBarberosTable
            )
            
            // CREACIÓN AUTOMÁTICA DE ADMIN (Si no existe ninguno)
            val adminExists = UsuariosTable.selectAll().where { UsuariosTable.rol eq "ADMIN" }.count() > 0
            if (!adminExists) {
                val adminId = UsuariosTable.insertAndGetId {
                    it[UsuariosTable.nombre] = "Administrador Sistema"
                    it[UsuariosTable.email] = "admin@wolf.com"
                    it[UsuariosTable.password] = PasswordHasher.hash("admin123")
                    it[UsuariosTable.rol] = "ADMIN"
                }
                
                PerfilesAdminsTable.insert {
                    it[PerfilesAdminsTable.usuarioId] = adminId
                    it[PerfilesAdminsTable.nombres] = "Administrador"
                    it[PerfilesAdminsTable.apellidos] = "Sistema"
                    it[PerfilesAdminsTable.telefono] = "0000000000"
                }

                println("👑 Administrador por defecto creado: admin@wolf.com / admin123")
            }

            // ASEGURAR PERFILES PARA TODOS LOS ADMINS EXISTENTES
            val allAdmins = UsuariosTable.selectAll().where { UsuariosTable.rol eq "ADMIN" }.toList()
            for (admin in allAdmins) {
                val adminId = admin[UsuariosTable.id]
                val hasProfile = PerfilesAdminsTable.selectAll().where { PerfilesAdminsTable.usuarioId eq adminId }.count() > 0
                if (!hasProfile) {
                    val fullNombre = admin[UsuariosTable.nombre]
                    val parts = fullNombre.split(" ")
                    PerfilesAdminsTable.insert {
                        it[PerfilesAdminsTable.usuarioId] = adminId
                        it[PerfilesAdminsTable.nombres] = parts.firstOrNull() ?: "Admin"
                        it[PerfilesAdminsTable.apellidos] = if (parts.size > 1) parts.drop(1).joinToString(" ") else "Sistema"
                        it[PerfilesAdminsTable.telefono] = "0000000000"
                    }
                    println("✅ Perfil de administrador creado para: ${admin[UsuariosTable.email]}")
                }
            }
            
            println("✅ Database Synced: Todas las tablas y columnas están listas.")
        }
    }
}
