package com.weekplanner.plans.models

import com.weekplanner.images.models.PublicImage
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Full plan-slot record as stored/retrieved from the database. */
data class PlanSlotRecord(
    val id: UUID,
    val userId: UUID,
    val weekStart: LocalDate,
    val dayIndex: Int,
    val slotIndex: Int,
    val imageId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Public plan-slot representation returned to clients. */
@Serializable
data class PublicPlanSlot(
    val id: String,
    val weekStart: String,
    val dayIndex: Int,
    val slotIndex: Int,
    val imageId: String,
    val image: PublicImage?,
    val createdAt: String,
    val updatedAt: String,
)

/** Response wrapper for the weekly plan. */
@Serializable
data class WeekPlanResponse(
    val weekStart: String,
    val slots: List<PublicPlanSlot>,
)

/** Request body for upserting a plan slot. */
@Serializable
data class UpsertSlotRequest(
    val weekStart: String,
    val dayIndex: Int,
    val slotIndex: Int,
    val imageId: String,
)
