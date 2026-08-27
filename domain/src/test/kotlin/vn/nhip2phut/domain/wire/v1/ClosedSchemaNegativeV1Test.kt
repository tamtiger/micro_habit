package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ClosedSchemaNegativeV1Test {
    private val stamp = """
        {
          "occurred_at_utc":"2026-08-27T08:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0
        }
    """.trimIndent()

    @Test
    fun workScheduleRejectsSemanticArrayAndTimeAliases() {
        val valid = """
            {
              "schedule_version_id":"00000000-0000-4000-8000-000000000020",
              "enabled":true,
              "selected_weekdays":[1,3],
              "work_start":"09:00",
              "work_end":"17:00",
              "reminder_times":["10:00","15:00"],
              "effective_from":$stamp,
              "replaced_at":null
            }
        """.trimIndent()
        WorkScheduleSchemaV1.validateAndOrder(StrictJsonV1.parseObject(valid), "schedule")

        listOf(
            valid.replace("[1,3]", "[3,1]"),
            valid.replace("[1,3]", "[1,1]"),
            valid.replace("[\"10:00\",\"15:00\"]", "[\"15:00\",\"10:00\"]"),
            valid.replace("\"09:00\"", "\"9:00\""),
            valid.replace("\"17:00\"", "\"09:00\""),
        ).forEach { mutant ->
            assertFailsWith<WireContractException> {
                WorkScheduleSchemaV1.validateAndOrder(StrictJsonV1.parseObject(mutant), "schedule")
            }
        }
    }

    @Test
    fun checkInDiscriminantDoesNotNormalizeOppositeBranch() {
        val valid = """
            {
              "check_in_id":"00000000-0000-4000-8000-000000000021",
              "parent_id":null,
              "schedule_version_id":"00000000-0000-4000-8000-000000000020",
              "rule_version":1,
              "answers_kind":"full",
              "red_flag":false,
              "acute_issue":"none",
              "energy":"okay",
              "stiffness":"mild",
              "intent":"gentle",
              "confirmed_at":$stamp,
              "confirmed_boot_marker":1,
              "confirmed_elapsed_realtime_ms":2,
              "ttl_monotonic_deadline_ms":3,
              "confirmed_clock_generation":4,
              "confirmed_zone_id":"UTC",
              "confirmed_wall_minus_elapsed_ms":5
            }
        """.trimIndent()
        CheckInSchemaV1.validateAndOrder(StrictJsonV1.parseObject(valid), "check-in")

        listOf(
            valid.replace("\"answers_kind\":\"full\"", "\"answers_kind\":\"acute_stop\""),
            valid.replace("\"acute_issue\":\"none\"", "\"acute_issue\":null"),
            valid.replace("\"energy\":\"okay\"", "\"energy\":null"),
            valid.replace("\"rule_version\":1", "\"rule_version\":\"1\""),
            valid.replace("\"confirmed_zone_id\":\"UTC\"", "\"confirmed_zone_id\":\"utc\""),
        ).forEach { mutant ->
            assertFailsWith<WireContractException> {
                CheckInSchemaV1.validateAndOrder(StrictJsonV1.parseObject(mutant), "check-in")
            }
        }
    }

    @Test
    fun reminderOppositeBranchKeyIsForbiddenEvenWhenNull() {
        val valid = """
            {
              "reminder_occurrence_id":"448eaf7b-8277-8012-9fdd-dd3ea3f33c4a",
              "schedule_version_id":"00000000-0000-4000-8000-000000000020",
              "kind":"fixed",
              "slot_index":0,
              "local_date":"2026-08-27",
              "generation":0,
              "creation_reason":"initial",
              "supersedes_occurrence_id":null,
              "merged_into_occurrence_id":null,
              "is_selected_workday_at_due":true,
              "due_at":$stamp,
              "delivered_at":null,
              "first_opened_at":null,
              "dismissed_at":null,
              "status":"SCHEDULED"
            }
        """.trimIndent()
        ReminderSchemaV1.validateAndOrder(StrictJsonV1.parseObject(valid), "reminder")

        listOf(
            valid.replace("\"supersedes_occurrence_id\":null,", "\"parent_occurrence_id\":null,\"supersedes_occurrence_id\":null,"),
            valid.replace("\"generation\":0", "\"generation\":1"),
            valid.replace("\"status\":\"SCHEDULED\"", "\"status\":\"DELIVERED\""),
            valid.replace("\"kind\":\"fixed\"", "\"kind\":\"FIXED\""),
        ).forEach { mutant ->
            assertFailsWith<WireContractException> {
                ReminderSchemaV1.validateAndOrder(StrictJsonV1.parseObject(mutant), "reminder")
            }
        }
    }

    @Test
    fun weeklyRateUsesIndependentSuppressionAndExactHalfUpInteger() {
        val valid = """{"numerator":2,"denominator":5,"value_percent":40,"suppression_reason":null}"""
        WeeklyRateSchemaV1.validateAndOrder(StrictJsonV1.parseObject(valid), "rate")

        listOf(
            valid.replace("40", "39"),
            valid.replace("\"denominator\":5", "\"denominator\":4"),
            valid.replace("\"numerator\":2", "\"numerator\":6"),
            valid.replace("40", "40.0"),
            valid.replace("null", "\"insufficient_sample\""),
        ).forEach { mutant ->
            assertFailsWith<WireContractException> {
                WeeklyRateSchemaV1.validateAndOrder(StrictJsonV1.parseObject(mutant), "rate")
            }
        }
    }
}
