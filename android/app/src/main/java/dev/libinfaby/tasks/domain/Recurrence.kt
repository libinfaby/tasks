package dev.libinfaby.tasks.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Recurring reminders. Mirrors worker/src/utils/recurrence.ts — keep the two (and their tests) in sync,
 * so the phone and the server always land on the same next time.
 */
enum class RepeatRule(val wire: String, val label: String) {
    DAILY("daily", "Daily"),
    WEEKDAYS("weekdays", "Weekdays"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly");

    companion object {
        fun fromWire(value: String?): RepeatRule? = entries.firstOrNull { it.wire == value }
    }
}

private val ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Same format the web app sends: UTC with milliseconds, e.g. 2026-10-05T03:30:00.000Z. */
fun Instant.toWireIso(): String = ISO_MILLIS.format(this)

private fun LocalDateTime.step(rule: RepeatRule): LocalDateTime = when (rule) {
    RepeatRule.DAILY -> plusDays(1)
    RepeatRule.WEEKLY -> plusWeeks(1)
    RepeatRule.WEEKDAYS -> {
        var next = plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) next = next.plusDays(1)
        next
    }
    // plusMonths clamps to the month's last day (Jan 31 → Feb 28); later steps start from the clamped day.
    RepeatRule.MONTHLY -> plusMonths(1)
}

/** Next occurrence strictly after [now], stepping in [zone]'s wall-clock time. Missed occurrences are skipped. */
fun nextOccurrence(reminderIso: String, rule: RepeatRule, zone: ZoneId, now: Instant = Instant.now()): String {
    var local = Instant.parse(reminderIso).atZone(zone).toLocalDateTime()
    var instant: Instant
    do {
        local = local.step(rule)
        instant = local.atZone(zone).toInstant()
    } while (!instant.isAfter(now))
    return instant.toWireIso()
}
