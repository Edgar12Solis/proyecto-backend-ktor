package com.example.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
            // Exposed calculará el orden correcto (Usuarios primero, luego los Perfiles).
            SchemaUtils.createMissingTablesAndColumns(
                UsuariosTable, 
                ClientesTable,
                PerfilesClientesTable, 
                PerfilesBarberosTable,
                CitasTable
            )
            
            println("✅ Database Synced: Todas las tablas y columnas están listas.")
        }
    }
}
