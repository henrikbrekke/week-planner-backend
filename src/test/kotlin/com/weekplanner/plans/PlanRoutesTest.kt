package com.weekplanner.plans

import com.weekplanner.testApp
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class PlanRoutesTest : FreeSpec({

    suspend fun registerAndGetToken(client: HttpClient, suffix: String = ""): String {
        val reg = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Planner$suffix","email":"plan$suffix@test.com","password":"pass"}""")
        }
        return Json.parseToJsonElement(reg.bodyAsText())
            .jsonObject["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
    }

    suspend fun uploadImage(client: HttpClient, token: String): String {
        val response = client.post("/api/images") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("name", "School")
                        append("tags[]", "weekday")
                        append("image", "fake".toByteArray(), Headers.build {
                            append(HttpHeaders.ContentType, "image/png")
                            append(HttpHeaders.ContentDisposition, "filename=\"school.png\"")
                        })
                    },
                ),
            )
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
    }

    "create, read and delete a plan slot" {
        testApp { _ ->
            val client = createClient {}
            val token = registerAndGetToken(client, "crud")
            val imageId = uploadImage(client, token)

            val slotResponse = client.put("/api/plans/slot") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"weekStart":"2026-07-06","dayIndex":1,"slotIndex":0,"imageId":"${imageId}"}""")
            }
            slotResponse.status shouldBe HttpStatusCode.Created
            val slot = Json.parseToJsonElement(slotResponse.bodyAsText()).jsonObject
            slot["imageId"]!!.jsonPrimitive.content shouldBe imageId
            val slotId = slot["id"]!!.jsonPrimitive.content

            val planResponse = client.get("/api/plans?weekStart=2026-07-06") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            planResponse.status shouldBe HttpStatusCode.OK
            val plan = Json.parseToJsonElement(planResponse.bodyAsText()).jsonObject
            plan["weekStart"]!!.jsonPrimitive.content shouldBe "2026-07-06"
            plan["slots"]!!.jsonArray.size shouldBe 1
            plan["slots"]!!.jsonArray[0].jsonObject["image"]!!.jsonObject["id"]!!.jsonPrimitive.content shouldBe imageId

            val updateResponse = client.put("/api/plans/slot") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"weekStart":"2026-07-06","dayIndex":1,"slotIndex":0,"imageId":"${imageId}"}""")
            }
            updateResponse.status shouldBe HttpStatusCode.OK

            val deleteResponse = client.delete("/api/plans/slot/${slotId}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            deleteResponse.status shouldBe HttpStatusCode.OK

            val emptyPlan = client.get("/api/plans?weekStart=2026-07-06") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            Json.parseToJsonElement(emptyPlan.bodyAsText())
                .jsonObject["slots"]!!.jsonArray.size shouldBe 0
        }
    }

    "invalid weekStart returns 400" {
        testApp { _ ->
            val client = createClient {}
            val token = registerAndGetToken(client, "badweek")
            client.get("/api/plans?weekStart=not-a-date") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    "deleting a non-existent slot returns 404" {
        testApp { _ ->
            val client = createClient {}
            val token = registerAndGetToken(client, "noslot")
            val fakeId = "00000000-0000-0000-0000-000000000000"
            client.delete("/api/plans/slot/${fakeId}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.status shouldBe HttpStatusCode.NotFound
        }
    }
})
