package dev.libinfaby.tasks.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Task dates are local YYYY-MM-DD strings; reminders are UTC ISO instants (same as the web app).

object Dates {
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val shortDateYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
    private val weekdayDate = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    private val fullDate = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    fun today(): LocalDate = LocalDate.now()

    fun parseDate(value: String?): LocalDate? = value?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun parseInstant(value: String?): Instant? = value?.let {
        runCatching { Instant.parse(it) }.getOrNull()
            // Older rows hold a bare date or local datetime
            ?: runCatching { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
            ?: parseDate(it)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()
    }

    fun label(date: LocalDate): String {
        val today = today()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (date.year == today.year) shortDate.format(date) else shortDateYear.format(date)
        }
    }

    fun formatDate(value: String?): String = parseDate(value)?.let(::label) ?: ""

    fun formatReminder(value: String?): String {
        val instant = parseInstant(value) ?: return ""
        val local = instant.atZone(ZoneId.systemDefault())
        return "${label(local.toLocalDate())} at ${time.format(local)}"
    }

    fun formatTime(instant: Instant): String = time.format(instant.atZone(ZoneId.systemDefault()))

    fun longLabel(date: LocalDate): String = longDate.format(date)

    /** Upcoming section titles: "Tomorrow", then "Wednesday, Oct 7". */
    fun dayLabel(date: LocalDate): String = if (date == today().plusDays(1)) "Tomorrow" else weekdayDate.format(date)

    /** The Today header: "Sunday, October 4". */
    fun fullLabel(date: LocalDate): String = fullDate.format(date)

    fun isOverdue(value: String?): Boolean = parseDate(value)?.isBefore(today()) == true
    fun isToday(value: String?): Boolean = parseDate(value) == today()
}
