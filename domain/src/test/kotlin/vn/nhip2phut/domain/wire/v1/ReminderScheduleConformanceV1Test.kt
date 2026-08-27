package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ReminderScheduleConformanceV1Test {
    @Test
    fun ordinaryAndDstResolvedFixedOccurrencesPass() {
        requireValid(
            schedule(
                scheduleId = SCHEDULE_ORDINARY,
                selectedWeekdays = "[4]",
                reminderTimes = "[\"10:00\"]",
                effectiveFrom = stamp("2026-08-01", "00:00:00.000Z", "UTC", 0),
            ),
            fixed(SCHEDULE_ORDINARY, 0, "2026-08-27", stamp("2026-08-27", "10:00:00.000Z", "UTC", 0)),
        )
        requireValid(
            schedule(
                scheduleId = SCHEDULE_GAP,
                selectedWeekdays = "[7]",
                reminderTimes = "[\"02:30\"]",
                effectiveFrom = stamp("2026-01-01", "05:00:00.000Z", NEW_YORK, -300),
            ),
            fixed(SCHEDULE_GAP, 0, "2026-03-08", stamp("2026-03-08", "07:00:00.000Z", NEW_YORK, -240)),
        )
        requireValid(
            schedule(
                scheduleId = SCHEDULE_OVERLAP,
                selectedWeekdays = "[7]",
                reminderTimes = "[\"01:30\"]",
                effectiveFrom = stamp("2026-01-01", "05:00:00.000Z", NEW_YORK, -300),
            ),
            fixed(SCHEDULE_OVERLAP, 0, "2026-11-01", stamp("2026-11-01", "05:30:00.000Z", NEW_YORK, -240)),
        )
    }

    @Test
    fun fixedOccurrenceRequiresEnabledReferencedScheduleAndValidSlot() {
        assertFailure("referenced enabled schedule") {
            ReminderScheduleConformanceV1.requireValid(
                emptyMap(),
                listOf(fixed(SCHEDULE_ORDINARY, 0, "2026-08-27", stamp("2026-08-27", "10:00:00.000Z", "UTC", 0))),
            )
        }
        assertFailure("enabled schedule") {
            requireValid(
                schedule(SCHEDULE_ORDINARY, "[4]", "[\"10:00\"]", stamp("2026-08-01", "00:00:00.000Z", "UTC", 0), enabled = false),
                fixed(SCHEDULE_ORDINARY, 0, "2026-08-27", stamp("2026-08-27", "10:00:00.000Z", "UTC", 0)),
            )
        }
        assertFailure("slot_index") {
            requireValid(
                schedule(SCHEDULE_ORDINARY, "[4]", "[\"10:00\"]", stamp("2026-08-01", "00:00:00.000Z", "UTC", 0)),
                fixed(SCHEDULE_ORDINARY, 1, "2026-08-27", stamp("2026-08-27", "10:00:00.000Z", "UTC", 0)),
            )
        }
    }

    @Test
    fun fixedOccurrenceRequiresSelectedDateOnOrAfterScheduleEffectiveDate() {
        val schedule = schedule(
            SCHEDULE_ORDINARY,
            "[3,4]",
            "[\"10:00\"]",
            stamp("2026-08-27", "00:00:00.000Z", "UTC", 0),
        )
        assertFailure("selected weekday") {
            requireValid(schedule, fixed(SCHEDULE_ORDINARY, 0, "2026-08-28", stamp("2026-08-28", "10:00:00.000Z", "UTC", 0)))
        }
        assertFailure("effective_from") {
            requireValid(schedule, fixed(SCHEDULE_ORDINARY, 0, "2026-08-26", stamp("2026-08-26", "10:00:00.000Z", "UTC", 0)))
        }
        val effectiveAfterSameDaySlot = schedule(
            SCHEDULE_ORDINARY,
            "[4]",
            "[\"10:00\"]",
            stamp("2026-08-27", "12:00:00.000Z", "UTC", 0),
        )
        assertFailure("effective_from") {
            requireValid(
                effectiveAfterSameDaySlot,
                fixed(SCHEDULE_ORDINARY, 0, "2026-08-27", stamp("2026-08-27", "10:00:00.000Z", "UTC", 0)),
            )
        }
    }

    @Test
    fun fixedDueStampMustMatchLogicalDateAndIndexedWallTime() {
        val schedule = schedule(
            SCHEDULE_ORDINARY,
            "[4,5]",
            "[\"10:00\"]",
            stamp("2026-08-01", "00:00:00.000Z", "UTC", 0),
        )
        assertFailure("local_date") {
            requireValid(schedule, fixed(SCHEDULE_ORDINARY, 0, "2026-08-27", stamp("2026-08-28", "10:00:00.000Z", "UTC", 0)))
        }
        assertFailure("indexed reminder time") {
            requireValid(schedule, fixed(SCHEDULE_ORDINARY, 0, "2026-08-27", stamp("2026-08-27", "10:01:00.000Z", "UTC", 0)))
        }
    }

    @Test
    fun gapMustUseFirstValidTimeAndOverlapMustUseEarlierOffset() {
        val gapSchedule = schedule(
            SCHEDULE_GAP,
            "[7]",
            "[\"02:30\"]",
            stamp("2026-01-01", "05:00:00.000Z", NEW_YORK, -300),
        )
        assertFailure("indexed reminder time") {
            requireValid(gapSchedule, fixed(SCHEDULE_GAP, 0, "2026-03-08", stamp("2026-03-08", "07:01:00.000Z", NEW_YORK, -240)))
        }

        val overlapSchedule = schedule(
            SCHEDULE_OVERLAP,
            "[7]",
            "[\"01:30\"]",
            stamp("2026-01-01", "05:00:00.000Z", NEW_YORK, -300),
        )
        assertFailure("earlier overlap offset") {
            requireValid(overlapSchedule, fixed(SCHEDULE_OVERLAP, 0, "2026-11-01", stamp("2026-11-01", "06:30:00.000Z", NEW_YORK, -300)))
        }
    }

    @Test
    fun gapResolutionMustRemainInsideTheHalfOpenWorkWindow() {
        val schedule = schedule(
            scheduleId = SCHEDULE_GAP,
            selectedWeekdays = "[7]",
            reminderTimes = "[\"02:30\"]",
            effectiveFrom = stamp("2026-01-01", "05:00:00.000Z", NEW_YORK, -300),
            workStart = "00:00",
            workEnd = "03:00",
        )
        assertFailure("work window") {
            requireValid(
                schedule,
                fixed(SCHEDULE_GAP, 0, "2026-03-08", stamp("2026-03-08", "07:00:00.000Z", NEW_YORK, -240)),
            )
        }
    }

    @Test
    fun fullCivilDayGapUsesTheResolvedLocalDate() {
        requireValid(
            schedule(
                scheduleId = SCHEDULE_GAP,
                selectedWeekdays = "[5]",
                reminderTimes = "[\"12:00\"]",
                effectiveFrom = stamp("2011-01-01", "00:00:00.000Z", "UTC", 0),
            ),
            fixed(
                SCHEDULE_GAP,
                0,
                "2011-12-30",
                stampAt("2011-12-30T10:00:00.000Z", "2011-12-31", APIA, 840),
            ),
        )
    }

    @Test
    fun pendingRowsRequireEnabledUnreplacedScheduleButHistoricalTerminalRowsRemainValid() {
        val active = schedule(
            SCHEDULE_ORDINARY,
            "[4]",
            "[\"10:00\"]",
            stamp("2026-08-01", "00:00:00.000Z", "UTC", 0),
        )
        ReminderScheduleConformanceV1.requireValid(mapOf(SCHEDULE_ORDINARY to active), listOf(snooze(SCHEDULE_ORDINARY)))
        assertFailure("enabled schedule") {
            val disabled = schedule(
                SCHEDULE_ORDINARY,
                "[4]",
                "[\"10:00\"]",
                stamp("2026-08-01", "00:00:00.000Z", "UTC", 0),
                enabled = false,
            )
            ReminderScheduleConformanceV1.requireValid(mapOf(SCHEDULE_ORDINARY to disabled), listOf(snooze(SCHEDULE_ORDINARY)))
        }
        assertFailure("enabled schedule") {
            val disabled = schedule(
                SCHEDULE_ORDINARY,
                "[4]",
                "[\"10:00\"]",
                stamp("2026-08-01", "00:00:00.000Z", "UTC", 0),
                enabled = false,
            )
            ReminderScheduleConformanceV1.requireValid(
                mapOf(SCHEDULE_ORDINARY to disabled),
                listOf(snooze(SCHEDULE_ORDINARY, status = "CANCELLED")),
            )
        }
        val replaced = schedule(
            SCHEDULE_ORDINARY,
            "[4]",
            "[\"10:00\"]",
            stamp("2026-08-01", "00:00:00.000Z", "UTC", 0),
            replacedAt = stamp("2026-08-27", "11:00:00.000Z", "UTC", 0),
        )
        assertFailure("enabled schedule") {
            ReminderScheduleConformanceV1.requireValid(mapOf(SCHEDULE_ORDINARY to replaced), listOf(snooze(SCHEDULE_ORDINARY)))
        }
        ReminderScheduleConformanceV1.requireValid(
            mapOf(SCHEDULE_ORDINARY to replaced),
            listOf(snooze(SCHEDULE_ORDINARY, status = "CANCELLED")),
        )
    }

    private fun requireValid(schedule: WorkScheduleWireV1, reminder: ReminderWireV1) {
        ReminderScheduleConformanceV1.requireValid(mapOf(schedule.scheduleVersionId.value to schedule), listOf(reminder))
    }

    private fun assertFailure(message: String, block: () -> Unit) {
        val failure = assertFailsWith<WireContractException>(block = block)
        assertContains(failure.message.orEmpty(), message)
    }

    private fun schedule(
        scheduleId: String,
        selectedWeekdays: String,
        reminderTimes: String,
        effectiveFrom: String,
        enabled: Boolean = true,
        workStart: String = "00:00",
        workEnd: String = "23:59",
        replacedAt: String = "null",
    ): WorkScheduleWireV1 {
        val source = """{"schedule_version_id":"$scheduleId","enabled":$enabled,"selected_weekdays":$selectedWeekdays,"work_start":"$workStart","work_end":"$workEnd","reminder_times":$reminderTimes,"effective_from":$effectiveFrom,"replaced_at":$replacedAt}"""
        val ordered = WorkScheduleSchemaV1.validateAndOrder(StrictJsonV1.parseObject(source), "test.schedule")
        return WorkScheduleWireV1(StrictJsonObjectV1(ordered))
    }

    private fun fixed(scheduleId: String, slotIndex: Long, date: String, dueAt: String): ReminderWireV1 = reminder(
        """{"reminder_occurrence_id":"$REMINDER_ID","schedule_version_id":"$scheduleId","kind":"fixed","slot_index":$slotIndex,"local_date":"$date","generation":0,"creation_reason":"initial","supersedes_occurrence_id":null,"merged_into_occurrence_id":null,"is_selected_workday_at_due":true,"due_at":$dueAt,"delivered_at":null,"first_opened_at":null,"dismissed_at":null,"status":"SCHEDULED"}""",
    )

    private fun snooze(scheduleId: String, status: String = "SNOOZED"): ReminderWireV1 = reminder(
        """{"reminder_occurrence_id":"$REMINDER_ID","schedule_version_id":"$scheduleId","kind":"snooze","parent_occurrence_id":"$PARENT_ID","ordinal":0,"supersedes_occurrence_id":null,"merged_into_occurrence_id":null,"is_selected_workday_at_due":true,"due_at":${stamp("2026-08-27", "10:00:00.000Z", "UTC", 0)},"delivered_at":null,"first_opened_at":null,"dismissed_at":null,"status":"$status"}""",
    )

    private fun reminder(source: String): ReminderWireV1 {
        val ordered = ReminderSchemaV1.validateAndOrder(StrictJsonV1.parseObject(source), "test.reminder")
        return ReminderWireV1(StrictJsonObjectV1(ordered))
    }

    private fun stamp(date: String, instantTime: String, zoneId: String, offsetMinutes: Int): String =
        """{"occurred_at_utc":"${date}T$instantTime","local_date":"$date","zone_id":"$zoneId","utc_offset_minutes":$offsetMinutes}"""

    private fun stampAt(occurredAtUtc: String, localDate: String, zoneId: String, offsetMinutes: Int): String =
        """{"occurred_at_utc":"$occurredAtUtc","local_date":"$localDate","zone_id":"$zoneId","utc_offset_minutes":$offsetMinutes}"""

    private companion object {
        const val SCHEDULE_ORDINARY = "00000000-0000-4000-8000-000000000030"
        const val SCHEDULE_GAP = "00000000-0000-4000-8000-000000000031"
        const val SCHEDULE_OVERLAP = "00000000-0000-4000-8000-000000000032"
        const val REMINDER_ID = "00000000-0000-4000-8000-000000000040"
        const val PARENT_ID = "00000000-0000-4000-8000-000000000041"
        const val NEW_YORK = "America/New_York"
        const val APIA = "Pacific/Apia"
    }
}
