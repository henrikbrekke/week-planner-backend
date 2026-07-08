package com.weekplanner

import com.weekplanner.auth.AuthRepository
import com.weekplanner.auth.AuthService
import com.weekplanner.images.ImageRepository
import com.weekplanner.images.ImageService
import com.weekplanner.plans.PlanRepository
import com.weekplanner.plans.PlanService
import com.weekplanner.plugins.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.io.File

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // 1. Serialization (JSON)
    configureSerialization()

    // 2. Database: HikariCP + Flyway + Exposed
    configureDatabase()

    // 3. Resolve upload directory
    val storageDir = System.getenv("STORAGE_DIR") ?: "./storage"
    val uploadDir = File(storageDir, "uploads").also { it.mkdirs() }

    // 4. Wire up the dependency graph
    val authRepository = AuthRepository()
    val imageRepository = ImageRepository()
    val planRepository = PlanRepository()

    val accessSecret = System.getenv("JWT_ACCESS_SECRET")
        ?: devFallback("JWT_ACCESS_SECRET")
    val refreshSecret = System.getenv("JWT_REFRESH_SECRET")
        ?: devFallback("JWT_REFRESH_SECRET")
    val googleClientId = System.getenv("GOOGLE_CLIENT_ID") ?: ""

    val authService = AuthService(authRepository, accessSecret, refreshSecret, googleClientId)
    val imageService = ImageService(imageRepository, uploadDir)
    val planService = PlanService(planRepository, imageRepository, imageService)

    // 5. Security (JWT authentication)
    configureSecurity(authService)

    // 6. Routing (CORS, routes, static files, error pages)
    configureRouting(authService, imageService, planService, uploadDir.absolutePath)
}

private fun devFallback(name: String): String {
    val isProduction = System.getenv("KTOR_ENV") == "production"
    if (isProduction) error("$name must be configured in production")
    return "local-development-${name.lowercase()}"
}
