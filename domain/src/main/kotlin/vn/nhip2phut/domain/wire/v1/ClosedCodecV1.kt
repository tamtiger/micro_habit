package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vn.nhip2phut.domain.events.EventContractRegistryV1
import vn.nhip2phut.domain.events.EventEnvelopeV1
import vn.nhip2phut.domain.events.EventNameV1
import vn.nhip2phut.domain.events.EventSourceV1
import vn.nhip2phut.domain.events.ProductEventWireV1

/** One closed codec shared by export, offline import and the typed event boundary. */
object ClosedCodecV1 {
    fun decodeExport(source: String): ExportDatasetWireV1 {
        val root = StrictJsonV1.parseObject(source)
        RootExportSchemaV1.validateAndOrder(root, "export")
        val metadata = ExportMetadataWireV1.fromObject(
            root.requiredElement("metadata", "export").asStrictObject("export.metadata"),
            "export.metadata",
        )
        val dataset = ExportDatasetWireV1(
            metadata = metadata,
            profile = decodeRecords(root, "profile", ProfileSchemaV1, ::ProfileWireV1),
            workSchedule = decodeRecords(root, "work_schedule", WorkScheduleSchemaV1, ::WorkScheduleWireV1),
            checkIns = decodeRecords(root, "check_ins", CheckInSchemaV1, ::CheckInWireV1),
            decisions = decodeRecords(root, "decisions", DecisionSchemaV1, ::DecisionWireV1),
            sessions = decodeRecords(root, "sessions", SessionSchemaV1, ::SessionWireV1),
            feedback = decodeRecords(root, "feedback", FeedbackSchemaV1, ::FeedbackWireV1),
            reminders = decodeRecords(root, "reminders", ReminderSchemaV1, ::ReminderWireV1),
            events = root.requiredElement("events", "export").asArray("export.events").mapIndexed { index, element ->
                decodeEventElement(element, "export.events[$index]")
            },
            weeklySummaries = decodeRecords(root, "weekly_summaries", WeeklySummarySchemaV1, ::WeeklySummaryWireV1),
        )
        DatasetConformanceV1.requireValid(dataset)
        return dataset
    }

    fun encodeExport(dataset: ExportDatasetWireV1): String {
        DatasetConformanceV1.requireValid(dataset)
        val root = JsonObject(
            linkedMapOf(
                "metadata" to dataset.metadata.toJson(),
                "profile" to encodeRecords(dataset.profile, ProfileSchemaV1, "export.profile"),
                "work_schedule" to encodeRecords(dataset.workSchedule, WorkScheduleSchemaV1, "export.work_schedule"),
                "check_ins" to encodeRecords(dataset.checkIns, CheckInSchemaV1, "export.check_ins"),
                "decisions" to encodeRecords(dataset.decisions, DecisionSchemaV1, "export.decisions"),
                "sessions" to encodeRecords(dataset.sessions, SessionSchemaV1, "export.sessions"),
                "feedback" to encodeRecords(dataset.feedback, FeedbackSchemaV1, "export.feedback"),
                "reminders" to encodeRecords(dataset.reminders, ReminderSchemaV1, "export.reminders"),
                "events" to JsonArray(dataset.events.mapIndexed { index, event -> encodeEventElement(event, "export.events[$index]") }),
                "weekly_summaries" to encodeRecords(dataset.weeklySummaries, WeeklySummarySchemaV1, "export.weekly_summaries"),
            ),
        )
        return StrictJsonV1.encode(root)
    }

    fun decodeEvent(source: String): ProductEventWireV1 = decodeEventElement(StrictJsonV1.parse(source), "event")

    fun encodeEvent(event: ProductEventWireV1): String = StrictJsonV1.encode(encodeEventElement(event, "event"))

    internal fun decodeEventElement(element: JsonElement, path: String): ProductEventWireV1 {
        val raw = element.asStrictObject(path)
        EventEnvelopeSchemaV1.validateAndOrder(raw, path)
        val eventNameToken = raw.requiredString("name", path)
        val name = EventNameV1.fromWire(eventNameToken) ?: fail("$path.name", "unknown or wrong-case event name '$eventNameToken'")
        val spec = EventContractRegistryV1.specFor(name)
        val envelope = EventEnvelopeV1(
            eventId = UuidWireV1.parse(raw.requiredString("event_id", path)),
            occurred = LocalStampWireV1(
                occurredAtUtc = InstantWireV1.parse(raw.requiredString("occurred_at_utc", path)),
                localDate = DateWireV1.parse(raw.requiredString("local_date", path)),
                zoneId = raw.requiredString("zone_id", path),
                utcOffsetMinutes = raw.requiredInt64("utc_offset_minutes", path),
            ),
            installationId = UuidWireV1.parse(raw.requiredString("installation_id", path)),
            decisionId = raw.nullableUuid("decision_id", path),
            sessionId = raw.nullableUuid("session_id", path),
            reminderOccurrenceId = raw.nullableUuid("reminder_occurrence_id", path),
            scheduleVersionId = raw.nullableUuid("schedule_version_id", path),
            source = raw.nullableString("source", path)?.let { source ->
                EventSourceV1.fromWire(source) ?: fail("$path.source", "unknown or wrong-case source '$source'")
            },
        )
        val properties = spec.decodeAny(raw.requiredElement("properties", path).asStrictObject("$path.properties"), "$path.properties")
        spec.validateAny(envelope, properties, path)
        return ProductEventWireV1(name, envelope, properties)
    }

    internal fun encodeEventElement(event: ProductEventWireV1, path: String): JsonObject {
        val spec = EventContractRegistryV1.specFor(event.name)
        spec.validateAny(event.envelope, event.properties, path)
        val properties = spec.encodeAny(event.properties, "$path.properties").element
        val envelope = event.envelope
        return JsonObject(
            linkedMapOf(
                "event_id" to JsonPrimitive(envelope.eventId.value),
                "event_schema_version" to JsonPrimitive(1),
                "name" to JsonPrimitive(event.name.wire),
                "occurred_at_utc" to JsonPrimitive(envelope.occurred.occurredAtUtc.value),
                "local_date" to JsonPrimitive(envelope.occurred.localDate.value),
                "zone_id" to JsonPrimitive(envelope.occurred.zoneId),
                "utc_offset_minutes" to JsonPrimitive(envelope.occurred.utcOffsetMinutes),
                "installation_id" to JsonPrimitive(envelope.installationId.value),
                "decision_id" to envelope.decisionId.toJsonNullable(),
                "session_id" to envelope.sessionId.toJsonNullable(),
                "reminder_occurrence_id" to envelope.reminderOccurrenceId.toJsonNullable(),
                "schedule_version_id" to envelope.scheduleVersionId.toJsonNullable(),
                "source" to (envelope.source?.let { JsonPrimitive(it.wire) } ?: JsonNull),
                "properties" to properties,
            ),
        )
    }
}

internal val EventEnvelopeSchemaV1 = ClosedObjectSchemaV1(
    "ProductEventWireV1",
    listOf(
        required("event_id", UuidShapeV1),
        required("event_schema_version", Int64ShapeV1(literal = 1)),
        required("name", EnumShapeV1(EventNameV1.entries.map { it.wire })),
        required("occurred_at_utc", InstantShapeV1),
        required("local_date", DateShapeV1),
        required("zone_id", ZoneIdShapeV1),
        required("utc_offset_minutes", Int64ShapeV1(-1080, 1080)),
        required("installation_id", UuidShapeV1),
        required("decision_id", NullableShapeV1(UuidShapeV1)),
        required("session_id", NullableShapeV1(UuidShapeV1)),
        required("reminder_occurrence_id", NullableShapeV1(UuidShapeV1)),
        required("schedule_version_id", NullableShapeV1(UuidShapeV1)),
        required("source", NullableShapeV1(EnumShapeV1(listOf("home", "reminder")))),
        required("properties", AnyObjectShapeV1),
    ),
) { value, path -> validateFlatLocalStamp(value, path) }

private inline fun <T> decodeRecords(
    root: StrictJsonObjectV1,
    key: String,
    schema: ClosedObjectSchemaV1,
    factory: (StrictJsonObjectV1) -> T,
): List<T> = root.requiredElement(key, "export").asArray("export.$key").mapIndexed { index, element ->
    val path = "export.$key[$index]"
    factory(StrictJsonObjectV1(schema.validateAndOrder(element.asStrictObject(path), path)))
}

private fun <T : ClosedWireRecordV1> encodeRecords(
    records: List<T>,
    schema: ClosedObjectSchemaV1,
    path: String,
): JsonArray = JsonArray(records.mapIndexed { index, record -> schema.validateAndOrder(record.body, "$path[$index]") })

private fun StrictJsonObjectV1.nullableUuid(key: String, path: String): UuidWireV1? =
    nullableString(key, path)?.let(UuidWireV1::parse)

private fun UuidWireV1?.toJsonNullable(): JsonElement = this?.let { JsonPrimitive(it.value) } ?: JsonNull
