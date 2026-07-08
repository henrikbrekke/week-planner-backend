package com.weekplanner.auth

import com.weekplanner.testApp
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class AuthRoutesTest : FreeSpec({

    "register, login and fetch current user" {
        testApp { _ ->
            val client = createClient { followRedirects = false }

            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Test User","email":"user@example.com","password":"secret123"}""")
            }
            registerResponse.status shouldBe HttpStatusCode.Created
            val registerBody = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
            registerBody["user"]!!.jsonObject["email"]!!.jsonPrimitive.content shouldBe "user@example.com"
            registerBody["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content shouldNotBe null

            val loginResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"user@example.com","password":"secret123"}""")
            }
            loginResponse.status shouldBe HttpStatusCode.OK
            val loginToken = Json.parseToJsonElement(loginResponse.bodyAsText())
                .jsonObject["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content

            val meResponse = client.get("/api/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $loginToken")
            }
            meResponse.status shouldBe HttpStatusCode.OK
            val meBody = Json.parseToJsonElement(meResponse.bodyAsText()).jsonObject
            meBody["name"]!!.jsonPrimitive.content shouldBe "Test User"
        }
    }

    "register with duplicate email returns 409" {
        testApp { _ ->
            val client = createClient {}
            val body = """{"name":"A","email":"dup@example.com","password":"pw1"}"""
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.status shouldBe HttpStatusCode.Created
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.status shouldBe HttpStatusCode.Conflict
        }
    }

    "login with wrong password returns 401" {
        testApp { _ ->
            val client = createClient {}
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"A","email":"a@b.com","password":"right"}""")
            }
            client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"a@b.com","password":"wrong"}""")
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "accessing /me without token returns 401" {
        testApp { _ ->
            createClient {}.get("/api/auth/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "logout clears session" {
        testApp { _ ->
            val client = createClient {}
            val reg = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"User","email":"logout@test.com","password":"pass"}""")
            }
            val token = Json.parseToJsonElement(reg.bodyAsText())
                .jsonObject["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
            client.post("/api/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.status shouldBe HttpStatusCode.OK
        }
    }
})
