package com.weekplanner.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

fun Application.configureDatabase(): DataSource {
    val rawUrl = System.getenv("DATABASE_URL")
        ?: "postgresql://localhost:5432/weekplanner"

    // Accept both postgresql:// and jdbc:postgresql:// formats
    val jdbcUrl = if (rawUrl.startsWith("jdbc:")) rawUrl else "jdbc:$rawUrl"

    val config = HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        minimumIdle = 2
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        poolName = "WeekPlannerPool"
        validate()
    }
    val dataSource = HikariDataSource(config)

    // Run Flyway migrations
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    // Connect Exposed to the pool
    Database.connect(dataSource)

    return dataSource
}
