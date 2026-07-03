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
            // ORDEN CRÍTICO: 1. Crear tablas base primero
            SchemaUtils.create(UsuariosTable, ClientesTable)
            
            // ORDEN CRÍTICO: 2. Crear tablas que dependen de 'usuarios' (FKs)
            SchemaUtils.create(
                PerfilesClientesTable, 
                PerfilesBarberosTable,
                CitasTable
            )
            
            // Sincronizar columnas faltantes si las tablas ya existían
            SchemaUtils.createMissingTablesAndColumns(
                UsuariosTable, 
                ClientesTable,
                PerfilesClientesTable, 
                PerfilesBarberosTable,
                CitasTable
            )

            println("✅ Database Synced: Usuarios, Clientes, PerfilesClientes, PerfilesBarberos, Citas")
        }
    }
}
