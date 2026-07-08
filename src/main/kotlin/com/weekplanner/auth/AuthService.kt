package com.weekplanner.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.weekplanner.auth.models.*
import com.weekplanner.common.AppException
import com.weekplanner.common.normalizeEmail
import org.mindrot.jbcrypt.BCrypt
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val ACCESS_TOKEN_TTL_HOURS = 1L
private const val REFRESH_TOKEN_TTL_DAYS = 30L

class AuthService(
    private val repository: AuthRepository,
    private val accessSecret: String,
    private val refreshSecret: String,
    private val googleClientId: String,
) {
    private val accessAlgorithm = Algorithm.HMAC256(accessSecret)
    private val refreshAlgorithm = Algorithm.HMAC256(refreshSecret)

    fun register(name: String, email: String, password: String): AuthResponse {
        val normalizedEmail = email.normalizeEmail()
        if (repository.existsByEmail(normalizedEmail)) {
            throw AppException.Conflict("Email is already registered")
        }
        val hash = BCrypt.hashpw(password, BCrypt.gensalt(10))
        val user = repository.insertUser(name.trim(), normalizedEmail, hash, null)
        val tokens = issueTokens(user.id, user.email)
        repository.addRefreshToken(user.id, tokens.refreshToken)
        return AuthResponse(user.toPublic(), tokens)
    }

    fun login(email: String, password: String): AuthResponse {
        val normalizedEmail = email.normalizeEmail()
        val user = repository.findByEmail(normalizedEmail)
        val hash = user?.passwordHash
        if (hash == null || !BCrypt.checkpw(password, hash)) {
            throw AppException.Unauthorized("Invalid email or password")
        }
        val tokens = issueTokens(user.id, user.email)
        repository.addRefreshToken(user.id, tokens.refreshToken)
        return AuthResponse(user.toPublic(), tokens)
    }

    fun loginWithGoogle(idToken: String): AuthResponse {
        if (googleClientId.isBlank()) {
            throw AppException.ServiceUnavailable("Google OAuth is not configured")
        }
        val payload = verifyGoogleToken(idToken)
            ?: throw AppException.Unauthorized("Invalid Google credential")

        val sub = payload["sub"] ?: throw AppException.Unauthorized("Invalid Google credential")
        val email = (payload["email"] ?: throw AppException.Unauthorized("Invalid Google credential"))
            .normalizeEmail()
        val name = payload["name"] ?: email.substringBefore('@')

        val existingByGoogle = repository.findByGoogleSub(sub)
        val existingByEmail = repository.findByEmail(email)
        val user = when {
            existingByGoogle != null -> existingByGoogle
            existingByEmail != null -> {
                repository.updateGoogleSub(existingByEmail.id, sub)
                existingByEmail
            }
            else -> repository.insertUser(name, email, null, sub)
        }

        val tokens = issueTokens(user.id, user.email)
        repository.addRefreshToken(user.id, tokens.refreshToken)
        return AuthResponse(user.toPublic(), tokens)
    }

    fun logout(userId: UUID) {
        repository.deleteAllRefreshTokens(userId)
    }

    fun validateAccessToken(token: String): UUID? {
        return try {
            val verifier = JWT.require(accessAlgorithm).build()
            val decoded = verifier.verify(token)
            UUID.fromString(decoded.subject)
        } catch (_: Exception) {
            null
        }
    }

    fun findUserById(id: UUID) = repository.findById(id)

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun issueTokens(userId: UUID, email: String): TokenPair {
        val now = System.currentTimeMillis()
        val accessToken = JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + TimeUnit.HOURS.toMillis(ACCESS_TOKEN_TTL_HOURS)))
            .sign(accessAlgorithm)

        val refreshToken = JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + TimeUnit.DAYS.toMillis(REFRESH_TOKEN_TTL_DAYS)))
            .sign(refreshAlgorithm)

        return TokenPair(accessToken, refreshToken)
    }

    /**
     * Verifies a Google ID token and returns its claims, or `null` on failure.
     * Uses the Google token-info endpoint to avoid bundling the full Google SDK
     * verification chain, which requires exact audience matching.
     */
    private fun verifyGoogleToken(idToken: String): Map<String, String>? {
        return try {
            val transport = com.google.api.client.http.javanet.NetHttpTransport()
            val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
            val verifier = com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(listOf(googleClientId))
                .build()
            val token = verifier.verify(idToken) ?: return null
            val payload = token.payload
            mapOf(
                "sub" to payload.subject,
                "email" to (payload["email"] as? String ?: ""),
                "name" to (payload["name"] as? String ?: ""),
            )
        } catch (_: Exception) {
            null
        }
    }
}
