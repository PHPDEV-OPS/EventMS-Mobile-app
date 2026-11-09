package com.eventms.mobile.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * The backend serialises java.time.LocalDateTime with no zone and a variable
 * number of fractional-second digits ("2026-09-13T11:13:48.961687"), which
 * ISO_LOCAL_DATE_TIME handles, but it also emits values with no fraction at all.
 */
object DateTimes {

    fun parse(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: DateTimeParseException) {
            // Some payloads carry an offset; fall back to trimming it off.
            runCatching {
                LocalDateTime.parse(raw.substringBefore('+').substringBefore('Z').trim())
            }.getOrNull()
        }
    }

    /** Format the backend expects when a date is sent back. */
    fun toApi(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val fullFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

    fun day(value: LocalDateTime?): String = value?.format(dayFormat) ?: "Date to be announced"
    fun time(value: LocalDateTime?): String = value?.format(timeFormat) ?: ""
    fun short(value: LocalDateTime?): String = value?.format(shortFormat) ?: "—"
    fun full(value: LocalDateTime?): String = value?.format(fullFormat) ?: "—"

    /** "in 3 days" / "2 weeks ago", for event cards. */
    fun relative(value: LocalDateTime?): String {
        if (value == null) return ""
        val now = LocalDateTime.now()
        val days = java.time.Duration.between(now, value).toDays()
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days in 2..6 -> "In $days days"
            days in -6..-2 -> "${-days} days ago"
            days >= 7 -> "In ${days / 7} week${if (days / 7 == 1L) "" else "s"}"
            else -> "${-days / 7} week${if (-days / 7 == 1L) "" else "s"} ago"
        }
    }
}

object Money {
    /** The backend charges through Pesapal in Kenyan shillings. */
    fun format(amount: Double): String = when {
        amount <= 0.0 -> "Free"
        else -> "KSh " + String.format(Locale.getDefault(), "%,.0f", amount)
    }

    fun formatExact(amount: Double): String =
        "KSh " + String.format(Locale.getDefault(), "%,.2f", amount)
}
