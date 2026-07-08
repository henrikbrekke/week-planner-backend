package com.weekplanner.common

import io.ktor.server.application.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

/** Normalises an email address: trim + lowercase. */
fun String.normalizeEmail(): String = trim().lowercase()

/** Returns the ISO date string of the Monday that starts the current UTC week. */
fun currentWeekStart(): String {
    val today = LocalDate.now()
    return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
}

/**
 * Parses and validates a week-start string.
 * Returns a [LocalDate] if the string is a valid `YYYY-MM-DD` that can be parsed,
 * or `null` if the format is invalid.
 */
fun parseWeekStart(value: String?): LocalDate? {
    if (value == null || !value.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return null
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Normalises a tag list: trim, deduplicate, drop blanks.
 */
fun normalizeTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Resolves the absolute URL for a given path on the current request. */
fun ApplicationCall.absoluteUrl(path: String): String {
    val proto = request.headers["X-Forwarded-Proto"] ?: "http"
    val host = request.headers["Host"] ?: "localhost"
    return "$proto://$host$path"
}
