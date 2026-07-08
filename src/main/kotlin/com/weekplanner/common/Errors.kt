package com.weekplanner.common

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/** Typed domain exceptions that map to HTTP status codes. */
sealed class AppException(message: String) : Exception(message) {
    class Unauthorized(message: String = "Unauthorized") : AppException(message)
    class Forbidden(message: String = "Forbidden") : AppException(message)
    class NotFound(message: String) : AppException(message)
    class Conflict(message: String) : AppException(message)
    class BadRequest(message: String) : AppException(message)
    class ServiceUnavailable(message: String) : AppException(message)
}

/** Standard JSON error body. */
@kotlinx.serialization.Serializable
data class ErrorResponse(val error: String)

/** Convenience extension that maps [AppException] subtypes to HTTP status codes. */
suspend fun ApplicationCall.respondException(ex: AppException) {
    val status = when (ex) {
        is AppException.BadRequest -> HttpStatusCode.BadRequest
        is AppException.Unauthorized -> HttpStatusCode.Unauthorized
        is AppException.Forbidden -> HttpStatusCode.Forbidden
        is AppException.NotFound -> HttpStatusCode.NotFound
        is AppException.Conflict -> HttpStatusCode.Conflict
        is AppException.ServiceUnavailable -> HttpStatusCode.ServiceUnavailable
    }
    respond(status, ErrorResponse(ex.message ?: "Unknown error"))
}
