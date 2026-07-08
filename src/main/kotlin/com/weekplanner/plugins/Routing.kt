package com.weekplanner.plugins

import com.weekplanner.auth.AuthService
import com.weekplanner.auth.authRoutes
import com.weekplanner.common.AppException
import com.weekplanner.common.ErrorResponse
import com.weekplanner.common.respondException
import com.weekplanner.images.ImageService
import com.weekplanner.images.imageRoutes
import com.weekplanner.plans.PlanService
import com.weekplanner.plans.planRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    authService: AuthService,
    imageService: ImageService,
    planService: PlanService,
    uploadDirPath: String,
) {
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    install(DefaultHeaders)

    install(StatusPages) {
        exception<AppException> { call, ex ->
            call.respondException(ex)
        }
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    routing {
        get("/api/health") {
            call.respond(mapOf("ok" to true))
        }

        // Serve uploaded images
        staticFiles("/uploads", java.io.File(uploadDirPath))

        authRoutes(authService)
        imageRoutes(imageService)
        planRoutes(planService)
    }
}
