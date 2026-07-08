package com.weekplanner.images

import com.weekplanner.common.AppException
import com.weekplanner.common.normalizeTags
import com.weekplanner.images.models.ImageRecord
import com.weekplanner.images.models.PublicImage
import io.ktor.server.application.*
import java.io.File
import java.io.InputStream
import java.util.UUID

class ImageService(
    private val repository: ImageRepository,
    private val uploadDir: File,
) {
    init {
        uploadDir.mkdirs()
    }

    fun getImages(userId: UUID, filterTags: List<String>): List<ImageRecord> =
        repository.findByUser(userId, filterTags)

    fun uploadImage(
        userId: UUID,
        name: String,
        tags: List<String>,
        originalFilename: String,
        mimeType: String,
        stream: InputStream,
    ): ImageRecord {
        val extension = File(originalFilename).extension.lowercase().let {
            if (it.isNotEmpty()) ".$it" else ".bin"
        }
        val storedFilename = "${UUID.randomUUID()}$extension"
        val targetFile = File(uploadDir, storedFilename)
        stream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return try {
            repository.insert(
                userId = userId,
                name = name.trim(),
                filename = storedFilename,
                mimeType = mimeType,
                tags = normalizeTags(tags),
            )
        } catch (ex: Exception) {
            targetFile.delete()
            throw ex
        }
    }

    fun patchImage(userId: UUID, imageId: UUID, name: String?, tags: List<String>?): ImageRecord {
        val normalizedName = name?.trim()
        if (normalizedName != null && normalizedName.isEmpty()) {
            throw AppException.BadRequest("name cannot be empty")
        }
        val normalizedTags = tags?.let { normalizeTags(it) }
        return repository.update(imageId, userId, normalizedName, normalizedTags)
            ?: throw AppException.NotFound("Image not found")
    }

    fun deleteImage(userId: UUID, imageId: UUID): String {
        val filename = repository.delete(imageId, userId)
            ?: throw AppException.NotFound("Image not found")
        File(uploadDir, filename).delete()
        return filename
    }

    fun imageUrl(call: ApplicationCall, filename: String): String {
        val proto = call.request.headers["X-Forwarded-Proto"] ?: "http"
        val host = call.request.headers["Host"] ?: "localhost"
        return "$proto://$host/uploads/$filename"
    }

    fun toPublicImage(call: ApplicationCall, image: ImageRecord) = PublicImage(
        id = image.id.toString(),
        name = image.name,
        tags = image.tags,
        url = imageUrl(call, image.filename),
        createdAt = image.createdAt.toString(),
    )
}
