package com.weekplanner.images.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Full image record as stored/retrieved from the database. */
data class ImageRecord(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val tags: List<String>,
    val filename: String,
    val mimeType: String,
    val createdAt: Instant,
)

/** Public image representation returned to clients (includes a resolved URL). */
@Serializable
data class PublicImage(
    val id: String,
    val name: String,
    val tags: List<String>,
    val url: String,
    val createdAt: String,
)

/** Request body for updating an image's metadata. */
@Serializable
data class PatchImageRequest(
    val name: String? = null,
    val tags: List<String>? = null,
)
