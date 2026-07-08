package com.weekplanner.images

import com.weekplanner.common.AppException
import com.weekplanner.common.ErrorResponse
import com.weekplanner.common.respondException
import com.weekplanner.images.models.PatchImageRequest
import com.weekplanner.plugins.JWT_AUTH
import com.weekplanner.plugins.authenticatedUserId
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

private const val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5 MB

fun Route.imageRoutes(imageService: ImageService) {

    authenticate(JWT_AUTH) {
        route("/api/images") {

            get {
                val userId = call.authenticatedUserId()
                val filterTags = call.request.queryParameters["tags"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val images = imageService.getImages(userId, filterTags)
                    .map { imageService.toPublicImage(call, it) }
                call.respond(images)
            }

            post {
                val userId = call.authenticatedUserId()
                val multipart = call.receiveMultipart()

                var name = ""
                val tags = mutableListOf<String>()
                var fileBytes: ByteArray? = null
                var originalFilename = "upload"
                var mimeType = "application/octet-stream"

                multipart.forEachPart { part ->
                    when {
                        part is PartData.FormItem && part.name == "name" ->
                            name = part.value

                        part is PartData.FormItem && (part.name == "tags[]" || part.name == "tags") ->
                            tags.add(part.value)

                        part is PartData.FileItem && part.name == "image" -> {
                            val bytes = part.streamProvider().readBytes()
                            if (bytes.size <= MAX_FILE_SIZE) {
                                fileBytes = bytes
                                originalFilename = part.originalFileName ?: "upload"
                                mimeType = part.contentType?.toString() ?: "application/octet-stream"
                            }
                        }
                    }
                    part.dispose()
                }

                if (fileBytes == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("image is required"))
                }
                if (!mimeType.startsWith("image/")) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Only image files are allowed"))
                }
                if (name.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("name is required"))
                }

                try {
                    val image = imageService.uploadImage(
                        userId = userId,
                        name = name,
                        tags = tags,
                        originalFilename = originalFilename,
                        mimeType = mimeType,
                        stream = fileBytes!!.inputStream(),
                    )
                    call.respond(HttpStatusCode.Created, imageService.toPublicImage(call, image))
                } catch (ex: AppException) {
                    call.respondException(ex)
                }
            }

            patch("/{id}") {
                val userId = call.authenticatedUserId()
                val imageId = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image id"))

                val body = runCatching { call.receive<PatchImageRequest>() }.getOrElse { PatchImageRequest() }

                try {
                    val image = imageService.patchImage(userId, imageId, body.name, body.tags)
                    call.respond(imageService.toPublicImage(call, image))
                } catch (ex: AppException) {
                    call.respondException(ex)
                }
            }

            delete("/{id}") {
                val userId = call.authenticatedUserId()
                val imageId = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image id"))

                try {
                    imageService.deleteImage(userId, imageId)
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (ex: AppException) {
                    call.respondException(ex)
                }
            }
        }
    }
}
