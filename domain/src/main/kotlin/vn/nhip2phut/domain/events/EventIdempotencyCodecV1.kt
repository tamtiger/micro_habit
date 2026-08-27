package vn.nhip2phut.domain.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import vn.nhip2phut.domain.wire.v1.WireContractException
import vn.nhip2phut.domain.wire.v1.asString
import java.nio.charset.StandardCharsets

/**
 * Builds the exact RFC-8785-compatible logical preimage. It intentionally does not expose a
 * public hash/fingerprint; the on-device layer HMACs these bytes with its non-exportable key and
 * the offline validator keeps the bytes only for the duration of one validation pass.
 */
object EventIdempotencyCodecV1 {
    data class SelectorPart(val name: String, val value: String)

    data class LogicalKey(
        val kind: EventIdempotencyKindV1,
        val domain: String,
        val parts: List<SelectorPart>,
    )

    /** The single canonical selector resolution path shared by writers and dataset validation. */
    fun logicalKey(event: ProductEventWireV1): LogicalKey {
        val spec = EventContractRegistryV1.specFor(event.name)
        spec.validateAny(event.envelope, event.properties, "event-idempotency")
        val plan = spec.idempotencyAny(event.properties)
        return LogicalKey(plan.kind, plan.domain, plan.orderedSelectors.map { SelectorPart(it, selectorValue(event, it)) })
    }

    fun logicalPreimage(event: ProductEventWireV1): ByteArray {
        val key = logicalKey(event)
        val parts = key.parts.joinToString(separator = ",") { part ->
            "{\"name\":${quote(part.name)},\"value\":${quote(part.value)}}"
        }
        // RFC 8785 lexicographic object-member order: domain, parts, schema.
        val jcs = "{\"domain\":${quote(key.domain)},\"parts\":[$parts],\"schema\":\"event-idem-v1\"}"
        return jcs.toByteArray(StandardCharsets.UTF_8)
    }

    private fun selectorValue(event: ProductEventWireV1, selector: String): String {
        when (selector) {
            "event_id" -> return event.envelope.eventId.value
            "installation_id" -> return event.envelope.installationId.value
            "decision_id" -> return event.envelope.decisionId?.value ?: missing(selector)
            "session_id" -> return event.envelope.sessionId?.value ?: missing(selector)
            "reminder_occurrence_id" -> return event.envelope.reminderOccurrenceId?.value ?: missing(selector)
            "schedule_version_id" -> return event.envelope.scheduleVersionId?.value ?: missing(selector)
        }
        val element = event.properties.body[selector]
        if (element == null && selector == "source_id") return event.envelope.sessionId?.value ?: missing(selector)
        if (element == null || element === JsonNull) missing(selector)
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.joinToString(",") { it.asString("event-idempotency.$selector") }
            else -> throw WireContractException("event-idempotency: selector '$selector' is not a canonical scalar")
        }
    }

    private fun quote(value: String): String = JsonPrimitive(value).toString()

    private fun missing(selector: String): Nothing =
        throw WireContractException("event-idempotency: selector '$selector' is missing/null")
}
