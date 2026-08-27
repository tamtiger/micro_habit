package vn.nhip2phut.domain.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import vn.nhip2phut.domain.wire.v1.*

/** Public, typed construction surface for every closed DEL-01 event property record. */
fun AppFirstOpenedPropertiesV1(firstOpenId: UuidWireV1) =
    AppFirstOpenedPropertiesV1(eventObject("first_open_id" to json(firstOpenId)))

fun OnboardingStartedPropertiesV1(timingStartBootMarker: Long, timingStartElapsedRealtimeMs: Long) =
    OnboardingStartedPropertiesV1(eventObject("timing_start_boot_marker" to json(timingStartBootMarker), "timing_start_elapsed_realtime_ms" to json(timingStartElapsedRealtimeMs)))

fun AgeGateAnsweredPropertiesV1(eligible18Plus: Boolean = true) =
    AgeGateAnsweredPropertiesV1(eventObject("eligible_18_plus" to json(eligible18Plus)))

fun ScopeAcknowledgedPropertiesV1(
    acknowledgementId: UuidWireV1,
    eligibilityConfirmed: Boolean = true,
    contentVersion: SemVerWireV1,
    contentDigest: Sha256DigestWireV1,
) = ScopeAcknowledgedPropertiesV1(eventObject(
    "acknowledgement_id" to json(acknowledgementId), "kind" to json("onboarding"),
    "eligibility_confirmed" to json(eligibilityConfirmed), "content_version" to json(contentVersion), "content_digest" to json(contentDigest),
))

fun ScopeReackRequiredPropertiesV1(
    currentAcknowledgementId: UuidWireV1,
    previousContentVersion: SemVerWireV1,
    previousContentDigest: Sha256DigestWireV1,
    requiredContentVersion: SemVerWireV1,
    requiredContentDigest: Sha256DigestWireV1,
    trigger: ScopeReackTriggerV1,
) = ScopeReackRequiredPropertiesV1(eventObject(
    "current_acknowledgement_id" to json(currentAcknowledgementId), "previous_content_version" to json(previousContentVersion),
    "previous_content_digest" to json(previousContentDigest), "required_content_version" to json(requiredContentVersion),
    "required_content_digest" to json(requiredContentDigest), "trigger" to json(trigger),
))

fun ScopeReackCompletedPropertiesV1(
    acknowledgementId: UuidWireV1,
    supersedesAcknowledgementId: UuidWireV1,
    contentVersion: SemVerWireV1,
    contentDigest: Sha256DigestWireV1,
) = ScopeReackCompletedPropertiesV1(eventObject(
    "acknowledgement_id" to json(acknowledgementId), "supersedes_acknowledgement_id" to json(supersedesAcknowledgementId),
    "content_version" to json(contentVersion), "content_digest" to json(contentDigest),
))

fun NotificationPermissionPromptedPropertiesV1(attemptId: UuidWireV1, trigger: NotificationPromptTriggerV1) =
    NotificationPermissionPromptedPropertiesV1(eventObject("attempt_id" to json(attemptId), "trigger" to json(trigger)))

fun NotificationPermissionUpdatedPropertiesV1(
    state: PermissionStateV1,
    source: PermissionUpdateSourceV1,
    attemptId: UuidWireV1? = null,
    promptResult: PromptResultV1? = null,
) = NotificationPermissionUpdatedPropertiesV1(eventObject(
    "state" to json(state), "source" to json(source), "attempt_id" to jsonNullable(attemptId), "prompt_result" to jsonNullable(promptResult),
))

fun OnboardingCompletedPropertiesV1(
    timing: EventTimingV1,
    activationBootMarker: Long,
    activationElapsedRealtimeMs: Long,
    activationClockGeneration: Long,
    activationWallMinusElapsedMs: Long,
) = OnboardingCompletedPropertiesV1(typedObject(
    timingFields(timing, "duration_ms", "timing_invalid_reason") + listOf(
        "activation_boot_marker" to json(activationBootMarker), "activation_elapsed_realtime_ms" to json(activationElapsedRealtimeMs),
        "activation_clock_generation" to json(activationClockGeneration), "activation_wall_minus_elapsed_ms" to json(activationWallMinusElapsedMs),
    ),
))

fun CheckInStartedPropertiesV1(checkInFlowId: UuidWireV1, kind: CheckInKindV1, timingStartBootMarker: Long, timingStartElapsedRealtimeMs: Long) =
    CheckInStartedPropertiesV1(eventObject(
        "check_in_flow_id" to json(checkInFlowId), "kind" to json(kind), "timing_start_boot_marker" to json(timingStartBootMarker),
        "timing_start_elapsed_realtime_ms" to json(timingStartElapsedRealtimeMs),
    ))

fun CheckInReconfirmationRequiredPropertiesV1(checkInId: UuidWireV1, ageMs: Long?, reason: ReconfirmReasonV1, trigger: EntryTriggerV1) =
    CheckInReconfirmationRequiredPropertiesV1(eventObject(
        "check_in_id" to json(checkInId), "age_ms" to jsonNullable(ageMs), "reason" to json(reason), "trigger" to json(trigger),
    ))

fun RestSuppressionSupersededPropertiesV1(sourceDecisionId: UuidWireV1, newCheckInId: UuidWireV1, newResult: RestReplacementResultV1, futureFixedSlotsRescheduled: Long) =
    RestSuppressionSupersededPropertiesV1(eventObject(
        "source_decision_id" to json(sourceDecisionId), "new_check_in_id" to json(newCheckInId), "new_result" to json(newResult),
        "future_fixed_slots_rescheduled" to json(futureFixedSlotsRescheduled),
    ))

fun WeeklySummaryGeneratedPropertiesV1(weekStartLocalDate: DateWireV1, summaryId: UuidWireV1, qualifiedBreakDays: Long, completedCount: Long) =
    WeeklySummaryGeneratedPropertiesV1(eventObject(
        "week_start_local_date" to json(weekStartLocalDate), "summary_id" to json(summaryId),
        "qualified_break_days" to json(qualifiedBreakDays), "completed_count" to json(completedCount),
    ))

fun WeeklySummaryViewedPropertiesV1(summaryId: UuidWireV1, weekStartLocalDate: DateWireV1) =
    WeeklySummaryViewedPropertiesV1(eventObject("summary_id" to json(summaryId), "week_start_local_date" to json(weekStartLocalDate)))

fun ExportStartedPropertiesV1(exportId: UuidWireV1) =
    ExportStartedPropertiesV1(eventObject("export_id" to json(exportId), "export_schema_version" to json(1L)))

fun ExportCompletedPropertiesV1(exportId: UuidWireV1, recordCounts: RecordCountsWireV1, byteCount: Long) =
    ExportCompletedPropertiesV1(eventObject("export_id" to json(exportId), "record_counts" to recordCounts.toJson(), "byte_count" to json(byteCount)))

fun ExportFailedPropertiesV1(exportId: UuidWireV1, errorCode: ExportFailureCodeV1) =
    ExportFailedPropertiesV1(eventObject("export_id" to json(exportId), "error_code" to json(errorCode)))

fun WorkScheduleSavedPropertiesV1(
    previousScheduleVersionId: UuidWireV1?, enabled: Boolean, selectedWeekdayCount: Long,
    workStart: TimeMinuteWireV1, workEnd: TimeMinuteWireV1, reminderCount: Long,
    changeSource: ScheduleChangeSourceV1, activeDecisionInvalidated: Boolean,
) = WorkScheduleSavedPropertiesV1(eventObject(
    "previous_schedule_version_id" to jsonNullable(previousScheduleVersionId), "enabled" to json(enabled),
    "selected_weekday_count" to json(selectedWeekdayCount), "work_start" to json(workStart), "work_end" to json(workEnd),
    "reminder_count" to json(reminderCount), "change_source" to json(changeSource), "active_decision_invalidated" to json(activeDecisionInvalidated),
))

fun ScheduleReconciledPropertiesV1(reason: ScheduleReconcileReasonV1, scheduledCount: Long, cancelledCount: Long, mergedCount: Long) =
    ScheduleReconciledPropertiesV1(eventObject(
        "reason" to json(reason), "scheduled_count" to json(scheduledCount), "cancelled_count" to json(cancelledCount), "merged_count" to json(mergedCount),
    ))

fun CheckInSubmittedPropertiesV1(
    checkInFlowId: UuidWireV1, checkInId: UuidWireV1, kind: CheckInKindV1, answersKind: AnswersKindV1, timing: EventTimingV1,
) = CheckInSubmittedPropertiesV1(typedObject(
    listOf("check_in_flow_id" to json(checkInFlowId), "check_in_id" to json(checkInId), "kind" to json(kind), "answers_kind" to json(answersKind)) +
        timingFields(timing, "duration_ms", "timing_invalid_reason"),
))

fun DecisionEvaluatedPropertiesV1(
    checkInId: UuidWireV1, result: RuleResultV1, baseMode: ModeV1?, effectiveMode: ModeV1?,
    reasonCodes: List<ReasonCodeV1>, invalidFields: List<InvalidFieldV1>, capApplied: Boolean,
) = DecisionEvaluatedPropertiesV1(eventObject(
    "check_in_id" to json(checkInId), "result" to json(result), "base_mode" to jsonNullable(baseMode),
    "effective_mode" to jsonNullable(effectiveMode), "reason_codes" to jsonArray(reasonCodes), "invalid_fields" to jsonArray(invalidFields),
    "rule_version" to json(1L), "cap_applied" to json(capApplied),
))

fun RoutineStartBlockedPropertiesV1(gate: StartGateV1, reason: ReconfirmReasonV1? = null) =
    RoutineStartBlockedPropertiesV1(typedObject(buildList {
        add("gate" to json(gate)); if (reason != null) add("reason" to json(reason))
    }))

fun RecommendationShownPropertiesV1(
    routineId: RoutineIdV1, baseMode: ModeV1, decisionEffectiveMode: ModeV1, runtimeEffectiveMode: ModeV1,
    capApplied: Boolean, runtimeDayModeCapSnapshot: EventDayModeCapSnapshotV1?,
) = RecommendationShownPropertiesV1(eventObject(
    "routine_id" to json(routineId), "base_mode" to json(baseMode), "decision_effective_mode" to json(decisionEffectiveMode),
    "runtime_effective_mode" to json(runtimeEffectiveMode), "cap_applied" to json(capApplied),
    "runtime_day_mode_cap_snapshot" to jsonNullable(runtimeDayModeCapSnapshot),
))

fun RestSuppressionCreatedPropertiesV1(originLocalDate: DateWireV1, originTimezoneId: String, expiresAtUtc: InstantWireV1) =
    RestSuppressionCreatedPropertiesV1(eventObject(
        "origin_local_date" to json(originLocalDate), "origin_timezone_id" to json(originTimezoneId),
        "expires_at_utc" to json(expiresAtUtc), "rule_version" to json(1L),
    ))

fun RoutineSelectedPropertiesV1(
    routineId: RoutineIdV1, routineMode: ModeV1, runtimeEffectiveMode: ModeV1, selection: RoutineSelectionV1,
    runtimeDayModeCapSnapshot: EventDayModeCapSnapshotV1?,
) = RoutineSelectedPropertiesV1(eventObject(
    "routine_id" to json(routineId), "routine_mode" to json(routineMode), "runtime_effective_mode" to json(runtimeEffectiveMode),
    "selection" to json(selection), "runtime_day_mode_cap_snapshot" to jsonNullable(runtimeDayModeCapSnapshot),
))

fun RoutinePausedPropertiesV1(elapsedMs: Long) = RoutinePausedPropertiesV1(eventObject("elapsed_ms" to json(elapsedMs)))
fun RoutineResumedPropertiesV1(elapsedMs: Long) = RoutineResumedPropertiesV1(eventObject("elapsed_ms" to json(elapsedMs)))
fun RoutineRecoveryOfferedPropertiesV1(elapsedMs: Long, contentVersion: SemVerWireV1) =
    RoutineRecoveryOfferedPropertiesV1(eventObject("elapsed_ms" to json(elapsedMs), "content_version" to json(contentVersion)))
fun RoutineRecoveryFailedPropertiesV1(reason: RecoveryReasonV1) = RoutineRecoveryFailedPropertiesV1(eventObject("reason" to json(reason)))
fun RoutineStepSkippedPropertiesV1(stepId: String, activeElapsedMs: Long) =
    RoutineStepSkippedPropertiesV1(eventObject("step_id" to json(stepId), "active_elapsed_ms" to json(activeElapsedMs)))

fun RoutineStoppedPropertiesV1(elapsedMs: Long, painGateStatus: PainGateStatusV1) =
    RoutineStoppedPropertiesV1(eventObject("elapsed_ms" to json(elapsedMs), "pain_gate_status" to json(painGateStatus)))

fun RoutineAbandonedPropertiesV1(reason: RecoveryReasonV1) = RoutineAbandonedPropertiesV1(eventObject(
    "reason" to json(reason), "pain_gate_status" to json(PainGateStatusV1.PENDING),
))

fun RoutineCompletedPropertiesV1(
    routineId: RoutineIdV1, durationMs: Long, stepSkipCount: Long,
    completionBootMarker: Long, completionElapsedRealtimeMs: Long, completionClockGeneration: Long,
    completionWallMinusElapsedMs: Long,
) = RoutineCompletedPropertiesV1(eventObject(
    "routine_id" to json(routineId), "duration_ms" to json(durationMs), "step_skip_count" to json(stepSkipCount),
    "pain_gate_status" to json(PainGateStatusV1.PENDING), "completion_boot_marker" to json(completionBootMarker),
    "completion_elapsed_realtime_ms" to json(completionElapsedRealtimeMs), "completion_clock_generation" to json(completionClockGeneration),
    "completion_wall_minus_elapsed_ms" to json(completionWallMinusElapsedMs),
))

fun PainGateResolvedPropertiesV1(
    terminalState: TerminalStateV1, newOrWorsePain: PainAnswerV1, painGateStatus: PainGateStatusV1,
    answeredAtOrAfterOriginExpiry: Boolean,
) = PainGateResolvedPropertiesV1(eventObject(
    "terminal_state" to json(terminalState), "new_or_worse_pain" to json(newOrWorsePain),
    "pain_gate_status" to json(painGateStatus), "answered_at_or_after_origin_expiry" to json(answeredAtOrAfterOriginExpiry),
))

fun FeedbackUpdatedPropertiesV1(
    updatedFields: List<UpdatedFieldV1>, terminalState: TerminalStateV1, effort: EffortV1?, contextFit: ContextFitV1?,
    feedbackComplete: Boolean, capResult: CapResultV1,
) = FeedbackUpdatedPropertiesV1(eventObject(
    "updated_fields" to jsonArray(updatedFields), "terminal_state" to json(terminalState), "effort" to jsonNullable(effort),
    "context_fit" to jsonNullable(contextFit), "feedback_complete" to json(feedbackComplete), "cap_result" to json(capResult),
))

fun DayModeCapUpdatedPropertiesV1(
    expirySourceSessionId: UuidWireV1, basisMode: ModeV1, previousCap: ModeV1?, newCap: ModeV1,
    deadlineSource: DeadlineSourceV1, origin: LocalStampWireV1, expiresAtUtc: InstantWireV1,
) = DayModeCapUpdatedPropertiesV1(eventObject(
    "expiry_source_session_id" to json(expirySourceSessionId), "basis_mode" to json(basisMode), "previous_cap" to jsonNullable(previousCap),
    "new_cap" to json(newCap), "deadline_source" to json(deadlineSource), "origin_occurred_at_utc" to json(origin.occurredAtUtc),
    "origin_local_date" to json(origin.localDate), "origin_timezone_id" to json(origin.zoneId),
    "origin_utc_offset_minutes" to json(origin.utcOffsetMinutes), "expires_at_utc" to json(expiresAtUtc), "rule_version" to json(1L),
))

fun ReminderPostedPropertiesV1(kind: ReminderKindV1, dueAt: LocalStampWireV1, deliveredAt: LocalStampWireV1, latenessMs: Long) =
    ReminderPostedPropertiesV1(eventObject(
        "kind" to json(kind), "due_at" to dueAt.toJson(), "delivered_at" to deliveredAt.toJson(), "lateness_ms" to json(latenessMs),
    ))

fun ReminderOpenedPropertiesV1(firstOpenedAt: LocalStampWireV1, openSurface: OpenSurfaceV1) =
    ReminderOpenedPropertiesV1(eventObject("first_opened_at" to firstOpenedAt.toJson(), "open_surface" to json(openSurface)))

fun ReminderSnoozedPropertiesV1(snoozeOccurrenceId: UuidWireV1, durationMinutes: Long, targetAt: LocalStampWireV1) =
    ReminderSnoozedPropertiesV1(eventObject(
        "snooze_occurrence_id" to json(snoozeOccurrenceId), "duration_minutes" to json(durationMinutes), "target_at" to targetAt.toJson(),
        "ordinal" to json(0L), "supersedes_occurrence_id" to JsonNull,
    ))

fun ReminderDismissedPropertiesV1(dismissedAt: LocalStampWireV1) =
    ReminderDismissedPropertiesV1(eventObject("dismissed_at" to dismissedAt.toJson()))

fun ReminderMergedPropertiesV1(keptOccurrenceId: UuidWireV1, distanceMs: Long, tieBreak: MergeTieBreakV1) =
    ReminderMergedPropertiesV1(eventObject(
        "kept_occurrence_id" to json(keptOccurrenceId), "distance_ms" to json(distanceMs), "tie_break" to json(tieBreak),
    ))

fun ReminderCancelledPropertiesV1(reason: ReminderCancelReasonV1, resultingStatus: ReminderResultStatusV1) =
    ReminderCancelledPropertiesV1(eventObject("reason" to json(reason), "resulting_status" to json(resultingStatus)))

fun ReminderBlockedPermissionPropertiesV1() =
    ReminderBlockedPermissionPropertiesV1(eventObject("status" to json(ReminderResultStatusV1.BLOCKED_PERMISSION)))

fun ReminderSkippedPropertiesV1(status: ReminderSkippedStatusV1, latenessMs: Long) =
    ReminderSkippedPropertiesV1(eventObject("status" to json(status), "lateness_ms" to json(latenessMs)))

fun ReminderScheduledPropertiesV1(dueAt: LocalStampWireV1, branch: ReminderScheduleBranchV1) =
    ReminderScheduledPropertiesV1(typedObject(buildList {
        add("due_at" to dueAt.toJson())
        when (branch) {
            is ReminderScheduleBranchV1.Fixed -> {
                add("kind" to json(ReminderKindV1.FIXED)); add("supersedes_occurrence_id" to jsonNullable(branch.supersedesOccurrenceId))
                add("logical_fixed_key" to branch.logicalKey.toJson().element); add("generation" to json(branch.generation))
                add("creation_reason" to json(branch.creationReason))
            }
            is ReminderScheduleBranchV1.Snooze -> {
                add("kind" to json(ReminderKindV1.SNOOZE)); add("supersedes_occurrence_id" to JsonNull)
                add("parent_occurrence_id" to json(branch.parentOccurrenceId)); add("ordinal" to json(branch.ordinal))
            }
        }
    }))

fun SafetyHoldCreatedPropertiesV1(
    kind: SafetyHoldKindV1, source: SafetyHoldSourceV1, originLocalDate: DateWireV1,
    originTimezoneId: String, expiresAtUtc: InstantWireV1,
) = SafetyHoldCreatedPropertiesV1(typedObject(buildList {
    add("kind" to json(kind))
    when (source) {
        is SafetyHoldSourceV1.CheckIn -> { add("source_type" to json(ConstraintSourceTypeV1.CHECK_IN)); add("source_id" to json(source.sourceId)) }
        SafetyHoldSourceV1.Session -> add("source_type" to json(ConstraintSourceTypeV1.SESSION))
    }
    add("origin_local_date" to json(originLocalDate)); add("origin_timezone_id" to json(originTimezoneId))
    add("expires_at_utc" to json(expiresAtUtc)); add("rule_version" to json(1L))
}))

fun SafetyScreenShownPropertiesV1(result: SafetyScreenResultV1, routeId: SafetyRouteV1, contentDigest: Sha256DigestWireV1) =
    SafetyScreenShownPropertiesV1(eventObject("result" to json(result), "route_id" to json(routeId), "content_digest" to json(contentDigest)))

fun RoutineStartedPropertiesV1(
    routineId: RoutineIdV1, checkInFlowId: UuidWireV1, runtimeEffectiveModeAtStart: ModeV1,
    isSelectedWorkdayAtStart: Boolean, startBootMarker: Long, startElapsedRealtimeMs: Long,
    startClockGeneration: Long, startWallMinusElapsedMs: Long, totalTiming: EventTimingV1,
) = RoutineStartedPropertiesV1(typedObject(
    listOf(
        "routine_id" to json(routineId), "check_in_flow_id" to json(checkInFlowId),
        "runtime_effective_mode_at_start" to json(runtimeEffectiveModeAtStart), "is_selected_workday_at_start" to json(isSelectedWorkdayAtStart),
        "start_boot_marker" to json(startBootMarker), "start_elapsed_realtime_ms" to json(startElapsedRealtimeMs),
        "start_clock_generation" to json(startClockGeneration), "start_wall_minus_elapsed_ms" to json(startWallMinusElapsedMs),
    ) + timingFields(totalTiming, "total_duration_ms", "total_timing_invalid_reason"),
))

private fun typedObject(fields: List<Pair<String, kotlinx.serialization.json.JsonElement>>) =
    StrictJsonObjectV1(JsonObject(linkedMapOf(*fields.toTypedArray())))

private fun timingFields(value: EventTimingV1, durationKey: String, invalidReasonKey: String) = when (value) {
    is EventTimingV1.Duration -> listOf(durationKey to json(value.milliseconds))
    is EventTimingV1.Invalid -> listOf(invalidReasonKey to json(value.reason))
}
