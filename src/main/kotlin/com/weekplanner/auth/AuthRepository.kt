package com.weekplanner.auth

import com.weekplanner.auth.models.UserRecord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

// ---------------------------------------------------------------------------
// Exposed table definitions
// ---------------------------------------------------------------------------

object UsersTable : Table("users") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val passwordHash = varchar("password_hash", 255).nullable()
    val googleSub = varchar("google_sub", 255).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val token = text("token")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

class AuthRepository {

    fun findByEmail(email: String): UserRecord? = transaction {
        UsersTable
            .select { UsersTable.email eq email }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun findById(id: UUID): UserRecord? = transaction {
        UsersTable
            .select { UsersTable.id eq id }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun findByGoogleSub(sub: String): UserRecord? = transaction {
        UsersTable
            .select { UsersTable.googleSub eq sub }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun existsByEmail(email: String): Boolean = transaction {
        UsersTable.select { UsersTable.email eq email }.count() > 0
    }

    fun insertUser(
        name: String,
        email: String,
        passwordHash: String?,
        googleSub: String?,
    ): UserRecord = transaction {
        val newId = UUID.randomUUID()
        val now = Instant.now()
        UsersTable.insert {
            it[UsersTable.id] = newId
            it[UsersTable.name] = name
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.googleSub] = googleSub
            it[UsersTable.createdAt] = now
        }
        UsersTable
            .select { UsersTable.id eq newId }
            .single()
            .toUserRecord()
    }

    fun updateGoogleSub(userId: UUID, sub: String) = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[googleSub] = sub
        }
    }

    fun addRefreshToken(userId: UUID, token: String) = transaction {
        RefreshTokensTable.insert {
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.token] = token
        }
    }

    fun hasRefreshToken(userId: UUID, token: String): Boolean = transaction {
        RefreshTokensTable
            .select { (RefreshTokensTable.userId eq userId) and (RefreshTokensTable.token eq token) }
            .count() > 0
    }

    fun deleteAllRefreshTokens(userId: UUID) = transaction {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
    }

    // ---------------------------------------------------------------------------
    // Mapping helpers
    // ---------------------------------------------------------------------------

    private fun ResultRow.toUserRecord() = UserRecord(
        id = this[UsersTable.id],
        name = this[UsersTable.name],
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        googleSub = this[UsersTable.googleSub],
        createdAt = this[UsersTable.createdAt],
    )
}
