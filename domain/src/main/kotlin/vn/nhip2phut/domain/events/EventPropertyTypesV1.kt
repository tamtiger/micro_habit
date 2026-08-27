package vn.nhip2phut.domain.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vn.nhip2phut.domain.wire.v1.*

interface EventWireTokenV1 { val wire: String }

sealed interface EventTimingV1 {
    data class Duration(val milliseconds: Long) : EventTimingV1
    data class Invalid(val reason: TimingInvalidReasonV1) : EventTimingV1
}

sealed interface ReminderScheduleBranchV1 {
    data class Fixed(
        val logicalKey: LogicalFixedKeyV1,
        val generation: Long,
        val creationReason: ReminderCreationReasonV1,
        val supersedesOccurrenceId: UuidWireV1?,
    ) : ReminderScheduleBranchV1

    data class Snooze(val parentOccurrenceId: UuidWireV1, val ordinal: Long = 0) : ReminderScheduleBranchV1
}

sealed interface SafetyHoldSourceV1 {
    data class CheckIn(val sourceId: UuidWireV1) : SafetyHoldSourceV1
    data object Session : SafetyHoldSourceV1
}

enum class ScopeReackTriggerV1(override val wire: String) : EventWireTokenV1 { HOME("home"), NOTIFICATION("notification"), CHECK_IN("check_in"), ROUTINE_START("routine_start") }
enum class NotificationPromptTriggerV1(override val wire: String) : EventWireTokenV1 { AUTOMATIC_ONBOARDING("automatic_onboarding"), EXPLICIT_USER_RETRY("explicit_user_retry") }
enum class PermissionStateV1(override val wire: String) : EventWireTokenV1 { GRANTED("granted"), DENIED("denied"), UNAVAILABLE("unavailable") }
enum class PermissionUpdateSourceV1(override val wire: String) : EventWireTokenV1 { SYSTEM_PROMPT("system_prompt"), SETTINGS("settings"), RESUME_CHECK("resume_check") }
enum class PromptResultV1(override val wire: String) : EventWireTokenV1 { GRANTED("granted"), NOT_GRANTED("not_granted") }
enum class TimingInvalidReasonV1(override val wire: String) : EventWireTokenV1 { SAME_BOOT_UNAVAILABLE("same_boot_unavailable"), ELAPSED_ROLLBACK("elapsed_rollback"), OVERFLOW("overflow"), BACKGROUND_OVER_10M("background_over_10m") }
enum class CheckInKindV1(override val wire: String) : EventWireTokenV1 { NEW("new"), RECONFIRM("reconfirm") }
enum class AnswersKindV1(override val wire: String) : EventWireTokenV1 { RED_FLAG_STOP("red_flag_stop"), ACUTE_STOP("acute_stop"), FULL("full") }
enum class ReconfirmReasonV1(override val wire: String) : EventWireTokenV1 { SCHEDULE_CHANGED("schedule_changed"), TTL("ttl"), LOCAL_DATE_CHANGED("local_date_changed"), TIMEZONE_OR_TIME_CHANGE("timezone_or_time_change"), CLOCK_UNKNOWN("clock_unknown") }
enum class EntryTriggerV1(override val wire: String) : EventWireTokenV1 { HOME("home"), NOTIFICATION("notification"), ROUTINE_START("routine_start") }
enum class RestReplacementResultV1(override val wire: String) : EventWireTokenV1 { MODE("mode"), REST("rest"), SAFETY("safety") }
enum class ScheduleChangeSourceV1(override val wire: String) : EventWireTokenV1 { ONBOARDING("onboarding"), SETTINGS("settings") }
enum class ScheduleReconcileReasonV1(override val wire: String) : EventWireTokenV1 {
    SCHEDULE_EDIT("schedule_edit"), BOOT("boot"), TIMEZONE_CHANGE("timezone_change"), APP_UPDATE("app_update"), PERMISSION_CHANGE("permission_change"), SAFETY_HOLD("safety_hold"), REST_ONLY("rest_only"), FRESH_CHECK_IN_AFTER_REST("fresh_check_in_after_rest"), ACTIVE_SESSION("active_session"), PENDING_PAIN("pending_pain"), PAIN_RESOLVED_NO("pain_resolved_no")
}
enum class RuleResultV1(override val wire: String) : EventWireTokenV1 { URGENT_STOP("URGENT_STOP"), PAUSE_TODAY("PAUSE_TODAY"), INCOMPLETE("INCOMPLETE"), REST_ONLY("REST_ONLY"), RECOVER("RECOVER"), MAINTAIN("MAINTAIN"), BUILD("BUILD") }
enum class ModeV1(override val wire: String) : EventWireTokenV1 { RECOVER("RECOVER"), MAINTAIN("MAINTAIN"), BUILD("BUILD") }
enum class ReasonCodeV1(override val wire: String) : EventWireTokenV1 {
    SAF_LOCK_ACTIVE("SAF_LOCK_ACTIVE"), SAF_RED_FLAG_PRESENT("SAF_RED_FLAG_PRESENT"), SAF_INPUT_MISSING("SAF_INPUT_MISSING"), SAF_INPUT_INVALID("SAF_INPUT_INVALID"), SAF_ACUTE_ILLNESS("SAF_ACUTE_ILLNESS"), SAF_ACUTE_NEW_OR_WORSENING_PAIN("SAF_ACUTE_NEW_OR_WORSENING_PAIN"), SAF_MEDICALLY_RESTRICTED("SAF_MEDICALLY_RESTRICTED"), SAF_INTENT_REST("SAF_INTENT_REST"), SAF_ENERGY_LOW("SAF_ENERGY_LOW"), SAF_STIFFNESS_NOTABLE("SAF_STIFFNESS_NOTABLE"), SAF_BUILD_CONDITIONS("SAF_BUILD_CONDITIONS"), SAF_MAINTAIN_DEFAULT("SAF_MAINTAIN_DEFAULT"), SAF_DAY_MODE_CAP_APPLIED("SAF_DAY_MODE_CAP_APPLIED")
}
enum class InvalidFieldV1(override val wire: String) : EventWireTokenV1 { RED_FLAG("red_flag"), ACUTE_ISSUE("acute_issue"), ENERGY("energy"), STIFFNESS("stiffness"), INTENT("intent"), DAY_MODE_CAP("day_mode_cap") }
enum class SafetyScreenResultV1(override val wire: String) : EventWireTokenV1 { URGENT_STOP("URGENT_STOP"), PAUSE_TODAY("PAUSE_TODAY"), BLOCKED_FOR_TODAY("BLOCKED_FOR_TODAY") }
enum class SafetyRouteV1(override val wire: String) : EventWireTokenV1 {
    URGENT_STOP("urgent_stop"), PAUSE_ACUTE_ILLNESS("pause_acute_illness"), PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY("pause_new_or_worsening_pain_or_injury"), PAUSE_MEDICALLY_RESTRICTED("pause_medically_restricted"), BLOCKED_RED_FLAG("blocked_red_flag"), BLOCKED_ACUTE_ILLNESS("blocked_acute_illness"), BLOCKED_NEW_OR_WORSENING_PAIN_OR_INJURY("blocked_new_or_worsening_pain_or_injury"), BLOCKED_MEDICALLY_RESTRICTED("blocked_medically_restricted"), BLOCKED_POST_SESSION_NEW_OR_WORSE_PAIN("blocked_post_session_new_or_worse_pain")
}
enum class RoutineIdV1(override val wire: String) : EventWireTokenV1 {
    REC_01("REC-01"), REC_02("REC-02"), MAI_01("MAI-01"), MAI_02("MAI-02"), BUI_01("BUI-01"), BUI_02("BUI-02")
}
enum class SafetyHoldKindV1(override val wire: String) : EventWireTokenV1 { RED_FLAG("RED_FLAG"), ACUTE_ILLNESS("ACUTE_ILLNESS"), NEW_OR_WORSENING_PAIN_OR_INJURY("NEW_OR_WORSENING_PAIN_OR_INJURY"), MEDICALLY_RESTRICTED("MEDICALLY_RESTRICTED"), POST_SESSION_NEW_OR_WORSE_PAIN("POST_SESSION_NEW_OR_WORSE_PAIN") }
enum class ConstraintSourceTypeV1(override val wire: String) : EventWireTokenV1 { CHECK_IN("check_in"), SESSION("session") }
enum class RoutineSelectionV1(override val wire: String) : EventWireTokenV1 { RECOMMENDED("recommended"), SAME_MODE("same_mode"), LIGHTER_MODE("lighter_mode") }
enum class StartGateV1(override val wire: String) : EventWireTokenV1 { SAFETY_LOCKED("SAFETY_LOCKED"), PENDING_SAFETY_FEEDBACK("PENDING_SAFETY_FEEDBACK"), SESSION_ALREADY_ACTIVE("SESSION_ALREADY_ACTIVE"), SCOPE_REACK_REQUIRED("SCOPE_REACK_REQUIRED"), RECONFIRM_REQUIRED("RECONFIRM_REQUIRED"), EXPIRED("EXPIRED"), OUTCOME_HAS_NO_ROUTINE("OUTCOME_HAS_NO_ROUTINE"), MODE_NOT_ALLOWED("MODE_NOT_ALLOWED"), CONTRACT_ERROR("CONTRACT_ERROR") }
enum class RecoveryReasonV1(override val wire: String) : EventWireTokenV1 { REBOOT_OR_CLOCK_DISCONTINUITY("reboot_or_clock_discontinuity"), WORK_WINDOW_OR_DATE_EXPIRED("work_window_or_date_expired"), CONTENT_UNAVAILABLE_OR_IDENTITY_MISMATCH("content_unavailable_or_identity_mismatch") }
enum class PainGateStatusV1(override val wire: String) : EventWireTokenV1 { PENDING("PENDING"), RESOLVED_NO("RESOLVED_NO"), RESOLVED_HOLD("RESOLVED_HOLD") }
enum class TerminalStateV1(override val wire: String) : EventWireTokenV1 { COMPLETED("completed"), STOPPED("stopped"), ABANDONED("abandoned") }
enum class PainAnswerV1(override val wire: String) : EventWireTokenV1 { YES("yes"), NO("no") }
enum class UpdatedFieldV1(override val wire: String) : EventWireTokenV1 { EFFORT("effort"), CONTEXT_FIT("context_fit") }
enum class EffortV1(override val wire: String) : EventWireTokenV1 { EASY("easy"), MODERATE("moderate"), TOO_HARD("too_hard") }
enum class ContextFitV1(override val wire: String) : EventWireTokenV1 { YES("yes"), NO("no") }
enum class CapResultV1(override val wire: String) : EventWireTokenV1 { APPLIED("applied"), NOT_TOO_HARD("not_too_hard"), PAIN_NOT_NO("pain_not_no"), ORIGIN_DAY_EXPIRED("origin_day_expired"), NO_EFFORT_TRANSITION("no_effort_transition") }
enum class DeadlineSourceV1(override val wire: String) : EventWireTokenV1 { EXISTING_LATER("existing_later"), CANDIDATE_LATER("candidate_later"), SAME("same") }
enum class ReminderKindV1(override val wire: String) : EventWireTokenV1 { FIXED("fixed"), SNOOZE("snooze") }
enum class OpenSurfaceV1(override val wire: String) : EventWireTokenV1 { NOTIFICATION_BODY("notification_body"), START_ACTION("start_action") }
enum class MergeTieBreakV1(override val wire: String) : EventWireTokenV1 { EARLIER_DUE("earlier_due"), SNOOZE_OVER_FIXED("snooze_over_fixed") }
enum class ReminderCancelReasonV1(override val wire: String) : EventWireTokenV1 { SCHEDULE_EDIT("schedule_edit"), PERMISSION_REVOKED("permission_revoked"), TIMEZONE_CHANGE("timezone_change"), SAFETY_HOLD("safety_hold"), REST_ONLY("rest_only"), ACTIVE_SESSION("active_session"), PENDING_PAIN("pending_pain") }
enum class ReminderResultStatusV1(override val wire: String) : EventWireTokenV1 { CANCELLED("CANCELLED"), BLOCKED_PERMISSION("BLOCKED_PERMISSION") }
enum class ReminderSkippedStatusV1(override val wire: String) : EventWireTokenV1 { SKIPPED_LATE("SKIPPED_LATE"), SKIPPED_WORK_END("SKIPPED_WORK_END"), SKIPPED_SAFETY_HOLD("SKIPPED_SAFETY_HOLD"), SKIPPED_REST("SKIPPED_REST"), SKIPPED_SESSION_GUARD("SKIPPED_SESSION_GUARD"), SKIPPED_NOT_SELECTED_WORKDAY("SKIPPED_NOT_SELECTED_WORKDAY") }
enum class ReminderCreationReasonV1(override val wire: String) : EventWireTokenV1 { INITIAL("initial"), SLOT_REELIGIBLE("slot_reeligible") }
enum class ExportFailureCodeV1(override val wire: String) : EventWireTokenV1 { SNAPSHOT_READ_FAILED("snapshot_read_failed"), JSON_ENCODE_FAILED("json_encode_failed"), DESTINATION_OPEN_FAILED("destination_open_failed"), DESTINATION_WRITE_FAILED("destination_write_failed"), DESTINATION_FLUSH_FAILED("destination_flush_failed"), DESTINATION_CLOSE_FAILED("destination_close_failed"), PROVIDER_FAILED("provider_failed"), SECURITY_DENIED("security_denied") }

data class EventClockIntegrityEvidenceV1(
    val originBootMarker: Long,
    val createdElapsedRealtimeMs: Long,
    val monotonicDeadlineMs: Long,
    val remainingElapsedMsAtLastCheckpoint: Long,
    val originalDurationMs: Long,
) {
    internal fun toJson() = eventObject(
        "origin_boot_marker" to json(originBootMarker),
        "created_elapsed_realtime_ms" to json(createdElapsedRealtimeMs),
        "monotonic_deadline_ms" to json(monotonicDeadlineMs),
        "remaining_elapsed_ms_at_last_checkpoint" to json(remainingElapsedMsAtLastCheckpoint),
        "original_duration_ms" to json(originalDurationMs),
    )
    companion object { internal fun from(body: StrictJsonObjectV1) = EventClockIntegrityEvidenceV1(body.requiredInt64("origin_boot_marker", "clock"), body.requiredInt64("created_elapsed_realtime_ms", "clock"), body.requiredInt64("monotonic_deadline_ms", "clock"), body.requiredInt64("remaining_elapsed_ms_at_last_checkpoint", "clock"), body.requiredInt64("original_duration_ms", "clock")) }
}

data class EventDayModeCapSnapshotV1(
    val occurred: LocalStampWireV1,
    val maxMode: ModeV1,
    val modeTriggerSessionId: UuidWireV1,
    val sourceSessionId: UuidWireV1,
    val expiresAtUtc: InstantWireV1,
    val clockIntegrity: EventClockIntegrityEvidenceV1,
    val ruleVersion: Long = 1,
) {
    internal fun toJson() = eventObject(*flatStamp(occurred), "max_mode" to json(maxMode), "mode_trigger_session_id" to json(modeTriggerSessionId), "source_session_id" to json(sourceSessionId), "expires_at_utc" to json(expiresAtUtc), "clock_integrity" to clockIntegrity.toJson().element, "rule_version" to json(ruleVersion))
    companion object { internal fun from(body: StrictJsonObjectV1) = EventDayModeCapSnapshotV1(flatStampFrom(body), enum(body, "max_mode", ModeV1.entries), UuidWireV1.parse(body.requiredString("mode_trigger_session_id", "cap")), UuidWireV1.parse(body.requiredString("source_session_id", "cap")), InstantWireV1.parse(body.requiredString("expires_at_utc", "cap")), EventClockIntegrityEvidenceV1.from(body.requiredElement("clock_integrity", "cap").asStrictObject("cap.clock_integrity")), body.requiredInt64("rule_version", "cap")) }
}

data class LogicalFixedKeyV1(val scheduleVersionId: UuidWireV1, val slotIndex: Long, val localDate: DateWireV1) {
    internal fun toJson() = eventObject("schedule_version_id" to json(scheduleVersionId), "slot_index" to json(slotIndex), "local_date" to json(localDate), "kind" to json("fixed"))
    companion object { internal fun from(body: StrictJsonObjectV1) = LogicalFixedKeyV1(UuidWireV1.parse(body.requiredString("schedule_version_id", "logical_fixed_key")), body.requiredInt64("slot_index", "logical_fixed_key"), DateWireV1.parse(body.requiredString("local_date", "logical_fixed_key"))) }
}

internal fun eventObject(vararg fields: Pair<String, JsonElement>): StrictJsonObjectV1 = StrictJsonObjectV1(JsonObject(linkedMapOf(*fields)))
internal fun json(value: String): JsonElement = JsonPrimitive(value)
internal fun json(value: Long): JsonElement = JsonPrimitive(value)
internal fun json(value: Boolean): JsonElement = JsonPrimitive(value)
internal fun json(value: UuidWireV1): JsonElement = JsonPrimitive(value.value)
internal fun json(value: InstantWireV1): JsonElement = JsonPrimitive(value.value)
internal fun json(value: DateWireV1): JsonElement = JsonPrimitive(value.value)
internal fun json(value: TimeMinuteWireV1): JsonElement = JsonPrimitive(value.value)
internal fun json(value: SemVerWireV1): JsonElement = JsonPrimitive(value.value)
internal fun json(value: Sha256DigestWireV1): JsonElement = JsonPrimitive(value.value)
internal fun json(value: EventWireTokenV1): JsonElement = JsonPrimitive(value.wire)
internal fun jsonNullable(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> json(value)
    is Long -> json(value)
    is Boolean -> json(value)
    is UuidWireV1 -> json(value)
    is EventWireTokenV1 -> json(value)
    is EventDayModeCapSnapshotV1 -> value.toJson().element
    else -> throw IllegalArgumentException("Unsupported typed event value: ${value::class.simpleName}")
}
internal fun jsonArray(values: List<EventWireTokenV1>): JsonElement = JsonArray(values.map(::json))
internal fun flatStamp(stamp: LocalStampWireV1): Array<Pair<String, JsonElement>> = arrayOf("occurred_at_utc" to json(stamp.occurredAtUtc), "local_date" to json(stamp.localDate), "zone_id" to json(stamp.zoneId), "utc_offset_minutes" to json(stamp.utcOffsetMinutes))
internal fun flatStampFrom(body: StrictJsonObjectV1) = LocalStampWireV1(InstantWireV1.parse(body.requiredString("occurred_at_utc", "stamp")), DateWireV1.parse(body.requiredString("local_date", "stamp")), body.requiredString("zone_id", "stamp"), body.requiredInt64("utc_offset_minutes", "stamp"))
internal fun nestedStamp(body: StrictJsonObjectV1, key: String) = LocalStampWireV1.fromObject(body.requiredElement(key, "properties").asStrictObject("properties.$key"), "properties.$key")
internal fun <E> enum(body: StrictJsonObjectV1, key: String, values: List<E>): E where E : Enum<E>, E : EventWireTokenV1 = values.firstOrNull { it.wire == body.requiredString(key, "properties") } ?: throw WireContractException("properties.$key: unknown enum")
internal fun <E> enumNullable(body: StrictJsonObjectV1, key: String, values: List<E>): E? where E : Enum<E>, E : EventWireTokenV1 = body.nullableString(key, "properties")?.let { token -> values.firstOrNull { it.wire == token } ?: throw WireContractException("properties.$key: unknown enum") }
