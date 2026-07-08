package com.weekplanner.images

import com.weekplanner.testApp
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class ImageRoutesTest : FreeSpec({

    suspend fun registerAndGetToken(client: HttpClient, suffix: String = ""): String {
        val reg = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"User$suffix","email":"img$suffix@test.com","password":"pass"}""")
        }
        return Json.parseToJsonElement(reg.bodyAsText())
            .jsonObject["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
    }

    "upload image, list with tag filter, patch, then delete" {
        testApp { _ ->
            val client = createClient {}
            val token = registerAndGetToken(client, "upload")

            val uploadResponse = client.post("/api/images") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("name", "School")
                            append("tags[]", "weekday")
                            append("tags[]", "routine")
                            append("image", "fake-image-bytes".toByteArray(), Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"school.png\"")
                            })
                        },
                    ),
                )
            }
            uploadResponse.status shouldBe HttpStatusCode.Created
            val uploaded = Json.parseToJsonElement(uploadResponse.bodyAsText()).jsonObject
            uploaded["name"]!!.jsonPrimitive.content shouldBe "School"
            val tags = uploaded["tags"]!!.jsonArray.map { it.jsonPrimitive.content }
            tags shouldBe listOf("weekday", "routine")
            val imageId = uploaded["id"]!!.jsonPrimitive.content

            val listResponse = client.get("/api/images?tags=weekday") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            listResponse.status shouldBe HttpStatusCode.OK
            Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray.size shouldBe 1

            val noMatchResponse = client.get("/api/images?tags=nonexistent") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            Json.parseToJsonElement(noMatchResponse.bodyAsText()).jsonArray.size shouldBe 0

            val patchResponse = client.patch("/api/images/${imageId}") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"School Updated","tags":["weekday","updated"]}""")
            }
            patchResponse.status shouldBe HttpStatusCode.OK
            Json.parseToJsonElement(patchResponse.bodyAsText())
                .jsonObject["name"]!!.jsonPrimitive.content shouldBe "School Updated"

            val deleteResponse = client.delete("/api/images/${imageId}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            deleteResponse.status shouldBe HttpStatusCode.OK

            val afterDelete = client.get("/api/images") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            Json.parseToJsonElement(afterDelete.bodyAsText()).jsonArray.size shouldBe 0
        }
    }

    "image endpoint requires authentication" {
        testApp { _ ->
            createClient {}.get("/api/images").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "upload without image field returns 400" {
        testApp { _ ->
            val client = createClient {}
            val token = registerAndGetToken(client, "noimg")
            val response = client.post("/api/images") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(MultiPartFormDataContent(formData { append("name", "Test") }))
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
