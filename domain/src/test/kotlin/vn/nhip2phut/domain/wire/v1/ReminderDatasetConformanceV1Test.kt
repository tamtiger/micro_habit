package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ReminderDatasetConformanceV1Test {
    @Test
    fun canonicalGenerationAndSnoozeGraphsPass() {
        val generation0 = fixedId(SCHEDULE_A, DATE_A, 0)
        val generation1 = fixedId(SCHEDULE_A, DATE_A, 1)
        val deliveredParent = fixedId(SCHEDULE_A, DATE_B, 0)
        val terminalSnoozeParent = fixedId(SCHEDULE_A, DATE_C, 0)

        DatasetConformanceV1.requireValidReminderOccurrences(
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "CANCELLED"),
                fixed(SCHEDULE_A, DATE_A, 1, "BLOCKED_PERMISSION", generation0),
                fixed(SCHEDULE_A, DATE_A, 2, "SCHEDULED", generation1),
                fixed(SCHEDULE_A, DATE_B, 0, "DELIVERED"),
                snooze(SCHEDULE_A, deliveredParent, "SNOOZED"),
                fixed(SCHEDULE_A, DATE_C, 0, "DELIVERED"),
                snooze(SCHEDULE_A, terminalSnoozeParent, "CANCELLED"),
            ),
        )

    }

    @Test
    fun pendingStatusMustMatchReminderKind() {
        assertReminderFailure("fixed reminder cannot use SNOOZED pending status") {
            listOf(fixed(SCHEDULE_A, DATE_A, 0, "SNOOZED"))
        }

        val parentId = fixedId(SCHEDULE_A, DATE_PREVIOUS, 0)
        assertReminderFailure("snooze reminder cannot use SCHEDULED pending status") {
            listOf(
                fixed(SCHEDULE_A, DATE_PREVIOUS, 0, "DELIVERED"),
                snooze(SCHEDULE_A, parentId, "SCHEDULED"),
            )
        }
    }

    @Test
    fun canonicalMergeWinnerMatrixPasses() {
        val equalityParentId = fixedId(SCHEDULE_A, DATE_PREVIOUS, 0)
        val equalitySnoozeId = snoozeId(equalityParentId)
        DatasetConformanceV1.requireValidReminderOccurrences(
            listOf(
                fixed(SCHEDULE_A, DATE_PREVIOUS, 0, "DELIVERED"),
                snooze(SCHEDULE_A, equalityParentId, "SNOOZED", dueAt = stamp(DATE_A)),
                fixed(SCHEDULE_A, DATE_A, 0, "MERGED", mergedInto = equalitySnoozeId),
            ),
        )

        val earlierParentId = fixedId(SCHEDULE_A, DATE_PREVIOUS, 0)
        val earlierFixedId = fixedId(SCHEDULE_A, DATE_A, 0)
        DatasetConformanceV1.requireValidReminderOccurrences(
            listOf(
                fixed(SCHEDULE_A, DATE_PREVIOUS, 0, "DELIVERED"),
                fixed(SCHEDULE_A, DATE_A, 0, "SCHEDULED"),
                snooze(
                    SCHEDULE_A,
                    earlierParentId,
                    "MERGED",
                    mergedInto = earlierFixedId,
                    dueAt = stamp(DATE_A, "10:15:00.000"),
                ),
            ),
        )
    }

    @Test
    fun sameKindMergeIsRejected() {
        val keptId = fixedId(SCHEDULE_A, DATE_A, 0)
        assertReminderFailure("opposite reminder kinds") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "SCHEDULED"),
                fixed(SCHEDULE_A, DATE_B, 0, "MERGED", mergedInto = keptId),
            )
        }
    }

    @Test
    fun crossScheduleMergeIsRejected() {
        val parentId = fixedId(SCHEDULE_B, DATE_PREVIOUS, 0)
        val keptId = snoozeId(parentId)
        assertReminderFailure("same schedule") {
            listOf(
                fixed(SCHEDULE_B, DATE_PREVIOUS, 0, "DELIVERED"),
                snooze(SCHEDULE_B, parentId, "SNOOZED", dueAt = stamp(DATE_A, "09:45:00.000")),
                fixed(SCHEDULE_A, DATE_A, 0, "MERGED", mergedInto = keptId),
            )
        }
    }

    @Test
    fun positiveDistanceMergeMustKeepTheStrictlyEarlierOccurrence() {
        val parentId = fixedId(SCHEDULE_A, DATE_PREVIOUS, 0)
        val laterSnoozeId = snoozeId(parentId)
        assertReminderFailure("strictly earlier due time") {
            listOf(
                fixed(SCHEDULE_A, DATE_PREVIOUS, 0, "DELIVERED"),
                fixed(SCHEDULE_A, DATE_A, 0, "MERGED", mergedInto = laterSnoozeId),
                snooze(SCHEDULE_A, parentId, "SNOOZED", dueAt = stamp(DATE_A, "10:15:00.000")),
            )
        }
    }

    @Test
    fun equalDueMergeMustKeepSnoozeOverFixed() {
        val parentId = fixedId(SCHEDULE_A, DATE_PREVIOUS, 0)
        val fixedId = fixedId(SCHEDULE_A, DATE_A, 0)
        assertReminderFailure("equal-due merge must keep snooze over fixed") {
            listOf(
                fixed(SCHEDULE_A, DATE_PREVIOUS, 0, "DELIVERED"),
                fixed(SCHEDULE_A, DATE_A, 0, "SCHEDULED"),
                snooze(
                    SCHEDULE_A,
                    parentId,
                    "MERGED",
                    mergedInto = fixedId,
                    dueAt = stamp(DATE_A),
                ),
            )
        }
    }

    @Test
    fun randomOccurrenceIdThatDoesNotMatchItsPreimageIsRejected() {
        assertReminderFailure("canonical reminder occurrence ID") {
            listOf(
                fixed(
                    scheduleId = SCHEDULE_A,
                    date = DATE_A,
                    generation = 0,
                    status = "CANCELLED",
                    idOverride = RANDOM_ID,
                ),
            )
        }
    }

    @Test
    fun fixedGenerationMustSupersedeTheNearestLowerEligibleTerminal() {
        val generation0 = fixedId(SCHEDULE_A, DATE_A, 0)

        assertReminderFailure("nearest lower generation") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "CANCELLED"),
                fixed(SCHEDULE_A, DATE_A, 1, "BLOCKED_PERMISSION", generation0),
                fixed(SCHEDULE_A, DATE_A, 2, "SCHEDULED", generation0),
            )
        }

        assertReminderFailure("same logical fixed key") {
            val otherKeyGeneration0 = fixedId(SCHEDULE_B, DATE_A, 0)
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "CANCELLED"),
                fixed(SCHEDULE_B, DATE_A, 0, "CANCELLED"),
                fixed(SCHEDULE_A, DATE_A, 1, "SCHEDULED", otherKeyGeneration0),
            )
        }

        assertReminderFailure("eligible terminal") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "DELIVERED"),
                fixed(SCHEDULE_A, DATE_A, 1, "SCHEDULED", generation0),
            )
        }

    }

    @Test
    fun snoozeParentMustShareScheduleBeDeliveredAndHaveAtMostOneChild() {
        val parentId = fixedId(SCHEDULE_A, DATE_A, 0)

        assertReminderFailure("same schedule") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "DELIVERED"),
                snooze(SCHEDULE_B, parentId, "SNOOZED"),
            )
        }

        assertReminderFailure("must be DELIVERED") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "CANCELLED"),
                snooze(SCHEDULE_A, parentId, "SNOOZED"),
            )
        }

        assertReminderFailure("at most one snooze child") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "DELIVERED"),
                snooze(SCHEDULE_A, parentId, "SNOOZED"),
                snooze(SCHEDULE_A, parentId, "SNOOZED", idOverride = RANDOM_ID),
            )
        }
    }

    @Test
    fun duplicatePendingFixedLogicalSourceIsRejected() {
        val generation0 = fixedId(SCHEDULE_A, DATE_A, 0)

        assertReminderFailure("at most one pending fixed occurrence") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "SCHEDULED"),
                fixed(SCHEDULE_A, DATE_A, 1, "SCHEDULED", generation0),
            )
        }
    }

    @Test
    fun cyclesAcrossReminderRelationshipGraphAreRejected() {
        val firstId = fixedId(SCHEDULE_A, DATE_A, 0)
        val secondId = fixedId(SCHEDULE_A, DATE_B, 0)

        assertReminderFailure("relationship cycle") {
            listOf(
                fixed(SCHEDULE_A, DATE_A, 0, "MERGED", mergedInto = secondId),
                fixed(SCHEDULE_A, DATE_B, 0, "MERGED", mergedInto = firstId),
            )
        }
    }

    private fun assertReminderFailure(expectedMessage: String, reminders: () -> List<ReminderWireV1>) {
        val error = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValidReminderOccurrences(reminders())
        }
        assertContains(error.message.orEmpty(), expectedMessage)
    }

    private fun fixed(
        scheduleId: String,
        date: String,
        generation: Long,
        status: String,
        supersedes: String? = null,
        mergedInto: String? = null,
        idOverride: String? = null,
    ): ReminderWireV1 {
        val occurrenceId = idOverride ?: fixedId(scheduleId, date, generation)
        val dueAt = stamp(date)
        return reminder(
            """
                {
                  "reminder_occurrence_id":"$occurrenceId",
                  "schedule_version_id":"$scheduleId",
                  "kind":"fixed",
                  "slot_index":0,
                  "local_date":"$date",
                  "generation":$generation,
                  "creation_reason":"${if (generation == 0L) "initial" else "slot_reeligible"}",
                  "supersedes_occurrence_id":${supersedes.jsonNullable()},
                  "merged_into_occurrence_id":${mergedInto.jsonNullable()},
                  "is_selected_workday_at_due":true,
                  "due_at":$dueAt,
                  "delivered_at":${if (status == "DELIVERED") dueAt else "null"},
                  "first_opened_at":null,
                  "dismissed_at":null,
                  "status":"$status"
                }
            """.trimIndent(),
        )
    }

    private fun snooze(
        scheduleId: String,
        parentId: String,
        status: String,
        idOverride: String? = null,
        mergedInto: String? = null,
        dueAt: String = stamp(DATE_A),
    ): ReminderWireV1 {
        val occurrenceId = idOverride ?: snoozeId(parentId)
        return reminder(
            """
                {
                  "reminder_occurrence_id":"$occurrenceId",
                  "schedule_version_id":"$scheduleId",
                  "kind":"snooze",
                  "parent_occurrence_id":"$parentId",
                  "ordinal":0,
                  "supersedes_occurrence_id":null,
                  "merged_into_occurrence_id":${mergedInto.jsonNullable()},
                  "is_selected_workday_at_due":true,
                  "due_at":$dueAt,
                  "delivered_at":${if (status == "DELIVERED") dueAt else "null"},
                  "first_opened_at":null,
                  "dismissed_at":null,
                  "status":"$status"
                }
            """.trimIndent(),
        )
    }

    private fun reminder(source: String): ReminderWireV1 {
        val ordered = ReminderSchemaV1.validateAndOrder(StrictJsonV1.parseObject(source), "test.reminder")
        return ReminderWireV1(StrictJsonObjectV1(ordered))
    }

    private fun fixedId(scheduleId: String, date: String, generation: Long): String =
        ReminderOccurrenceIdCodecV1.fixed(
            UuidWireV1.parse(scheduleId),
            0,
            DateWireV1.parse(date),
            generation,
        ).value

    private fun snoozeId(parentId: String): String =
        ReminderOccurrenceIdCodecV1.snooze(UuidWireV1.parse(parentId), 0).value

    private fun String?.jsonNullable(): String = this?.let { "\"$it\"" } ?: "null"

    private fun stamp(date: String, time: String = "10:00:00.000"): String =
        "{\"occurred_at_utc\":\"${date}T${time}Z\",\"local_date\":\"$date\",\"zone_id\":\"UTC\",\"utc_offset_minutes\":0}"

    private companion object {
        const val SCHEDULE_A = "00000000-0000-4000-8000-000000000030"
        const val SCHEDULE_B = "00000000-0000-4000-8000-000000000031"
        const val RANDOM_ID = "00000000-0000-4000-8000-000000000099"
        const val DATE_PREVIOUS = "2026-08-26"
        const val DATE_A = "2026-08-27"
        const val DATE_B = "2026-08-28"
        const val DATE_C = "2026-08-29"
    }
}
