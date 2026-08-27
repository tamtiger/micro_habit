package vn.nhip2phut.domain.events

import vn.nhip2phut.domain.wire.v1.ClosedCodecV1
import vn.nhip2phut.domain.wire.v1.WireContractException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.charset.StandardCharsets

class ClosedEventCodecV1Test {
    private val validFirstOpenEvent = """
        {
          "event_id": "00000000-0000-4000-8000-000000000001",
          "event_schema_version": 1,
          "name": "app_first_opened",
          "occurred_at_utc": "2026-08-27T08:00:00.000Z",
          "local_date": "2026-08-27",
          "zone_id": "UTC",
          "utc_offset_minutes": 0,
          "installation_id": "00000000-0000-4000-8000-000000000002",
          "decision_id": null,
          "session_id": null,
          "reminder_occurrence_id": null,
          "schedule_version_id": null,
          "source": null,
          "properties": {
            "first_open_id": "00000000-0000-4000-8000-000000000003"
          }
        }
    """.trimIndent()

    @Test
    fun registryHasOneTypedSpecForEveryEvent() {
        assertEquals(48, EventContractRegistryV1.specs.size)
        assertEquals(EventNameV1.entries.toSet(), EventContractRegistryV1.specs.keys)
        assertEquals(48, EventContractRegistryV1.specs.values.map { it.propertiesType }.distinct().size)
    }

    @Test
    fun eventRoundTripUsesTheSameSpecAsWriterAndImporter() {
        val decoded = ClosedCodecV1.decodeEvent(validFirstOpenEvent)

        assertTrue(decoded.properties is AppFirstOpenedPropertiesV1)
        assertEquals(decoded, ClosedCodecV1.decodeEvent(ClosedCodecV1.encodeEvent(decoded)))
    }

    @Test
    fun idempotencyPreimageUsesExactJcsShapeAndRegistrySelectorOrder() {
        val event = ClosedCodecV1.decodeEvent(validFirstOpenEvent)

        assertEquals(
            "{\"domain\":\"app_first_opened\",\"parts\":[{\"name\":\"first_open_id\",\"value\":\"00000000-0000-4000-8000-000000000003\"}],\"schema\":\"event-idem-v1\"}",
            String(EventIdempotencyCodecV1.logicalPreimage(event), StandardCharsets.UTF_8),
        )
    }

    @Test
    fun eventRejectsUnknownMissingDuplicateWrongTypeNullFlipAndEnumCase() {
        val mutants = listOf(
            validFirstOpenEvent.replace("\"first_open_id\":", "\"extra\": true, \"first_open_id\":"),
            validFirstOpenEvent.replace("\"first_open_id\": \"00000000-0000-4000-8000-000000000003\"", ""),
            validFirstOpenEvent.replace("\"first_open_id\":", "\"first_open_id\": \"00000000-0000-4000-8000-000000000003\", \"first_open_id\":"),
            validFirstOpenEvent.replace("\"first_open_id\": \"00000000-0000-4000-8000-000000000003\"", "\"first_open_id\": 3"),
            validFirstOpenEvent.replace("\"first_open_id\": \"00000000-0000-4000-8000-000000000003\"", "\"first_open_id\": null"),
            validFirstOpenEvent.replace("\"app_first_opened\"", "\"APP_FIRST_OPENED\""),
        )

        mutants.forEach { mutant ->
            assertFailsWith<WireContractException> { ClosedCodecV1.decodeEvent(mutant) }
        }
    }

    @Test
    fun forbiddenEnvelopeSlotIsRejected() {
        val mutant = validFirstOpenEvent.replace(
            "\"decision_id\": null",
            "\"decision_id\": \"00000000-0000-4000-8000-000000000004\"",
        )

        assertFailsWith<WireContractException> { ClosedCodecV1.decodeEvent(mutant) }
    }
}
