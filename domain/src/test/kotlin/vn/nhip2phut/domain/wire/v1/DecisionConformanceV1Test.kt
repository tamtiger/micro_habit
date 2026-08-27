package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DecisionConformanceV1Test {
    @Test
    fun acceptsEveryCanonicalSnapshotOutcomeBranch() {
        listOf(
            decisionJson(),
            decisionJson(
                outcome = "URGENT_STOP",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_RED_FLAG_PRESENT\"]",
                safetySnapshot = safetySnapshot("RED_FLAG"),
            ),
            decisionJson(
                outcome = "PAUSE_TODAY",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_ACUTE_ILLNESS\"]",
                safetySnapshot = safetySnapshot("ACUTE_ILLNESS"),
            ),
            decisionJson(
                outcome = "REST_ONLY",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_INTENT_REST\"]",
                restSnapshot = restSnapshot(),
            ),
            decisionJson(
                effectiveMode = "MAINTAIN",
                reasonCodes = "[\"SAF_BUILD_CONDITIONS\",\"SAF_DAY_MODE_CAP_APPLIED\"]",
                capSnapshot = capSnapshot(),
            ),
            decisionJson(
                outcome = "INCOMPLETE",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_INPUT_INVALID\"]",
                invalidFields = "[\"day_mode_cap\"]",
            ),
        ).forEachIndexed { index, decision ->
            DecisionSchemaV1.validateAndOrder(StrictJsonV1.parseObject(decision), "decision[$index]")
        }
    }

    @Test
    fun rejectsMissingExtraOrWrongSnapshotForOutcome() {
        val mutants = listOf(
            decisionJson(
                outcome = "URGENT_STOP",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_RED_FLAG_PRESENT\"]",
            ),
            decisionJson(safetySnapshot = safetySnapshot("RED_FLAG")),
            decisionJson(restSnapshot = restSnapshot()),
            decisionJson(
                outcome = "REST_ONLY",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_INTENT_REST\"]",
            ),
            decisionJson(
                outcome = "URGENT_STOP",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_RED_FLAG_PRESENT\"]",
                safetySnapshot = safetySnapshot("ACUTE_ILLNESS"),
            ),
            decisionJson(
                outcome = "URGENT_STOP",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_RED_FLAG_PRESENT\"]",
                safetySnapshot = safetySnapshot("RED_FLAG", OTHER_ID),
            ),
            decisionJson(
                outcome = "REST_ONLY",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_INTENT_REST\"]",
                restSnapshot = restSnapshot(OTHER_ID),
            ),
            decisionJson(
                effectiveMode = "MAINTAIN",
                reasonCodes = "[\"SAF_BUILD_CONDITIONS\",\"SAF_DAY_MODE_CAP_APPLIED\"]",
            ),
            decisionJson(capSnapshot = capSnapshot()),
            decisionJson(
                effectiveMode = "MAINTAIN",
                reasonCodes = "[\"SAF_BUILD_CONDITIONS\",\"SAF_DAY_MODE_CAP_APPLIED\"]",
                capSnapshot = capSnapshot("RECOVER"),
            ),
            decisionJson(
                outcome = "INCOMPLETE",
                baseMode = null,
                effectiveMode = null,
                reasonCodes = "[\"SAF_INPUT_INVALID\"]",
                invalidFields = "[\"energy\"]",
            ),
        )

        mutants.forEachIndexed { index, mutant ->
            assertFailsWith<WireContractException>("mutant $index") {
                DecisionSchemaV1.validateAndOrder(StrictJsonV1.parseObject(mutant), "decision")
            }
        }
    }

    private fun decisionJson(
        outcome: String = "BUILD",
        baseMode: String? = "BUILD",
        effectiveMode: String? = "BUILD",
        reasonCodes: String = "[\"SAF_BUILD_CONDITIONS\"]",
        invalidFields: String = "[]",
        safetySnapshot: String = "null",
        restSnapshot: String = "null",
        capSnapshot: String = "null",
    ): String = """
        {
          "decision_id":"$DECISION_ID",
          "check_in_id":"$CHECK_IN_ID",
          "schedule_version_id":"$SCHEDULE_ID",
          "rule_version":1,
          "outcome":"$outcome",
          "base_mode":${baseMode.jsonNullable()},
          "effective_mode":${effectiveMode.jsonNullable()},
          "reason_codes":$reasonCodes,
          "invalid_fields":$invalidFields,
          "created_safety_hold_snapshot":$safetySnapshot,
          "created_rest_suppression_snapshot":$restSnapshot,
          "evaluation_day_mode_cap_snapshot":$capSnapshot,
          "created_at":$stamp,
          "reconfirm_after":"2026-08-27T16:00:00.000Z",
          "valid_until_work_end":"2026-08-27T17:00:00.000Z",
          "confirmed_boot_marker":1,
          "confirmed_elapsed_realtime_ms":2,
          "ttl_monotonic_deadline_ms":3,
          "confirmed_clock_generation":4,
          "confirmed_zone_id":"UTC",
          "confirmed_wall_minus_elapsed_ms":5
        }
    """.trimIndent()

    private fun safetySnapshot(kind: String, sourceId: String = CHECK_IN_ID): String = """
        {
          "occurred_at_utc":"2026-08-27T10:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "kind":"$kind",
          "source_type":"check_in",
          "source_id":"$sourceId",
          "expires_at_utc":"2026-08-28T00:00:00.000Z",
          "clock_integrity":$clockIntegrity,
          "rule_version":1
        }
    """.trimIndent()

    private fun String?.jsonNullable(): String = this?.let { "\"$it\"" } ?: "null"

    private fun restSnapshot(sourceDecisionId: String = DECISION_ID): String =
        """{"occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,"source_decision_id":"$sourceDecisionId","expires_at_utc":"2026-08-28T00:00:00.000Z","clock_integrity":$clockIntegrity,"rule_version":1}"""

    private fun capSnapshot(maxMode: String = "MAINTAIN"): String =
        """{"occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,"max_mode":"$maxMode","mode_trigger_session_id":"00000000-0000-4000-8000-000000000041","source_session_id":"00000000-0000-4000-8000-000000000041","expires_at_utc":"2026-08-28T00:00:00.000Z","clock_integrity":$clockIntegrity,"rule_version":1}"""

    private val stamp = """{"occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
    private val clockIntegrity = """{"origin_boot_marker":1,"created_elapsed_realtime_ms":2,"monotonic_deadline_ms":3,"remaining_elapsed_ms_at_last_checkpoint":1,"original_duration_ms":1}"""

    companion object {
        private const val DECISION_ID = "00000000-0000-4000-8000-000000000030"
        private const val CHECK_IN_ID = "00000000-0000-4000-8000-000000000020"
        private const val SCHEDULE_ID = "00000000-0000-4000-8000-000000000010"
        private const val OTHER_ID = "00000000-0000-4000-8000-000000000099"
    }
}
