package dev.libinfaby.tasks

import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.domain.nextOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Same cases as worker/test/recurrence.test.ts. */
class RecurrenceTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val ny = ZoneId.of("America/New_York")

    private fun next(from: String, rule: RepeatRule, zone: ZoneId = ist, now: String = from) =
        nextOccurrence(from, rule, zone, Instant.parse(now))

    @Test fun dailyKeepsLocalWallClockTime() =
        assertEquals("2026-10-06T03:30:00.000Z", next("2026-10-05T03:30:00.000Z", RepeatRule.DAILY))

    @Test fun weekdaysSkipsTheWeekend() =
        assertEquals("2026-10-12T03:30:00.000Z", next("2026-10-09T03:30:00.000Z", RepeatRule.WEEKDAYS))

    @Test fun weekdaysFromAWeekendLandsOnMonday() =
        assertEquals("2026-10-12T03:30:00.000Z", next("2026-10-10T03:30:00.000Z", RepeatRule.WEEKDAYS))

    @Test fun weeklyAddsSevenDays() =
        assertEquals("2026-10-12T03:30:00.000Z", next("2026-10-05T03:30:00.000Z", RepeatRule.WEEKLY))

    @Test fun monthlyClampsJan31ToEndOfFebruary() =
        assertEquals("2027-02-28T03:30:00.000Z", next("2027-01-31T03:30:00.000Z", RepeatRule.MONTHLY))

    @Test fun monthlyRollsOverTheYear() =
        assertEquals("2027-01-15T03:30:00.000Z", next("2026-12-15T03:30:00.000Z", RepeatRule.MONTHLY))

    @Test fun missedOccurrencesAreSkipped() =
        assertEquals("2026-10-05T03:30:00.000Z", next("2026-09-24T03:30:00.000Z", RepeatRule.DAILY, now = "2026-10-04T10:00:00.000Z"))

    @Test fun localTimeSurvivesDstChange() =
        assertEquals("2026-11-01T14:00:00.000Z", next("2026-10-31T13:00:00.000Z", RepeatRule.DAILY, zone = ny))

    @Test fun onlyKnownRulesParse() {
        assertEquals(RepeatRule.DAILY, RepeatRule.fromWire("daily"))
        assertNull(RepeatRule.fromWire("yearly"))
        assertNull(RepeatRule.fromWire(null))
    }
}
