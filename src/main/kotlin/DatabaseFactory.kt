package com.example
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init() {

        Database.connect(
            url = "jdbc:postgresql://postgres.railway.internal:5432/railway",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "LMLGTVPARtsKDrCEPPdVyjYWNBypSFLz"
        )

        transaction {
            println("✅ Conectado a PostgreSQL Railway")
        }
    }
}