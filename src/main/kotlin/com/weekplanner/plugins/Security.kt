package com.weekplanner.plugins

import com.weekplanner.auth.AuthService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.response.*
import com.weekplanner.common.ErrorResponse
import java.util.UUID

const val JWT_AUTH = "auth-jwt"

fun Application.configureSecurity(authService: AuthService) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            validate { credential ->
                val sub = credential.payload.subject ?: return@validate null
                val userId = runCatching { UUID.fromString(sub) }.getOrNull() ?: return@validate null
                authService.findUserById(userId) ?: return@validate null
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired token"))
            }
        }
    }
}

/** Retrieves the authenticated user's UUID from the JWT principal. */
fun ApplicationCall.authenticatedUserId(): UUID {
    val principal = principal<JWTPrincipal>()
        ?: error("No JWT principal found — ensure the route is protected with authenticate(\"$JWT_AUTH\")")
    return UUID.fromString(principal.payload.subject)
}
