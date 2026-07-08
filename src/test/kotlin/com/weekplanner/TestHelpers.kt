package com.weekplanner

import com.weekplanner.auth.AuthRepository
import com.weekplanner.auth.AuthService
import com.weekplanner.images.ImageRepository
import com.weekplanner.images.ImageService
import com.weekplanner.plans.PlanRepository
import com.weekplanner.plans.PlanService
import com.weekplanner.plugins.*
import io.ktor.server.testing.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.nio.file.Files

/** Shared container that is started once per test run. */
object TestPostgres {
    val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).apply {
            start()
        }
    }

    fun dataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }
        return HikariDataSource(config)
    }
}

/**
 * Creates a test Ktor application wired with a real (containerised) PostgreSQL
 * database and an isolated upload directory.  Each call gets a fresh schema via
 * Flyway `clean` + `migrate`.
 */
fun testApp(block: suspend ApplicationTestBuilder.(uploadDir: File) -> Unit) {
    val ds = TestPostgres.dataSource()
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()
        .apply { clean(); migrate() }

    Database.connect(ds)

    val uploadDir = Files.createTempDirectory("week-planner-test-uploads").toFile()

    try {
        testApplication {
            application {
                configureSerialization()

                val accessSecret = "test-access-secret"
                val refreshSecret = "test-refresh-secret"
                val googleClientId = ""

                val authRepository = AuthRepository()
                val imageRepository = ImageRepository()
                val planRepository = PlanRepository()

                val authService = AuthService(authRepository, accessSecret, refreshSecret, googleClientId)
                val imageService = ImageService(imageRepository, uploadDir)
                val planService = PlanService(planRepository, imageRepository, imageService)

                configureSecurity(authService)
                configureRouting(authService, imageService, planService, uploadDir.absolutePath)
            }

            block(uploadDir)
        }
    } finally {
        uploadDir.deleteRecursively()
        ds.close()
    }
}
