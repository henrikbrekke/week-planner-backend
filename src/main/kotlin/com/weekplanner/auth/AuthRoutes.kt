package com.weekplanner.auth

import com.weekplanner.auth.models.*
import com.weekplanner.common.AppException
import com.weekplanner.common.ErrorResponse
import com.weekplanner.common.respondException
import com.weekplanner.plugins.JWT_AUTH
import com.weekplanner.plugins.authenticatedUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {

    route("/api/auth") {

        post("/register") {
            val body = runCatching { call.receive<RegisterRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("name, email and password are required"))

            val name = body.name.trim()
            val email = body.email.trim()
            val password = body.password

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("name, email and password are required"))
            }

            try {
                val response = authService.register(name, email, password)
                call.respond(HttpStatusCode.Created, response)
            } catch (ex: AppException) {
                call.respondException(ex)
            }
        }

        post("/login") {
            val body = runCatching { call.receive<LoginRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("email and password are required"))

            if (body.email.isBlank() || body.password.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("email and password are required"))
            }

            try {
                val response = authService.login(body.email, body.password)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: AppException) {
                call.respondException(ex)
            }
        }

        post("/google") {
            val body = runCatching { call.receive<GoogleAuthRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("credential is required"))

            if (body.credential.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("credential is required"))
            }

            try {
                val response = authService.loginWithGoogle(body.credential)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: AppException) {
                call.respondException(ex)
            }
        }

        authenticate(JWT_AUTH) {
            post("/logout") {
                authService.logout(call.authenticatedUserId())
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }

            get("/me") {
                val user = authService.findUserById(call.authenticatedUserId())
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                call.respond(user.toPublic())
            }
        }
    }
}
