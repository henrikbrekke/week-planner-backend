package com.weekplanner.images

import com.weekplanner.images.models.ImageRecord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

// ---------------------------------------------------------------------------
// Exposed table definitions
// ---------------------------------------------------------------------------

object ImagesTable : Table("images") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id")
    val name = varchar("name", 255)
    val filename = varchar("filename", 255)
    val mimeType = varchar("mime_type", 100)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object ImageTagsTable : Table("image_tags") {
    val imageId = uuid("image_id").references(ImagesTable.id, onDelete = ReferenceOption.CASCADE)
    val tag = varchar("tag", 100)

    override val primaryKey = PrimaryKey(imageId, tag)
}

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

class ImageRepository {

    fun findByUser(userId: UUID, filterTags: List<String> = emptyList()): List<ImageRecord> = transaction {
        val rows = ImagesTable
            .select { ImagesTable.userId eq userId }
            .orderBy(ImagesTable.createdAt, SortOrder.DESC)

        rows.map { row ->
            val imageId = row[ImagesTable.id]
            val tags = ImageTagsTable
                .select { ImageTagsTable.imageId eq imageId }
                .map { it[ImageTagsTable.tag] }
            ImageRecord(
                id = imageId,
                userId = row[ImagesTable.userId],
                name = row[ImagesTable.name],
                tags = tags,
                filename = row[ImagesTable.filename],
                mimeType = row[ImagesTable.mimeType],
                createdAt = row[ImagesTable.createdAt],
            )
        }.filter { image ->
            filterTags.isEmpty() || filterTags.all { it in image.tags }
        }
    }

    fun findByIdAndUser(id: UUID, userId: UUID): ImageRecord? = transaction {
        val row = ImagesTable
            .select { (ImagesTable.id eq id) and (ImagesTable.userId eq userId) }
            .singleOrNull() ?: return@transaction null

        val tags = ImageTagsTable
            .select { ImageTagsTable.imageId eq id }
            .map { it[ImageTagsTable.tag] }

        ImageRecord(
            id = row[ImagesTable.id],
            userId = row[ImagesTable.userId],
            name = row[ImagesTable.name],
            tags = tags,
            filename = row[ImagesTable.filename],
            mimeType = row[ImagesTable.mimeType],
            createdAt = row[ImagesTable.createdAt],
        )
    }

    fun insert(
        userId: UUID,
        name: String,
        filename: String,
        mimeType: String,
        tags: List<String>,
    ): ImageRecord = transaction {
        val newId = UUID.randomUUID()
        val now = Instant.now()
        ImagesTable.insert {
            it[ImagesTable.id] = newId
            it[ImagesTable.userId] = userId
            it[ImagesTable.name] = name
            it[ImagesTable.filename] = filename
            it[ImagesTable.mimeType] = mimeType
            it[ImagesTable.createdAt] = now
        }
        if (tags.isNotEmpty()) {
            ImageTagsTable.batchInsert(tags) { t ->
                this[ImageTagsTable.imageId] = newId
                this[ImageTagsTable.tag] = t
            }
        }
        ImageRecord(newId, userId, name, tags, filename, mimeType, now)
    }

    fun update(id: UUID, userId: UUID, name: String?, tags: List<String>?): ImageRecord? = transaction {
        val exists = ImagesTable
            .select { (ImagesTable.id eq id) and (ImagesTable.userId eq userId) }
            .singleOrNull() ?: return@transaction null

        if (name != null) {
            ImagesTable.update({ (ImagesTable.id eq id) and (ImagesTable.userId eq userId) }) {
                it[ImagesTable.name] = name
            }
        }
        if (tags != null) {
            ImageTagsTable.deleteWhere { ImageTagsTable.imageId eq id }
            if (tags.isNotEmpty()) {
                ImageTagsTable.batchInsert(tags) { t ->
                    this[ImageTagsTable.imageId] = id
                    this[ImageTagsTable.tag] = t
                }
            }
        }

        val finalTags = ImageTagsTable
            .select { ImageTagsTable.imageId eq id }
            .map { it[ImageTagsTable.tag] }

        ImageRecord(
            id = id,
            userId = exists[ImagesTable.userId],
            name = if (name != null) name else exists[ImagesTable.name],
            tags = finalTags,
            filename = exists[ImagesTable.filename],
            mimeType = exists[ImagesTable.mimeType],
            createdAt = exists[ImagesTable.createdAt],
        )
    }

    fun delete(id: UUID, userId: UUID): String? = transaction {
        val row = ImagesTable
            .select { (ImagesTable.id eq id) and (ImagesTable.userId eq userId) }
            .singleOrNull() ?: return@transaction null

        val filename = row[ImagesTable.filename]
        ImagesTable.deleteWhere { (ImagesTable.id eq id) and (ImagesTable.userId eq userId) }
        filename
    }
}
