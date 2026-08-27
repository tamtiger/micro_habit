package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

interface ClosedWireRecordV1 {
    val body: StrictJsonObjectV1
}

internal val ExportCollectionKeysV1 = listOf(
    "profile",
    "work_schedule",
    "check_ins",
    "decisions",
    "sessions",
    "feedback",
    "reminders",
    "events",
    "weekly_summaries",
)

data class DecisionFreshnessEvidenceWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class ContentIdentityWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class ClockIntegrityEvidenceWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class SafetyHoldSnapshotWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class DayModeCapSnapshotWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class RestDaySuppressionSnapshotWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class SessionRuntimeCapSnapshotWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class DayModeCapUpdateSnapshotWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class SkippedStepWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class PlayerCheckpointWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1

data class SafetyAcknowledgementWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1 {
    val acknowledgementId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("acknowledgement_id", "SafetyAcknowledgementWireV1"))
    val kind: String get() = body.requiredString("kind", "SafetyAcknowledgementWireV1")
}

data class WeeklyRateWireV1 internal constructor(
    override val body: StrictJsonObjectV1,
) : ClosedWireRecordV1 {
    val numerator: Long get() = body.requiredInt64("numerator", "WeeklyRateWireV1")
    val denominator: Long get() = body.requiredInt64("denominator", "WeeklyRateWireV1")
    val valuePercent: Long? get() = body.nullableInt64("value_percent", "WeeklyRateWireV1")
    val suppressionReason: String? get() = body.nullableString("suppression_reason", "WeeklyRateWireV1")
}

data class RecordCountsWireV1(
    val profile: Long,
    val workSchedule: Long,
    val checkIns: Long,
    val decisions: Long,
    val sessions: Long,
    val feedback: Long,
    val reminders: Long,
    val events: Long,
    val weeklySummaries: Long,
) {
    init {
        require(listOf(profile, workSchedule, checkIns, decisions, sessions, feedback, reminders, events, weeklySummaries).all { it >= 0 })
    }

    internal fun toJson(): JsonObject = JsonObject(
        linkedMapOf(
            "profile" to kotlinx.serialization.json.JsonPrimitive(profile),
            "work_schedule" to kotlinx.serialization.json.JsonPrimitive(workSchedule),
            "check_ins" to kotlinx.serialization.json.JsonPrimitive(checkIns),
            "decisions" to kotlinx.serialization.json.JsonPrimitive(decisions),
            "sessions" to kotlinx.serialization.json.JsonPrimitive(sessions),
            "feedback" to kotlinx.serialization.json.JsonPrimitive(feedback),
            "reminders" to kotlinx.serialization.json.JsonPrimitive(reminders),
            "events" to kotlinx.serialization.json.JsonPrimitive(events),
            "weekly_summaries" to kotlinx.serialization.json.JsonPrimitive(weeklySummaries),
        ),
    )

    companion object {
        internal fun fromObject(value: StrictJsonObjectV1, path: String): RecordCountsWireV1 {
            RecordCountsSchemaV1.validateAndOrder(value, path)
            return RecordCountsWireV1(
                value.requiredInt64("profile", path),
                value.requiredInt64("work_schedule", path),
                value.requiredInt64("check_ins", path),
                value.requiredInt64("decisions", path),
                value.requiredInt64("sessions", path),
                value.requiredInt64("feedback", path),
                value.requiredInt64("reminders", path),
                value.requiredInt64("events", path),
                value.requiredInt64("weekly_summaries", path),
            )
        }
    }
}

data class ExportMetadataWireV1(
    val exportedAtUtc: InstantWireV1,
    val appVersion: SemVerWireV1,
    val contentVersion: SemVerWireV1,
    val recordCounts: RecordCountsWireV1,
) {
    val exportSchemaVersion: Int = 1
    val ruleVersion: Int = 1
    val retentionPolicyVersion: Int = 1

    internal fun toJson(): JsonObject = JsonObject(
        linkedMapOf(
            "export_schema_version" to kotlinx.serialization.json.JsonPrimitive(1),
            "exported_at_utc" to kotlinx.serialization.json.JsonPrimitive(exportedAtUtc.value),
            "app_version" to kotlinx.serialization.json.JsonPrimitive(appVersion.value),
            "content_version" to kotlinx.serialization.json.JsonPrimitive(contentVersion.value),
            "rule_version" to kotlinx.serialization.json.JsonPrimitive(1),
            "retention_policy_version" to kotlinx.serialization.json.JsonPrimitive(1),
            "record_counts" to recordCounts.toJson(),
        ),
    )

    companion object {
        internal fun fromObject(value: StrictJsonObjectV1, path: String): ExportMetadataWireV1 {
            MetadataSchemaV1.validateAndOrder(value, path)
            return ExportMetadataWireV1(
                InstantWireV1.parse(value.requiredString("exported_at_utc", path)),
                SemVerWireV1.parse(value.requiredString("app_version", path)),
                SemVerWireV1.parse(value.requiredString("content_version", path)),
                RecordCountsWireV1.fromObject(value.requiredElement("record_counts", path).asStrictObject("$path.record_counts"), "$path.record_counts"),
            )
        }
    }
}

internal val DecisionFreshnessEvidenceSchemaV1 = ClosedObjectSchemaV1(
    "DecisionFreshnessEvidenceWireV1",
    listOf(
        required("confirmed_boot_marker", NonNegativeInt64ShapeV1),
        required("confirmed_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
        required("ttl_monotonic_deadline_ms", NonNegativeInt64ShapeV1),
        required("confirmed_clock_generation", NonNegativeInt64ShapeV1),
        required("confirmed_zone_id", ZoneIdShapeV1),
        required("confirmed_wall_minus_elapsed_ms", Int64ShapeV1()),
    ),
) { value, path ->
    val confirmed = value.requiredInt64("confirmed_elapsed_realtime_ms", path)
    val deadline = value.requiredInt64("ttl_monotonic_deadline_ms", path)
    if (deadline < confirmed) fail(path, "ttl_monotonic_deadline_ms precedes confirmation")
}

internal val ContentIdentitySchemaV1 = ClosedObjectSchemaV1(
    "ContentIdentityWireV1",
    listOf(
        required("schema_version", SemVerShapeV1),
        required("content_version", SemVerShapeV1),
        required("routine_revision", SemVerShapeV1),
        required("manifest_digest_sha256", DigestShapeV1),
    ),
) { value, path ->
    if (value.requiredString("schema_version", path) != "1.0.0") fail(path, "schema_version must be 1.0.0")
}

internal val ClockIntegrityEvidenceSchemaV1 = ClosedObjectSchemaV1(
    "ClockIntegrityEvidenceWireV1",
    listOf(
        required("origin_boot_marker", NonNegativeInt64ShapeV1),
        required("created_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
        required("monotonic_deadline_ms", NonNegativeInt64ShapeV1),
        required("remaining_elapsed_ms_at_last_checkpoint", NonNegativeInt64ShapeV1),
        required("original_duration_ms", NonNegativeInt64ShapeV1),
    ),
) { value, path ->
    val created = value.requiredInt64("created_elapsed_realtime_ms", path)
    val deadline = value.requiredInt64("monotonic_deadline_ms", path)
    val remaining = value.requiredInt64("remaining_elapsed_ms_at_last_checkpoint", path)
    val duration = value.requiredInt64("original_duration_ms", path)
    if (deadline < created) fail(path, "monotonic deadline precedes creation")
    if (remaining > duration) fail(path, "remaining duration exceeds original duration")
}

private val safetyHoldKinds = listOf(
    "RED_FLAG",
    "ACUTE_ILLNESS",
    "NEW_OR_WORSENING_PAIN_OR_INJURY",
    "MEDICALLY_RESTRICTED",
    "POST_SESSION_NEW_OR_WORSE_PAIN",
)

internal val SafetyHoldSnapshotSchemaV1 = ClosedObjectSchemaV1(
    "SafetyHoldSnapshotWireV1",
    LocalStampSchemaV1.fields + listOf(
        required("kind", EnumShapeV1(safetyHoldKinds)),
        required("source_type", EnumShapeV1(listOf("check_in", "session"))),
        required("source_id", UuidShapeV1),
        required("expires_at_utc", InstantShapeV1),
        required("clock_integrity", ObjectShapeV1(ClockIntegrityEvidenceSchemaV1)),
        required("rule_version", Int64ShapeV1(literal = 1)),
    ),
) { value, path ->
    validateFlatLocalStamp(value, path)
    val kind = value.requiredString("kind", path)
    val source = value.requiredString("source_type", path)
    if (kind == "POST_SESSION_NEW_OR_WORSE_PAIN" && source != "session") fail(path, "post-session hold must use session source")
    if (kind != "POST_SESSION_NEW_OR_WORSE_PAIN" && source != "check_in") fail(path, "check-in hold kind/source mismatch")
}

internal val DayModeCapSnapshotSchemaV1 = ClosedObjectSchemaV1(
    "DayModeCapSnapshotWireV1",
    LocalStampSchemaV1.fields + listOf(
        required("max_mode", EnumShapeV1(listOf("RECOVER", "MAINTAIN"))),
        required("mode_trigger_session_id", UuidShapeV1),
        required("source_session_id", UuidShapeV1),
        required("expires_at_utc", InstantShapeV1),
        required("clock_integrity", ObjectShapeV1(ClockIntegrityEvidenceSchemaV1)),
        required("rule_version", Int64ShapeV1(literal = 1)),
    ),
) { value, path -> validateFlatLocalStamp(value, path) }

internal val RestDaySuppressionSnapshotSchemaV1 = ClosedObjectSchemaV1(
    "RestDaySuppressionSnapshotWireV1",
    LocalStampSchemaV1.fields + listOf(
        required("source_decision_id", UuidShapeV1),
        required("expires_at_utc", InstantShapeV1),
        required("clock_integrity", ObjectShapeV1(ClockIntegrityEvidenceSchemaV1)),
        required("rule_version", Int64ShapeV1(literal = 1)),
    ),
) { value, path -> validateFlatLocalStamp(value, path) }

internal val SessionRuntimeCapSnapshotSchemaV1 = ClosedObjectSchemaV1(
    "SessionRuntimeCapSnapshotWireV1",
    listOf(
        required("applied_cap", ObjectShapeV1(DayModeCapSnapshotSchemaV1)),
        required("decision_effective_mode_before_runtime_cap", EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD"))),
        required("runtime_effective_mode_at_start", EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD"))),
    ),
) { value, path ->
    val cap = value.requiredElement("applied_cap", path).asStrictObject("$path.applied_cap")
    val before = value.requiredString("decision_effective_mode_before_runtime_cap", path)
    val runtime = value.requiredString("runtime_effective_mode_at_start", path)
    val max = cap.requiredString("max_mode", "$path.applied_cap")
    if (modeRank(runtime) != minOf(modeRank(before), modeRank(max))) fail(path, "runtime mode is not min(before, cap)")
    if (modeRank(runtime) >= modeRank(before)) fail(path, "runtime cap snapshot must describe a strict reduction")
}

internal val DayModeCapUpdateSnapshotSchemaV1 = ClosedObjectSchemaV1(
    "DayModeCapUpdateSnapshotWireV1",
    listOf(
        required("trigger_session_id", UuidShapeV1),
        required("expiry_source_session_id", UuidShapeV1),
        required("basis_mode", EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD"))),
        required("previous_max_mode", NullableShapeV1(EnumShapeV1(listOf("RECOVER", "MAINTAIN")))),
        required("resulting_cap", ObjectShapeV1(DayModeCapSnapshotSchemaV1)),
        required("deadline_source", EnumShapeV1(listOf("existing_later", "candidate_later", "same"))),
    ),
)

internal val SkippedStepSchemaV1 = ClosedObjectSchemaV1(
    "SkippedStepWireV1",
    listOf(
        required("step_id", StringShapeV1),
        required("active_elapsed_ms", NonNegativeInt64ShapeV1),
    ),
)

internal val PlayerCheckpointSchemaV1 = ClosedObjectSchemaV1(
    "PlayerCheckpointWireV1",
    listOf(
        required("substate", NullableShapeV1(EnumShapeV1(listOf("PLAYING", "PAUSED")))),
        required("phase", EnumShapeV1(listOf("STEP_TIMER", "STEP_TRANSITION", "COMPLETION_CTA_WAIT"))),
        required("step_index", NonNegativeInt64ShapeV1),
        required("current_step_remaining_ms", NonNegativeInt64ShapeV1),
        required("transition_remaining_ms", NonNegativeInt64ShapeV1),
        required("accumulated_active_ms", NonNegativeInt64ShapeV1),
        required("skipped_steps", ArrayShapeV1(ObjectShapeV1(SkippedStepSchemaV1))),
        required("segment_started_elapsed_realtime_ms", NullableShapeV1(NonNegativeInt64ShapeV1)),
        required("last_checkpoint_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
        required("boot_marker", NonNegativeInt64ShapeV1),
        required("last_announced_cadence_ordinal", NonNegativeInt64ShapeV1),
        required("content_identity", ObjectShapeV1(ContentIdentitySchemaV1)),
    ),
) { value, path ->
    val phase = value.requiredString("phase", path)
    val current = value.requiredInt64("current_step_remaining_ms", path)
    val transition = value.requiredInt64("transition_remaining_ms", path)
    val substate = value.nullableString("substate", path)
    val segment = value.nullableInt64("segment_started_elapsed_realtime_ms", path)
    when (phase) {
        "STEP_TIMER" -> {
            if (current <= 0 || transition != 0L) fail(path, "STEP_TIMER remaining matrix is invalid")
            if (substate == null) fail(path, "STEP_TIMER requires substate")
            if ((substate == "PLAYING") != (segment != null)) fail(path, "segment anchor must be present iff PLAYING")
        }
        "STEP_TRANSITION" -> {
            if (current != 0L || transition <= 0) fail(path, "STEP_TRANSITION remaining matrix is invalid")
            if (substate == null) fail(path, "STEP_TRANSITION requires substate")
            if (segment != null) fail(path, "transition must not retain active segment anchor")
        }
        "COMPLETION_CTA_WAIT" -> {
            if (current != 0L || transition != 0L || substate != null || segment != null) fail(path, "completion CTA matrix is invalid")
        }
    }
}

internal val SafetyAcknowledgementSchemaV1 = ClosedObjectSchemaV1(
    "SafetyAcknowledgementWireV1",
    listOf(
        required("acknowledgement_id", UuidShapeV1),
        required("kind", EnumShapeV1(listOf("onboarding", "reack"))),
        required("content_version", SemVerShapeV1),
        required("content_digest", DigestShapeV1),
        required("acknowledged_at", ObjectShapeV1(LocalStampSchemaV1)),
    ),
)

internal val WeeklyRateSchemaV1 = ClosedObjectSchemaV1(
    "WeeklyRateWireV1",
    listOf(
        required("numerator", NonNegativeInt64ShapeV1),
        required("denominator", NonNegativeInt64ShapeV1),
        required("value_percent", NullableShapeV1(Int64ShapeV1(0, 100))),
        required("suppression_reason", NullableShapeV1(EnumShapeV1(listOf("insufficient_sample")))),
    ),
) { value, path ->
    val numerator = value.requiredInt64("numerator", path)
    val denominator = value.requiredInt64("denominator", path)
    if (numerator > denominator) fail(path, "numerator exceeds denominator")
    val percent = value.nullableInt64("value_percent", path)
    val reason = value.nullableString("suppression_reason", path)
    if (denominator < 5) {
        if (percent != null || reason != "insufficient_sample") fail(path, "small denominator must be suppressed")
    } else {
        if (reason != null || percent == null) fail(path, "eligible rate must have integer percent and null reason")
        val expected = roundHalfUpPercent(numerator, denominator)
        if (percent != expected) fail(path, "value_percent must equal exact round-half-up result $expected")
    }
}

internal val RecordCountsSchemaV1 = ClosedObjectSchemaV1(
    "RecordCountsWireV1",
    ExportCollectionKeysV1.map { required(it, NonNegativeInt64ShapeV1) },
)

internal val MetadataSchemaV1 = ClosedObjectSchemaV1(
    "ExportMetadataWireV1",
    listOf(
        required("export_schema_version", Int64ShapeV1(literal = 1)),
        required("exported_at_utc", InstantShapeV1),
        required("app_version", SemVerShapeV1),
        required("content_version", SemVerShapeV1),
        required("rule_version", Int64ShapeV1(literal = 1)),
        required("retention_policy_version", Int64ShapeV1(literal = 1)),
        required("record_counts", ObjectShapeV1(RecordCountsSchemaV1)),
    ),
)

internal fun validateFlatLocalStamp(value: StrictJsonObjectV1, path: String) {
    LocalStampWireV1(
        InstantWireV1.parse(value.requiredString("occurred_at_utc", path)),
        DateWireV1.parse(value.requiredString("local_date", path)),
        value.requiredString("zone_id", path),
        value.requiredInt64("utc_offset_minutes", path),
    )
}

internal fun modeRank(mode: String): Int = when (mode) {
    "RECOVER" -> 0
    "MAINTAIN" -> 1
    "BUILD" -> 2
    else -> fail("mode", "unknown mode '$mode'")
}

private fun roundHalfUpPercent(numerator: Long, denominator: Long): Long {
    val n = java.math.BigInteger.valueOf(numerator)
    val d = java.math.BigInteger.valueOf(denominator)
    return n.multiply(java.math.BigInteger.valueOf(200)).add(d)
        .divide(d.multiply(java.math.BigInteger.TWO))
        .longValueExact()
}
