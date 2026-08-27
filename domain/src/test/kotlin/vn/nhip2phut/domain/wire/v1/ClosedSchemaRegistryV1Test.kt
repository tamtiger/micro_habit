package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClosedSchemaRegistryV1Test {
    @Test
    fun registryContainsTheExactRootAndAllClosedRecordShapes() {
        assertEquals(
            listOf(
                "metadata", "profile", "work_schedule", "check_ins", "decisions", "sessions",
                "feedback", "reminders", "events", "weekly_summaries",
            ),
            ClosedSchemaRegistryV1.rootKeys,
        )
        assertEquals(
            listOf(
                "profile", "work_schedule", "check_ins", "decisions", "sessions", "feedback",
                "reminders", "events", "weekly_summaries",
            ),
            ClosedSchemaRegistryV1.collectionKeys,
        )
        assertEquals(
            listOf("profile", "work_schedule", "check_ins", "decisions", "sessions", "feedback", "reminders", "weekly_summaries"),
            ClosedSchemaRegistryV1.entityKeys.keys.toList(),
        )
        assertEquals(
            listOf(
                "installation_id", "adult_confirmed", "eligibility_scope_confirmed", "locale",
                "onboarding_completed_at", "activation_boot_marker", "activation_elapsed_realtime_ms",
                "activation_clock_generation", "activation_wall_minus_elapsed_ms", "safety_acknowledgements",
                "current_safety_acknowledgement_id",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("profile"),
        )
        assertEquals(
            listOf(
                "schedule_version_id", "enabled", "selected_weekdays", "work_start", "work_end",
                "reminder_times", "effective_from", "replaced_at",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("work_schedule"),
        )
        assertEquals(
            listOf(
                "check_in_id", "parent_id", "schedule_version_id", "rule_version", "answers_kind", "red_flag",
                "acute_issue", "energy", "stiffness", "intent", "confirmed_at", "confirmed_boot_marker",
                "confirmed_elapsed_realtime_ms", "ttl_monotonic_deadline_ms", "confirmed_clock_generation",
                "confirmed_zone_id", "confirmed_wall_minus_elapsed_ms",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("check_ins"),
        )
        assertEquals(
            listOf(
                "decision_id", "check_in_id", "schedule_version_id", "rule_version", "outcome", "base_mode",
                "effective_mode", "reason_codes", "invalid_fields", "created_safety_hold_snapshot",
                "created_rest_suppression_snapshot", "evaluation_day_mode_cap_snapshot", "created_at",
                "reconfirm_after", "valid_until_work_end", "confirmed_boot_marker",
                "confirmed_elapsed_realtime_ms", "ttl_monotonic_deadline_ms", "confirmed_clock_generation",
                "confirmed_zone_id", "confirmed_wall_minus_elapsed_ms",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("decisions"),
        )
        assertEquals(
            listOf(
                "session_id", "decision_id", "schedule_version_id", "routine_id", "content_identity", "routine_mode",
                "decision_effective_mode_at_start", "runtime_effective_mode_at_start",
                "runtime_day_mode_cap_snapshot_at_start", "source", "reminder_occurrence_id",
                "is_selected_workday_at_start", "started_at", "start_boot_marker", "start_elapsed_realtime_ms",
                "start_clock_generation", "start_wall_minus_elapsed_ms", "status", "player_checkpoint", "terminal_at",
                "session_origin_day_expires_at_utc", "session_origin_clock_integrity", "completion_boot_marker",
                "completion_elapsed_realtime_ms", "completion_clock_generation", "completion_wall_minus_elapsed_ms",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("sessions"),
        )
        assertEquals(
            listOf(
                "session_id", "pain_gate_status", "new_or_worse_pain", "pain_answered_at", "effort", "context_fit",
                "created_post_session_safety_hold_snapshot", "day_mode_cap_update_snapshot", "updated_at",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("feedback"),
        )
        assertEquals(
            listOf(
                "reminder_occurrence_id", "schedule_version_id", "kind", "slot_index", "local_date", "generation",
                "creation_reason", "parent_occurrence_id", "ordinal", "supersedes_occurrence_id",
                "merged_into_occurrence_id", "is_selected_workday_at_due", "due_at", "delivered_at",
                "first_opened_at", "dismissed_at", "status",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("reminders"),
        )
        assertEquals(
            listOf(
                "summary_id", "week_start_local_date", "week_zone_id", "occurred_at_utc", "local_date", "zone_id",
                "utc_offset_minutes", "qualified_break_days", "started_count", "completed_count", "effort_easy_count",
                "effort_moderate_count", "effort_too_hard_count", "pain_yes_count", "pain_no_count",
                "context_yes_count", "context_no_count", "reminder_opened_count", "reminder_snoozed_count",
                "reminder_dismissed_count", "completion_rate", "context_fit_rate", "new_or_worse_pain_rate",
            ),
            ClosedSchemaRegistryV1.entityKeys.getValue("weekly_summaries"),
        )
        assertEquals(
            listOf(
                "export_schema_version", "exported_at_utc", "app_version", "content_version", "rule_version",
                "retention_policy_version", "record_counts",
            ),
            MetadataSchemaV1.keys,
        )
        assertEquals(
            listOf(
                "event_id", "event_schema_version", "name", "occurred_at_utc", "local_date", "zone_id",
                "utc_offset_minutes", "installation_id", "decision_id", "session_id", "reminder_occurrence_id",
                "schedule_version_id", "source", "properties",
            ),
            EventEnvelopeSchemaV1.keys,
        )
        assertFalse("allowed_modes" in ClosedSchemaRegistryV1.entityKeys.getValue("decisions"))
        assertFalse("presentation_route" in ClosedSchemaRegistryV1.entityKeys.getValue("decisions"))
        assertFalse("feedback_id" in ClosedSchemaRegistryV1.entityKeys.getValue("feedback"))
    }
}
