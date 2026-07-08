package com.weekplanner.plans

import com.weekplanner.common.AppException
import com.weekplanner.common.ErrorResponse
import com.weekplanner.common.currentWeekStart
import com.weekplanner.common.parseWeekStart
import com.weekplanner.common.respondException
import com.weekplanner.plans.models.UpsertSlotRequest
import com.weekplanner.plugins.JWT_AUTH
import com.weekplanner.plugins.authenticatedUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.planRoutes(planService: PlanService) {

    authenticate(JWT_AUTH) {
        route("/api/plans") {

            get {
                val userId = call.authenticatedUserId()
                val weekStartParam = call.request.queryParameters["weekStart"] ?: currentWeekStart()
                val weekStart = parseWeekStart(weekStartParam)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("weekStart must be YYYY-MM-DD"))

                try {
                    val plan = planService.getWeekPlan(call, userId, weekStart)
                    call.respond(plan)
                } catch (ex: AppException) {
                    call.respondException(ex)
                }
            }

            route("/slot") {

                put {
                    val userId = call.authenticatedUserId()
                    val body = runCatching { call.receive<UpsertSlotRequest>() }.getOrNull()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("weekStart, dayIndex, slotIndex and imageId are required"),
                        )

                    val weekStart = parseWeekStart(body.weekStart)
                    val dayIndex = body.dayIndex
                    val slotIndex = body.slotIndex
                    val imageId = runCatching { UUID.fromString(body.imageId) }.getOrNull()

                    if (weekStart == null || dayIndex < 0 || dayIndex > 6 || slotIndex < 0 || imageId == null) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("weekStart, dayIndex, slotIndex and imageId are required"),
                        )
                    }

                    try {
                        val (slot, created) = planService.upsertSlot(call, userId, weekStart, dayIndex, slotIndex, imageId)
                        val status = if (created) HttpStatusCode.Created else HttpStatusCode.OK
                        call.respond(status, slot)
                    } catch (ex: AppException) {
                        call.respondException(ex)
                    }
                }

                delete("/{id}") {
                    val userId = call.authenticatedUserId()
                    val slotId = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid slot id"))

                    try {
                        planService.deleteSlot(userId, slotId)
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } catch (ex: AppException) {
                        call.respondException(ex)
                    }
                }
            }
        }
    }
}
