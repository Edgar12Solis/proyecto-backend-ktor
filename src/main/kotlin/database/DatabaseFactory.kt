package com.example.database

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
            SchemaUtils.create(UsuariosTable, ClientesTable)
            println("✅ Conectado a PostgreSQL Railway y Tablas Sincronizadas")
        }
    }
}