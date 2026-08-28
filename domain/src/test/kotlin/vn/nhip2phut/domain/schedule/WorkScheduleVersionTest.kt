package vn.nhip2phut.domain.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import vn.nhip2phut.domain.model.LocalStamp

class WorkScheduleVersionTest {
    @Test
    fun acceptsCanonicalWeekdaysWindowAndOneOrTwoSortedDistinctReminders() {
        val oneReminder = schedule(reminders = listOf(time("09:00")))
        val twoReminders = schedule(reminders = listOf(time("09:00"), time("14:30")))

        assertEquals(setOf(1, 3, 5), oneReminder.selectedWeekdays)
        assertEquals(listOf("09:00"), oneReminder.reminderTimes.map(ScheduleTime::wire))
        assertEquals(listOf("09:00", "14:30"), twoReminders.reminderTimes.map(ScheduleTime::wire))
    }

    @Test
    fun rejectsEmptyOrOutOfRangeWeekdays() {
        assertFailsWith<IllegalArgumentException> { schedule(weekdays = emptySet()) }
        assertFailsWith<IllegalArgumentException> { schedule(weekdays = setOf(0, 1)) }
        assertFailsWith<IllegalArgumentException> { schedule(weekdays = setOf(1, 8)) }
    }

    @Test
    fun rejectsOvernightOrEmptyWindow() {
        assertFailsWith<IllegalArgumentException> {
            schedule(start = time("17:00"), end = time("09:00"))
        }
        assertFailsWith<IllegalArgumentException> {
            schedule(start = time("09:00"), end = time("09:00"))
        }
    }

    @Test
    fun rejectsMissingExcessDuplicateUnsortedOrOutsideReminders() {
        assertFailsWith<IllegalArgumentException> { schedule(reminders = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            schedule(reminders = listOf(time("09:00"), time("12:00"), time("15:00")))
        }
        assertFailsWith<IllegalArgumentException> {
            schedule(reminders = listOf(time("09:00"), time("09:00")))
        }
        assertFailsWith<IllegalArgumentException> {
            schedule(reminders = listOf(time("14:30"), time("09:00")))
        }
        assertFailsWith<IllegalArgumentException> { schedule(reminders = listOf(time("08:59"))) }
        assertFailsWith<IllegalArgumentException> { schedule(reminders = listOf(time("17:00"))) }
    }

    @Test
    fun rejectsNonCanonicalTimeAliasesAtBoundary() {
        listOf("9:00", "09:00:00", " 09:00", "09:00 ", "24:00", "０９:００").forEach { raw ->
            assertEquals(null, ScheduleTime.parse(raw), raw)
        }
    }

    @Test
    fun rejectsReplacementBeforeEffectiveTime() {
        assertFailsWith<IllegalArgumentException> {
            schedule(replacedAt = stamp("2026-08-27T01:59:59.999Z"))
        }
    }

    private fun schedule(
        weekdays: Set<Int> = setOf(1, 3, 5),
        start: ScheduleTime = time("09:00"),
        end: ScheduleTime = time("17:00"),
        reminders: List<ScheduleTime> = listOf(time("09:00"), time("14:30")),
        replacedAt: LocalStamp? = null,
    ): WorkScheduleVersion = WorkScheduleVersion(
        id = UUID.fromString("00000000-0000-4000-8000-000000000001"),
        enabled = true,
        selectedWeekdays = weekdays,
        workStart = start,
        workEnd = end,
        reminderTimes = reminders,
        effectiveFrom = stamp("2026-08-27T02:00:00.000Z"),
        replacedAt = replacedAt,
    )

    private fun time(raw: String): ScheduleTime = requireNotNull(ScheduleTime.parse(raw))

    private fun stamp(raw: String): LocalStamp {
        val instant = Instant.parse(raw)
        val zone = ZoneId.of("Asia/Bangkok")
        return LocalStamp(
            instant = instant,
            localDate = LocalDate.ofInstant(instant, zone),
            zoneId = zone,
            utcOffsetMinutes = zone.rules.getOffset(instant).totalSeconds / 60,
        )
    }
}
