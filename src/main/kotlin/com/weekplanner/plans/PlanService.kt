package com.weekplanner.plans

import com.weekplanner.common.AppException
import com.weekplanner.images.ImageRepository
import com.weekplanner.images.ImageService
import com.weekplanner.plans.models.PlanSlotRecord
import com.weekplanner.plans.models.PublicPlanSlot
import com.weekplanner.plans.models.WeekPlanResponse
import io.ktor.server.application.*
import java.time.LocalDate
import java.util.UUID

class PlanService(
    private val planRepository: PlanRepository,
    private val imageRepository: ImageRepository,
    private val imageService: ImageService,
) {
    fun getWeekPlan(call: ApplicationCall, userId: UUID, weekStart: LocalDate): WeekPlanResponse {
        val slots = planRepository.findByUserAndWeek(userId, weekStart)
        val publicSlots = slots.map { toPublicSlot(call, it) }
        return WeekPlanResponse(weekStart.toString(), publicSlots)
    }

    fun upsertSlot(
        call: ApplicationCall,
        userId: UUID,
        weekStart: LocalDate,
        dayIndex: Int,
        slotIndex: Int,
        imageId: UUID,
    ): Pair<PublicPlanSlot, Boolean> {
        imageRepository.findByIdAndUser(imageId, userId)
            ?: throw AppException.NotFound("Image not found")

        val (slot, created) = planRepository.upsert(userId, weekStart, dayIndex, slotIndex, imageId)
        return Pair(toPublicSlot(call, slot), created)
    }

    fun deleteSlot(userId: UUID, slotId: UUID) {
        val deleted = planRepository.delete(slotId, userId)
        if (!deleted) throw AppException.NotFound("Plan slot not found")
    }

    private fun toPublicSlot(call: ApplicationCall, slot: PlanSlotRecord): PublicPlanSlot {
        val image = imageRepository.findByIdAndUser(slot.imageId, slot.userId)
            ?.let { imageService.toPublicImage(call, it) }
        return PublicPlanSlot(
            id = slot.id.toString(),
            weekStart = slot.weekStart.toString(),
            dayIndex = slot.dayIndex,
            slotIndex = slot.slotIndex,
            imageId = slot.imageId.toString(),
            image = image,
            createdAt = slot.createdAt.toString(),
            updatedAt = slot.updatedAt.toString(),
        )
    }
}
