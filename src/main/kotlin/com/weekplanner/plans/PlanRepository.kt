package com.weekplanner.plans

import com.weekplanner.plans.models.PlanSlotRecord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ---------------------------------------------------------------------------
// Exposed table definition
// ---------------------------------------------------------------------------

object PlanSlotsTable : Table("plan_slots") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id")
    val weekStart = date("week_start")
    val dayIndex = short("day_index")
    val slotIndex = short("slot_index")
    val imageId = uuid("image_id")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

class PlanRepository {

    fun findByUserAndWeek(userId: UUID, weekStart: LocalDate): List<PlanSlotRecord> = transaction {
        PlanSlotsTable
            .select { (PlanSlotsTable.userId eq userId) and (PlanSlotsTable.weekStart eq weekStart) }
            .orderBy(PlanSlotsTable.dayIndex to SortOrder.ASC, PlanSlotsTable.slotIndex to SortOrder.ASC)
            .map { it.toPlanSlotRecord() }
    }

    fun findByIdAndUser(id: UUID, userId: UUID): PlanSlotRecord? = transaction {
        PlanSlotsTable
            .select { (PlanSlotsTable.id eq id) and (PlanSlotsTable.userId eq userId) }
            .singleOrNull()
            ?.toPlanSlotRecord()
    }

    fun findExistingSlot(userId: UUID, weekStart: LocalDate, dayIndex: Int, slotIndex: Int): PlanSlotRecord? =
        transaction {
            PlanSlotsTable
                .select {
                    (PlanSlotsTable.userId eq userId) and
                            (PlanSlotsTable.weekStart eq weekStart) and
                            (PlanSlotsTable.dayIndex eq dayIndex.toShort()) and
                            (PlanSlotsTable.slotIndex eq slotIndex.toShort())
                }
                .singleOrNull()
                ?.toPlanSlotRecord()
        }

    fun upsert(
        userId: UUID,
        weekStart: LocalDate,
        dayIndex: Int,
        slotIndex: Int,
        imageId: UUID,
    ): Pair<PlanSlotRecord, Boolean> = transaction {
        val existing = findExistingSlot(userId, weekStart, dayIndex, slotIndex)
        if (existing != null) {
            val now = Instant.now()
            PlanSlotsTable.update({
                (PlanSlotsTable.userId eq userId) and
                        (PlanSlotsTable.weekStart eq weekStart) and
                        (PlanSlotsTable.dayIndex eq dayIndex.toShort()) and
                        (PlanSlotsTable.slotIndex eq slotIndex.toShort())
            }) {
                it[PlanSlotsTable.imageId] = imageId
                it[PlanSlotsTable.updatedAt] = now
            }
            val updated = findByIdAndUser(existing.id, userId)!!
            Pair(updated, false)
        } else {
            val newId = UUID.randomUUID()
            val now = Instant.now()
            PlanSlotsTable.insert {
                it[PlanSlotsTable.id] = newId
                it[PlanSlotsTable.userId] = userId
                it[PlanSlotsTable.weekStart] = weekStart
                it[PlanSlotsTable.dayIndex] = dayIndex.toShort()
                it[PlanSlotsTable.slotIndex] = slotIndex.toShort()
                it[PlanSlotsTable.imageId] = imageId
                it[PlanSlotsTable.createdAt] = now
                it[PlanSlotsTable.updatedAt] = now
            }
            val inserted = findByIdAndUser(newId, userId)!!
            Pair(inserted, true)
        }
    }

    fun delete(id: UUID, userId: UUID): Boolean = transaction {
        val deleted = PlanSlotsTable.deleteWhere {
            (PlanSlotsTable.id eq id) and (PlanSlotsTable.userId eq userId)
        }
        deleted > 0
    }

    // ---------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------

    private fun ResultRow.toPlanSlotRecord() = PlanSlotRecord(
        id = this[PlanSlotsTable.id],
        userId = this[PlanSlotsTable.userId],
        weekStart = this[PlanSlotsTable.weekStart],
        dayIndex = this[PlanSlotsTable.dayIndex].toInt(),
        slotIndex = this[PlanSlotsTable.slotIndex].toInt(),
        imageId = this[PlanSlotsTable.imageId],
        createdAt = this[PlanSlotsTable.createdAt],
        updatedAt = this[PlanSlotsTable.updatedAt],
    )
}
