package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClosedCodecV1Test {
    private val emptyDataset = """
        {
          "metadata": {
            "export_schema_version": 1,
            "exported_at_utc": "2026-08-27T08:00:00.000Z",
            "app_version": "1.0.0",
            "content_version": "1.0.0",
            "rule_version": 1,
            "retention_policy_version": 1,
            "record_counts": {
              "profile": 0,
              "work_schedule": 0,
              "check_ins": 0,
              "decisions": 0,
              "sessions": 0,
              "feedback": 0,
              "reminders": 0,
              "events": 0,
              "weekly_summaries": 0
            }
          },
          "profile": [],
          "work_schedule": [],
          "check_ins": [],
          "decisions": [],
          "sessions": [],
          "feedback": [],
          "reminders": [],
          "events": [],
          "weekly_summaries": []
        }
    """.trimIndent()

    @Test
    fun emptyDatasetRoundTripsThroughTheSharedClosedCodec() {
        val decoded = ClosedCodecV1.decodeExport(emptyDataset)

        assertEquals(0, decoded.metadata.recordCounts.events)
        assertEquals(decoded, ClosedCodecV1.decodeExport(ClosedCodecV1.encodeExport(decoded)))
        assertEquals(ClosedSchemaRegistryV1.rootKeys, StrictJsonV1.parseObject(ClosedCodecV1.encodeExport(decoded)).keys)
    }

    @Test
    fun rootRequiresExactlyMetadataAndNineArrays() {
        val unknown = emptyDataset.replace("\"weekly_summaries\": []", "\"weekly_summaries\": [], \"extra\": []")
        val missing = emptyDataset.replace(",\n  \"feedback\": []", "")
        val wrongType = emptyDataset.replace("\"sessions\": []", "\"sessions\": {}")
        val nullFlip = emptyDataset.replace("\"reminders\": []", "\"reminders\": null")

        listOf(unknown, missing, wrongType, nullFlip).forEach { mutant ->
            assertFailsWith<WireContractException> { ClosedCodecV1.decodeExport(mutant) }
        }
    }

    @Test
    fun duplicateObjectMemberIsRejectedBeforeBinding() {
        val duplicate = emptyDataset.replace(
            "\"export_schema_version\": 1,",
            "\"export_schema_version\": 1, \"export_schema_version\": 1,",
        )

        val error = assertFailsWith<WireContractException> { ClosedCodecV1.decodeExport(duplicate) }
        assertTrue(error.message.orEmpty().contains("duplicate", ignoreCase = true))
    }

    @Test
    fun numericStringsAndFloatsDoNotCoerceToInt64() {
        val numericString = emptyDataset.replace("\"profile\": 0", "\"profile\": \"0\"")
        val float = emptyDataset.replace("\"profile\": 0", "\"profile\": 0.0")

        assertFailsWith<WireContractException> { ClosedCodecV1.decodeExport(numericString) }
        assertFailsWith<WireContractException> { ClosedCodecV1.decodeExport(float) }
    }

    @Test
    fun profileAcknowledgementHistoryRoundTripsAndFailsClosed() {
        val valid = emptyDataset
            .replace("\"profile\": 0", "\"profile\": 1")
            .replace("\"events\": 0", "\"events\": 2")
            .replace(
                "\"profile\": [],",
                """
                "profile": [{
                  "installation_id": "00000000-0000-4000-8000-000000000010",
                  "adult_confirmed": true,
                  "eligibility_scope_confirmed": true,
                  "locale": "vi-VN",
                  "onboarding_completed_at": {
                    "occurred_at_utc": "2026-08-27T08:00:00.000Z",
                    "local_date": "2026-08-27",
                    "zone_id": "UTC",
                    "utc_offset_minutes": 0
                  },
                  "activation_boot_marker": 1,
                  "activation_elapsed_realtime_ms": 2,
                  "activation_clock_generation": 3,
                  "activation_wall_minus_elapsed_ms": 4,
                  "safety_acknowledgements": [{
                    "acknowledgement_id": "00000000-0000-4000-8000-000000000011",
                    "kind": "onboarding",
                    "content_version": "1.0.0",
                    "content_digest": "0000000000000000000000000000000000000000000000000000000000000000",
                    "acknowledged_at": {
                      "occurred_at_utc": "2026-08-27T08:00:00.000Z",
                      "local_date": "2026-08-27",
                      "zone_id": "UTC",
                      "utc_offset_minutes": 0
                    }
                  }],
                  "current_safety_acknowledgement_id": "00000000-0000-4000-8000-000000000011"
                }],
                """.trimIndent(),
            )
            .replace("\"events\": []", "\"events\": [$profileCompanionEvents]")
        val decoded = ClosedCodecV1.decodeExport(valid)
        assertEquals(decoded, ClosedCodecV1.decodeExport(ClosedCodecV1.encodeExport(decoded)))

        listOf(
            valid.replace("\"adult_confirmed\": true", "\"adult_confirmed\": false"),
            valid.replace("\"locale\": \"vi-VN\"", "\"locale\": \"VI-vn\""),
            valid.replace(
                "\"current_safety_acknowledgement_id\": \"00000000-0000-4000-8000-000000000011\"",
                "\"current_safety_acknowledgement_id\": \"00000000-0000-4000-8000-000000000012\"",
            ),
            valid.replace("\"content_version\": \"1.0.0\"", "\"content_version\": \"01.0.0\""),
        ).forEach { mutant -> assertFailsWith<WireContractException> { ClosedCodecV1.decodeExport(mutant) } }
    }

    private val profileCompanionEvents = """
        {
          "event_id":"00000000-0000-4000-8000-000000000012",
          "event_schema_version":1,
          "name":"scope_acknowledged",
          "occurred_at_utc":"2026-08-27T08:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "installation_id":"00000000-0000-4000-8000-000000000010",
          "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
          "properties":{
            "acknowledgement_id":"00000000-0000-4000-8000-000000000011",
            "kind":"onboarding",
            "eligibility_confirmed":true,
            "content_version":"1.0.0",
            "content_digest":"0000000000000000000000000000000000000000000000000000000000000000"
          }
        },
        {
          "event_id":"00000000-0000-4000-8000-000000000013",
          "event_schema_version":1,
          "name":"onboarding_completed",
          "occurred_at_utc":"2026-08-27T08:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "installation_id":"00000000-0000-4000-8000-000000000010",
          "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
          "properties":{
            "duration_ms":1,
            "activation_boot_marker":1,
            "activation_elapsed_realtime_ms":2,
            "activation_clock_generation":3,
            "activation_wall_minus_elapsed_ms":4
          }
        }
    """.trimIndent()
}
