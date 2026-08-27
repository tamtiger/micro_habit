package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

data class ProfileWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val installationId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("installation_id", "ProfileWireV1"))
}

data class WorkScheduleWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val scheduleVersionId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("schedule_version_id", "WorkScheduleWireV1"))
}

data class CheckInWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val checkInId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("check_in_id", "CheckInWireV1"))
}

data class DecisionWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val decisionId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("decision_id", "DecisionWireV1"))
}

data class SessionWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val sessionId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("session_id", "SessionWireV1"))
}

data class FeedbackWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val sessionId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("session_id", "FeedbackWireV1"))
}

data class ReminderWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val reminderOccurrenceId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("reminder_occurrence_id", "ReminderWireV1"))
    val kind: String get() = body.requiredString("kind", "ReminderWireV1")
}

data class WeeklySummaryWireV1 internal constructor(override val body: StrictJsonObjectV1) : ClosedWireRecordV1 {
    val summaryId: UuidWireV1 get() = UuidWireV1.parse(body.requiredString("summary_id", "WeeklySummaryWireV1"))
    val weekStartLocalDate: DateWireV1 get() = DateWireV1.parse(body.requiredString("week_start_local_date", "WeeklySummaryWireV1"))
}

data class ExportDatasetWireV1(
    val metadata: ExportMetadataWireV1,
    val profile: List<ProfileWireV1>,
    val workSchedule: List<WorkScheduleWireV1>,
    val checkIns: List<CheckInWireV1>,
    val decisions: List<DecisionWireV1>,
    val sessions: List<SessionWireV1>,
    val feedback: List<FeedbackWireV1>,
    val reminders: List<ReminderWireV1>,
    val events: List<vn.nhip2phut.domain.events.ProductEventWireV1>,
    val weeklySummaries: List<WeeklySummaryWireV1>,
)

object ClosedSchemaRegistryV1 {
    val collectionKeys: List<String> = ExportCollectionKeysV1

    val rootKeys: List<String> = listOf("metadata") + collectionKeys

    val entityKeys: Map<String, List<String>> = linkedMapOf(
        "profile" to ProfileSchemaV1.keys,
        "work_schedule" to WorkScheduleSchemaV1.keys,
        "check_ins" to CheckInSchemaV1.keys,
        "decisions" to DecisionSchemaV1.keys,
        "sessions" to SessionSchemaV1.keys,
        "feedback" to FeedbackSchemaV1.keys,
        "reminders" to ReminderSchemaV1.keys,
        "weekly_summaries" to WeeklySummarySchemaV1.keys,
    )
}

internal val RootExportSchemaV1 = ClosedObjectSchemaV1(
    "ExportDatasetWireV1",
    listOf(required("metadata", ObjectShapeV1(MetadataSchemaV1))) +
        ExportCollectionKeysV1.map { required(it, ArrayShapeV1(AnyObjectShapeV1)) },
)

internal val ProfileSchemaV1 = ClosedObjectSchemaV1(
    "ProfileWireV1",
    listOf(
        required("installation_id", UuidShapeV1),
        required("adult_confirmed", BooleanShapeV1(true)),
        required("eligibility_scope_confirmed", BooleanShapeV1(true)),
        required("locale", EnumShapeV1(listOf("vi-VN"))),
        required("onboarding_completed_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("activation_boot_marker", NonNegativeInt64ShapeV1),
        required("activation_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
        required("activation_clock_generation", NonNegativeInt64ShapeV1),
        required("activation_wall_minus_elapsed_ms", Int64ShapeV1()),
        required("safety_acknowledgements", ArrayShapeV1(ObjectShapeV1(SafetyAcknowledgementSchemaV1), minimumSize = 1)),
        required("current_safety_acknowledgement_id", UuidShapeV1),
    ),
) { value, path ->
    val acknowledgements = value.requiredElement("safety_acknowledgements", path).asArray("$path.safety_acknowledgements")
    val ids = acknowledgements.mapIndexed { index, item ->
        val acknowledgement = item.asStrictObject("$path.safety_acknowledgements[$index]")
        val kind = acknowledgement.requiredString("kind", "$path.safety_acknowledgements[$index]")
        if ((index == 0 && kind != "onboarding") || (index > 0 && kind != "reack")) {
            fail(path, "acknowledgement history must start onboarding and append only reack")
        }
        acknowledgement.requiredString("acknowledgement_id", "$path.safety_acknowledgements[$index]")
    }
    if (ids.distinct().size != ids.size) fail(path, "duplicate acknowledgement_id")
    if (ids.last() != value.requiredString("current_safety_acknowledgement_id", path)) {
        fail(path, "current acknowledgement pointer must equal append-last acknowledgement")
    }
}

internal val WorkScheduleSchemaV1 = ClosedObjectSchemaV1(
    "WorkScheduleWireV1",
    listOf(
        required("schedule_version_id", UuidShapeV1),
        required("enabled", BooleanShapeV1()),
        required("selected_weekdays", ArrayShapeV1(Int64ShapeV1(1, 7), 1, 7) { array, path ->
            array.requireStrictlySortedUnique(path) { left, right -> left.asInt64(path).compareTo(right.asInt64(path)) }
        }),
        required("work_start", TimeMinuteShapeV1),
        required("work_end", TimeMinuteShapeV1),
        required("reminder_times", ArrayShapeV1(TimeMinuteShapeV1, 1, 2) { array, path ->
            array.requireStrictlySortedUnique(path) { left, right ->
                TimeMinuteWireV1.parse(left.asString(path)).compareTo(TimeMinuteWireV1.parse(right.asString(path)))
            }
        }),
        required("effective_from", ObjectShapeV1(LocalStampSchemaV1)),
        required("replaced_at", NullableShapeV1(ObjectShapeV1(LocalStampSchemaV1))),
    ),
) { value, path ->
    val start = TimeMinuteWireV1.parse(value.requiredString("work_start", path))
    val end = TimeMinuteWireV1.parse(value.requiredString("work_end", path))
    if (start >= end) fail(path, "work_end must be strictly later than work_start")
    value.requiredElement("reminder_times", path).asArray("$path.reminder_times").forEachIndexed { index, item ->
        val time = TimeMinuteWireV1.parse(item.asString("$path.reminder_times[$index]"))
        if (time < start || time >= end) fail(path, "reminder time must be inside [work_start, work_end)")
    }
    val effective = localStampInstant(value.requiredElement("effective_from", path), "$path.effective_from")
    value.requiredElement("replaced_at", path).takeUnless { it === JsonNull }?.let {
        if (localStampInstant(it, "$path.replaced_at") < effective) fail(path, "replaced_at precedes effective_from")
    }
}

private val freshnessInlineFields = DecisionFreshnessEvidenceSchemaV1.fields

internal val CheckInSchemaV1 = ClosedObjectSchemaV1(
    "CheckInWireV1",
    listOf(
        required("check_in_id", UuidShapeV1),
        required("parent_id", NullableShapeV1(UuidShapeV1)),
        required("schedule_version_id", UuidShapeV1),
        required("rule_version", Int64ShapeV1(literal = 1)),
        required("answers_kind", EnumShapeV1(listOf("red_flag_stop", "acute_stop", "full"))),
        required("red_flag", BooleanShapeV1()),
        required("acute_issue", NullableShapeV1(EnumShapeV1(listOf("none", "acute_illness", "new_or_worsening_pain_or_injury", "medically_restricted")))),
        required("energy", NullableShapeV1(EnumShapeV1(listOf("low", "okay", "good")))),
        required("stiffness", NullableShapeV1(EnumShapeV1(listOf("none", "mild", "notable")))),
        required("intent", NullableShapeV1(EnumShapeV1(listOf("rest", "gentle", "moderate")))),
        required("confirmed_at", ObjectShapeV1(LocalStampSchemaV1)),
    ) + freshnessInlineFields,
) { value, path ->
    validateInlineFreshness(value, path)
    if (value.requiredString("confirmed_zone_id", path) !=
        value.requiredElement("confirmed_at", path).asStrictObject("$path.confirmed_at").requiredString("zone_id", "$path.confirmed_at")) {
        fail(path, "confirmed_zone_id must equal confirmed_at.zone_id")
    }
    val kind = value.requiredString("answers_kind", path)
    val redFlag = value.requiredBoolean("red_flag", path)
    val acute = value.nullableString("acute_issue", path)
    val ordinaryPresent = listOf("energy", "stiffness", "intent").map { value.hasNonNull(it) }
    when (kind) {
        "red_flag_stop" -> if (!redFlag || acute != null || ordinaryPresent.any { it }) fail(path, "red_flag_stop branch mismatch")
        "acute_stop" -> if (redFlag || acute == null || acute == "none" || ordinaryPresent.any { it }) fail(path, "acute_stop branch mismatch")
        "full" -> if (redFlag || acute != "none" || ordinaryPresent.any { !it }) fail(path, "full answers branch mismatch")
    }
}

private val reasonCodeOrder = listOf(
    "SAF_LOCK_ACTIVE",
    "SAF_RED_FLAG_PRESENT",
    "SAF_INPUT_MISSING",
    "SAF_INPUT_INVALID",
    "SAF_ACUTE_ILLNESS",
    "SAF_ACUTE_NEW_OR_WORSENING_PAIN",
    "SAF_MEDICALLY_RESTRICTED",
    "SAF_INTENT_REST",
    "SAF_ENERGY_LOW",
    "SAF_STIFFNESS_NOTABLE",
    "SAF_BUILD_CONDITIONS",
    "SAF_MAINTAIN_DEFAULT",
    "SAF_DAY_MODE_CAP_APPLIED",
)
private val invalidFieldOrder = listOf("red_flag", "acute_issue", "energy", "stiffness", "intent", "day_mode_cap")

internal val DecisionSchemaV1 = ClosedObjectSchemaV1(
    "DecisionWireV1",
    listOf(
        required("decision_id", UuidShapeV1),
        required("check_in_id", UuidShapeV1),
        required("schedule_version_id", UuidShapeV1),
        required("rule_version", Int64ShapeV1(literal = 1)),
        required("outcome", EnumShapeV1(listOf("URGENT_STOP", "PAUSE_TODAY", "INCOMPLETE", "REST_ONLY", "RECOVER", "MAINTAIN", "BUILD"))),
        required("base_mode", NullableShapeV1(EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD")))),
        required("effective_mode", NullableShapeV1(EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD")))),
        required("reason_codes", ArrayShapeV1(EnumShapeV1(reasonCodeOrder)) { array, path ->
            array.requireUniqueStringsInCanonicalOrder(path, reasonCodeOrder)
        }),
        required("invalid_fields", ArrayShapeV1(EnumShapeV1(invalidFieldOrder)) { array, path ->
            array.requireUniqueStringsInCanonicalOrder(path, invalidFieldOrder)
        }),
        required("created_safety_hold_snapshot", NullableShapeV1(ObjectShapeV1(SafetyHoldSnapshotSchemaV1))),
        required("created_rest_suppression_snapshot", NullableShapeV1(ObjectShapeV1(RestDaySuppressionSnapshotSchemaV1))),
        required("evaluation_day_mode_cap_snapshot", NullableShapeV1(ObjectShapeV1(DayModeCapSnapshotSchemaV1))),
        required("created_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("reconfirm_after", InstantShapeV1),
        required("valid_until_work_end", InstantShapeV1),
    ) + freshnessInlineFields,
) { value, path ->
    validateInlineFreshness(value, path)
    if (value.requiredString("confirmed_zone_id", path) !=
        value.requiredElement("created_at", path).asStrictObject("$path.created_at").requiredString("zone_id", "$path.created_at")
    ) {
        fail(path, "confirmed_zone_id must equal created_at.zone_id")
    }
    val outcome = value.requiredString("outcome", path)
    val base = value.nullableString("base_mode", path)
    val effective = value.nullableString("effective_mode", path)
    val invalid = value.requiredElement("invalid_fields", path).asArray("$path.invalid_fields")
    if (outcome in listOf("RECOVER", "MAINTAIN", "BUILD")) {
        if (base != outcome || effective == null || modeRank(effective) > modeRank(base)) fail(path, "mode outcome matrix mismatch")
    } else if (base != null || effective != null) {
        fail(path, "non-mode outcome must have null modes")
    }
    if ((outcome == "INCOMPLETE") != invalid.isNotEmpty()) fail(path, "invalid_fields must be nonempty iff INCOMPLETE")
    if (outcome == "INCOMPLETE" && invalid.map { it.asString("$path.invalid_fields") } != listOf("day_mode_cap")) {
        fail(path, "persisted INCOMPLETE requires exact invalid_fields=[day_mode_cap]")
    }

    val safetySnapshot = value.requiredElement("created_safety_hold_snapshot", path)
    val restSnapshot = value.requiredElement("created_rest_suppression_snapshot", path)
    val capSnapshot = value.requiredElement("evaluation_day_mode_cap_snapshot", path)
    val requiresSafetySnapshot = outcome == "URGENT_STOP" || outcome == "PAUSE_TODAY"
    if ((safetySnapshot !== JsonNull) != requiresSafetySnapshot) {
        fail(path, "safety hold snapshot must be non-null iff outcome is URGENT_STOP/PAUSE_TODAY")
    }
    if ((restSnapshot !== JsonNull) != (outcome == "REST_ONLY")) {
        fail(path, "rest suppression snapshot must be non-null iff outcome is REST_ONLY")
    }

    if (safetySnapshot !== JsonNull) {
        val snapshot = safetySnapshot.asStrictObject("$path.created_safety_hold_snapshot")
        if (snapshot.requiredString("source_id", "$path.created_safety_hold_snapshot") != value.requiredString("check_in_id", path)) {
            fail(path, "safety hold snapshot source_id must equal check_in_id")
        }
        val kind = snapshot.requiredString("kind", "$path.created_safety_hold_snapshot")
        if (outcome == "URGENT_STOP" && kind != "RED_FLAG") {
            fail(path, "URGENT_STOP requires a RED_FLAG safety hold snapshot")
        }
        if (outcome == "PAUSE_TODAY" && kind !in setOf("ACUTE_ILLNESS", "NEW_OR_WORSENING_PAIN_OR_INJURY", "MEDICALLY_RESTRICTED")) {
            fail(path, "PAUSE_TODAY requires an acute safety hold snapshot")
        }
    }
    if (restSnapshot !== JsonNull) {
        val snapshot = restSnapshot.asStrictObject("$path.created_rest_suppression_snapshot")
        if (snapshot.requiredString("source_decision_id", "$path.created_rest_suppression_snapshot") != value.requiredString("decision_id", path)) {
            fail(path, "rest suppression snapshot source_decision_id must equal decision_id")
        }
    }

    val reasons = value.requiredElement("reason_codes", path).asArray("$path.reason_codes")
        .map { it.asString("$path.reason_codes") }
    val capReasonPresent = "SAF_DAY_MODE_CAP_APPLIED" in reasons
    val strictReduction = base != null && effective != null && modeRank(effective) < modeRank(base)
    if (capReasonPresent != strictReduction) fail(path, "cap reason must be present iff effective mode is strictly reduced")
    if ((capSnapshot !== JsonNull) != capReasonPresent) {
        fail(path, "evaluation cap snapshot must be non-null iff SAF_DAY_MODE_CAP_APPLIED is present")
    }
    if (capSnapshot !== JsonNull) {
        val snapshot = capSnapshot.asStrictObject("$path.evaluation_day_mode_cap_snapshot")
        val maxMode = snapshot.requiredString("max_mode", "$path.evaluation_day_mode_cap_snapshot")
        if (base == null || effective == null || modeRank(effective) != minOf(modeRank(base), modeRank(maxMode))) {
            fail(path, "effective_mode must equal min(base_mode, evaluation cap max_mode)")
        }
    }
}

private val routineIdShape = EnumShapeV1(listOf("REC-01", "REC-02", "MAI-01", "MAI-02", "BUI-01", "BUI-02"))
private val modeShape = EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD"))

internal val SessionSchemaV1 = ClosedObjectSchemaV1(
    "SessionWireV1",
    listOf(
        required("session_id", UuidShapeV1),
        required("decision_id", UuidShapeV1),
        required("schedule_version_id", UuidShapeV1),
        required("routine_id", routineIdShape),
        required("content_identity", ObjectShapeV1(ContentIdentitySchemaV1)),
        required("routine_mode", modeShape),
        required("decision_effective_mode_at_start", modeShape),
        required("runtime_effective_mode_at_start", modeShape),
        required("runtime_day_mode_cap_snapshot_at_start", NullableShapeV1(ObjectShapeV1(SessionRuntimeCapSnapshotSchemaV1))),
        required("source", EnumShapeV1(listOf("home", "reminder"))),
        required("reminder_occurrence_id", NullableShapeV1(UuidShapeV1)),
        required("is_selected_workday_at_start", BooleanShapeV1()),
        required("started_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("start_boot_marker", NonNegativeInt64ShapeV1),
        required("start_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
        required("start_clock_generation", NonNegativeInt64ShapeV1),
        required("start_wall_minus_elapsed_ms", Int64ShapeV1()),
        required("status", EnumShapeV1(listOf("ACTIVE", "COMPLETED", "STOPPED", "ABANDONED"))),
        required("player_checkpoint", ObjectShapeV1(PlayerCheckpointSchemaV1)),
        required("terminal_at", NullableShapeV1(ObjectShapeV1(LocalStampSchemaV1))),
        required("session_origin_day_expires_at_utc", NullableShapeV1(InstantShapeV1)),
        required("session_origin_clock_integrity", NullableShapeV1(ObjectShapeV1(ClockIntegrityEvidenceSchemaV1))),
        required("completion_boot_marker", NullableShapeV1(NonNegativeInt64ShapeV1)),
        required("completion_elapsed_realtime_ms", NullableShapeV1(NonNegativeInt64ShapeV1)),
        required("completion_clock_generation", NullableShapeV1(NonNegativeInt64ShapeV1)),
        required("completion_wall_minus_elapsed_ms", NullableShapeV1(Int64ShapeV1())),
    ),
) { value, path ->
    val source = value.requiredString("source", path)
    if ((source == "reminder") != value.hasNonNull("reminder_occurrence_id")) fail(path, "reminder occurrence must be non-null iff source=reminder")
    val decisionMode = value.requiredString("decision_effective_mode_at_start", path)
    val runtimeMode = value.requiredString("runtime_effective_mode_at_start", path)
    val routineMode = value.requiredString("routine_mode", path)
    RoutineModeCatalogV1.requireMode(
        routineId = value.requiredString("routine_id", path),
        mode = routineMode,
        path = path,
        modeName = "routine_mode",
    )
    if (modeRank(routineMode) > modeRank(runtimeMode) || modeRank(runtimeMode) > modeRank(decisionMode)) fail(path, "session mode ceiling invariant failed")
    val runtimeSnapshot = value.requiredElement("runtime_day_mode_cap_snapshot_at_start", path)
    if (runtimeSnapshot === JsonNull && runtimeMode != decisionMode) fail(path, "runtime mode must equal decision mode without cap snapshot")
    if (runtimeSnapshot !== JsonNull) {
        val snapshot = runtimeSnapshot.asStrictObject("$path.runtime_day_mode_cap_snapshot_at_start")
        if (snapshot.requiredString("decision_effective_mode_before_runtime_cap", "$path.runtime_day_mode_cap_snapshot_at_start") != decisionMode ||
            snapshot.requiredString("runtime_effective_mode_at_start", "$path.runtime_day_mode_cap_snapshot_at_start") != runtimeMode
        ) fail(path, "runtime cap snapshot does not mirror session modes")
    }
    val terminalKeys = listOf(
        "terminal_at",
        "session_origin_day_expires_at_utc",
        "session_origin_clock_integrity",
        "completion_boot_marker",
        "completion_elapsed_realtime_ms",
        "completion_clock_generation",
        "completion_wall_minus_elapsed_ms",
    )
    if (value.requiredString("status", path) == "ACTIVE") {
        if (terminalKeys.any { value.hasNonNull(it) }) fail(path, "ACTIVE session must not contain terminal evidence")
    } else if (terminalKeys.any { !value.hasNonNull(it) }) {
        fail(path, "terminal session requires all terminal/origin/completion evidence")
    }
    val checkpoint = value.requiredElement("player_checkpoint", path).asStrictObject("$path.player_checkpoint")
    if (checkpoint.requiredElement("content_identity", "$path.player_checkpoint") != value.requiredElement("content_identity", path)) {
        fail(path, "player content identity must mirror session content identity")
    }
}

internal val FeedbackSchemaV1 = ClosedObjectSchemaV1(
    "FeedbackWireV1",
    listOf(
        required("session_id", UuidShapeV1),
        required("pain_gate_status", EnumShapeV1(listOf("pending", "resolved_no", "resolved_hold"))),
        required("new_or_worse_pain", NullableShapeV1(EnumShapeV1(listOf("yes", "no")))),
        required("pain_answered_at", NullableShapeV1(ObjectShapeV1(LocalStampSchemaV1))),
        required("effort", NullableShapeV1(EnumShapeV1(listOf("easy", "moderate", "too_hard")))),
        required("context_fit", NullableShapeV1(EnumShapeV1(listOf("yes", "no")))),
        required("created_post_session_safety_hold_snapshot", NullableShapeV1(ObjectShapeV1(SafetyHoldSnapshotSchemaV1))),
        required("day_mode_cap_update_snapshot", NullableShapeV1(ObjectShapeV1(DayModeCapUpdateSnapshotSchemaV1))),
        required("updated_at", ObjectShapeV1(LocalStampSchemaV1)),
    ),
) { value, path ->
    when (value.requiredString("pain_gate_status", path)) {
        "pending" -> if (listOf("new_or_worse_pain", "pain_answered_at", "effort", "context_fit", "created_post_session_safety_hold_snapshot", "day_mode_cap_update_snapshot").any { value.hasNonNull(it) }) {
            fail(path, "pending feedback matrix mismatch")
        }
        "resolved_no" -> {
            if (value.nullableString("new_or_worse_pain", path) != "no" || !value.hasNonNull("pain_answered_at") || value.hasNonNull("created_post_session_safety_hold_snapshot")) fail(path, "resolved_no feedback matrix mismatch")
            if (value.hasNonNull("day_mode_cap_update_snapshot") && value.nullableString("effort", path) != "too_hard") fail(path, "cap update requires too_hard")
        }
        "resolved_hold" -> if (value.nullableString("new_or_worse_pain", path) != "yes" || !value.hasNonNull("pain_answered_at") || !value.hasNonNull("created_post_session_safety_hold_snapshot") || value.hasNonNull("day_mode_cap_update_snapshot")) {
            fail(path, "resolved_hold feedback matrix mismatch")
        }
    }
}

private val reminderStatuses = listOf(
    "SCHEDULED", "DELIVERED", "SNOOZED", "MERGED", "CANCELLED", "BLOCKED_PERMISSION",
    "SKIPPED_LATE", "SKIPPED_WORK_END", "SKIPPED_SAFETY_HOLD", "SKIPPED_REST",
    "SKIPPED_SESSION_GUARD", "SKIPPED_NOT_SELECTED_WORKDAY",
)

internal val ReminderSchemaV1 = ClosedObjectSchemaV1(
    "ReminderWireV1",
    listOf(
        required("reminder_occurrence_id", UuidShapeV1),
        required("schedule_version_id", UuidShapeV1),
        required("kind", EnumShapeV1(listOf("fixed", "snooze"))),
        optional("slot_index", Int64ShapeV1(0, 1)),
        optional("local_date", DateShapeV1),
        optional("generation", NonNegativeInt64ShapeV1),
        optional("creation_reason", EnumShapeV1(listOf("initial", "slot_reeligible"))),
        optional("parent_occurrence_id", UuidShapeV1),
        optional("ordinal", Int64ShapeV1(literal = 0)),
        required("supersedes_occurrence_id", NullableShapeV1(UuidShapeV1)),
        required("merged_into_occurrence_id", NullableShapeV1(UuidShapeV1)),
        required("is_selected_workday_at_due", BooleanShapeV1(true)),
        required("due_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("delivered_at", NullableShapeV1(ObjectShapeV1(LocalStampSchemaV1))),
        required("first_opened_at", NullableShapeV1(ObjectShapeV1(LocalStampSchemaV1))),
        required("dismissed_at", NullableShapeV1(ObjectShapeV1(LocalStampSchemaV1))),
        required("status", EnumShapeV1(reminderStatuses)),
    ),
) { value, path ->
    val fixedKeys = listOf("slot_index", "local_date", "generation", "creation_reason")
    val snoozeKeys = listOf("parent_occurrence_id", "ordinal")
    when (value.requiredString("kind", path)) {
        "fixed" -> {
            if (fixedKeys.any { !value.hasNonNull(it) } || snoozeKeys.any { value.hasKey(it) }) fail(path, "fixed reminder branch key mismatch")
            val generation = value.requiredInt64("generation", path)
            val reason = value.requiredString("creation_reason", path)
            if ((generation == 0L) != (reason == "initial")) fail(path, "generation 0 iff creation_reason=initial")
            if ((generation == 0L) != value.isNull("supersedes_occurrence_id")) fail(path, "fixed supersedes matrix mismatch")
        }
        "snooze" -> {
            if (snoozeKeys.any { !value.hasNonNull(it) } || fixedKeys.any { value.hasKey(it) } || !value.isNull("supersedes_occurrence_id")) fail(path, "snooze reminder branch key mismatch")
        }
    }
    val status = value.requiredString("status", path)
    if ((status == "MERGED") != value.hasNonNull("merged_into_occurrence_id")) fail(path, "merged_into occurrence must be non-null iff MERGED")
    if ((status == "DELIVERED") != value.hasNonNull("delivered_at")) fail(path, "delivered_at must be non-null iff DELIVERED")
    if (status != "DELIVERED" && (value.hasNonNull("first_opened_at") || value.hasNonNull("dismissed_at"))) fail(path, "interaction stamps require DELIVERED")
    value.requiredElement("delivered_at", path).takeUnless { it === JsonNull }?.let { delivered ->
        val deliveredInstant = localStampInstant(delivered, "$path.delivered_at")
        listOf("first_opened_at", "dismissed_at").forEach { key ->
            value.requiredElement(key, path).takeUnless { it === JsonNull }?.let { if (localStampInstant(it, "$path.$key") < deliveredInstant) fail(path, "$key precedes delivered_at") }
        }
    }
}

private val weeklyCountKeys = listOf(
    "qualified_break_days",
    "started_count",
    "completed_count",
    "effort_easy_count",
    "effort_moderate_count",
    "effort_too_hard_count",
    "pain_yes_count",
    "pain_no_count",
    "context_yes_count",
    "context_no_count",
    "reminder_opened_count",
    "reminder_snoozed_count",
    "reminder_dismissed_count",
)

internal val WeeklySummarySchemaV1 = ClosedObjectSchemaV1(
    "WeeklySummaryWireV1",
    listOf(
        required("summary_id", UuidShapeV1),
        required("week_start_local_date", DateShapeV1),
        required("week_zone_id", ZoneIdShapeV1),
    ) + LocalStampSchemaV1.fields + weeklyCountKeys.map { required(it, NonNegativeInt64ShapeV1) } + listOf(
        required("completion_rate", ObjectShapeV1(WeeklyRateSchemaV1)),
        required("context_fit_rate", ObjectShapeV1(WeeklyRateSchemaV1)),
        required("new_or_worse_pain_rate", ObjectShapeV1(WeeklyRateSchemaV1)),
    ),
) { value, path ->
    validateFlatLocalStamp(value, path)
    DateWireV1.parse(value.requiredString("week_start_local_date", path)).requireMonday("$path.week_start_local_date")
    requireRateMirrors(
        value = value,
        path = path,
        rateKey = "completion_rate",
        expectedNumerator = value.requiredInt64("completed_count", path),
        expectedDenominator = value.requiredInt64("started_count", path),
    )
    requireRateMirrors(
        value = value,
        path = path,
        rateKey = "new_or_worse_pain_rate",
        expectedNumerator = value.requiredInt64("pain_yes_count", path),
        expectedDenominator = checkedCountSum(
            value.requiredInt64("pain_yes_count", path),
            value.requiredInt64("pain_no_count", path),
            "$path.new_or_worse_pain_rate",
        ),
    )
}

private fun requireRateMirrors(
    value: StrictJsonObjectV1,
    path: String,
    rateKey: String,
    expectedNumerator: Long,
    expectedDenominator: Long,
) {
    val ratePath = "$path.$rateKey"
    val rate = value.requiredElement(rateKey, path).asStrictObject(ratePath)
    if (rate.requiredInt64("numerator", ratePath) != expectedNumerator ||
        rate.requiredInt64("denominator", ratePath) != expectedDenominator
    ) {
        fail(path, "$rateKey must mirror its visible count buckets")
    }
}

private fun checkedCountSum(left: Long, right: Long, path: String): Long = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    fail(path, "count bucket sum overflows int64")
}

private fun validateInlineFreshness(value: StrictJsonObjectV1, path: String) {
    val confirmed = value.requiredInt64("confirmed_elapsed_realtime_ms", path)
    if (value.requiredInt64("ttl_monotonic_deadline_ms", path) < confirmed) fail(path, "TTL monotonic deadline precedes confirmation")
    try {
        java.time.ZoneId.of(value.requiredString("confirmed_zone_id", path))
    } catch (_: Exception) {
        fail(path, "confirmed_zone_id is not a valid IANA ZoneId")
    }
}

private fun localStampInstant(value: JsonElement, path: String): InstantWireV1 {
    val objectValue = value.asStrictObject(path)
    LocalStampSchemaV1.validateAndOrder(objectValue, path)
    return InstantWireV1.parse(objectValue.requiredString("occurred_at_utc", path))
}
