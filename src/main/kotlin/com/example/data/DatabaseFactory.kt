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
            // SchemaUtils.create crea las tablas si no existen.
            // SchemaUtils.addMissingColumnsStatements genera los ALTER TABLE necesarios para columnas nuevas.
            SchemaUtils.create(
                UsuariosTable, 
                ClientesTable,
                PerfilesClientesTable, 
                PerfilesBarberosTable,
                CitasTable
            )
            
            // Forzamos la creación de columnas que falten en tablas existentes
            val missingColumns = SchemaUtils.addMissingColumnsStatements(
                UsuariosTable, 
                ClientesTable,
                PerfilesClientesTable, 
                PerfilesBarberosTable,
                CitasTable
            )
            
            if (missingColumns.isNotEmpty()) {
                println("⚠️ Añadiendo columnas faltantes a la BD...")
            }

            println("✅ Database Synced: Usuarios, Clientes, PerfilesClientes, PerfilesBarberos, Citas")
        }
    }
}
