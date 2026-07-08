package com.weekplanner.auth.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Full user record as stored/retrieved from the database (internal use only). */
data class UserRecord(
    val id: UUID,
    val name: String,
    val email: String,
    val passwordHash: String?,
    val googleSub: String?,
    val createdAt: Instant,
)

/** Public user representation returned to clients. */
@Serializable
data class PublicUser(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: String,
)

fun UserRecord.toPublic() = PublicUser(
    id = id.toString(),
    name = name,
    email = email,
    createdAt = createdAt.toString(),
)

/** Request bodies */
@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class GoogleAuthRequest(
    val credential: String,
)

/** Auth response that bundles the public user with issued tokens. */
@Serializable
data class AuthResponse(
    val user: PublicUser,
    val tokens: TokenPair,
)
