package vn.nhip2phut.domain.events

import kotlinx.serialization.json.JsonNull
import vn.nhip2phut.domain.wire.v1.*

internal object EventSpecsV1 {
    val all: Map<EventNameV1, EventSpecV1<out EventPropertiesV1>> = linkedMapOf(
        EventNameV1.APP_FIRST_OPENED to spec(
            EventNameV1.APP_FIRST_OPENED,
            AppFirstOpenedPropertiesV1::class,
            objectSchema("app_first_opened", required("first_open_id", UuidShapeV1)),
            ::AppFirstOpenedPropertiesV1,
            once("app_first_opened", "first_open_id"),
        ),
        EventNameV1.ONBOARDING_STARTED to spec(
            EventNameV1.ONBOARDING_STARTED,
            OnboardingStartedPropertiesV1::class,
            objectSchema(
                "onboarding_started",
                required("timing_start_boot_marker", NonNegativeInt64ShapeV1),
                required("timing_start_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
            ),
            ::OnboardingStartedPropertiesV1,
            once("onboarding_started", "installation_id"),
        ),
        EventNameV1.AGE_GATE_ANSWERED to spec(
            EventNameV1.AGE_GATE_ANSWERED,
            AgeGateAnsweredPropertiesV1::class,
            objectSchema("age_gate_answered", required("eligible_18_plus", BooleanShapeV1(true))),
            ::AgeGateAnsweredPropertiesV1,
            once("age_gate_answered", "installation_id"),
        ),
        EventNameV1.SCOPE_ACKNOWLEDGED to spec(
            EventNameV1.SCOPE_ACKNOWLEDGED,
            ScopeAcknowledgedPropertiesV1::class,
            objectSchema(
                "scope_acknowledged",
                required("acknowledgement_id", UuidShapeV1),
                required("kind", EnumShapeV1(listOf("onboarding"))),
                required("eligibility_confirmed", BooleanShapeV1(true)),
                required("content_version", SemVerShapeV1),
                required("content_digest", DigestShapeV1),
            ),
            ::ScopeAcknowledgedPropertiesV1,
            once("scope_acknowledged", "acknowledgement_id"),
            refs("acknowledgement_id" to RefTargetTypeV1.SAFETY_ACKNOWLEDGEMENT),
        ),
        EventNameV1.SCOPE_REACK_REQUIRED to spec(
            EventNameV1.SCOPE_REACK_REQUIRED,
            ScopeReackRequiredPropertiesV1::class,
            objectSchema(
                "scope_reack_required",
                required("current_acknowledgement_id", UuidShapeV1),
                required("previous_content_version", SemVerShapeV1),
                required("previous_content_digest", DigestShapeV1),
                required("required_content_version", SemVerShapeV1),
                required("required_content_digest", DigestShapeV1),
                required("trigger", EnumShapeV1(listOf("home", "notification", "check_in", "routine_start"))),
            ),
            ::ScopeReackRequiredPropertiesV1,
            once("scope_reack_required", "current_acknowledgement_id", "required_content_version", "required_content_digest", "trigger"),
            refs("current_acknowledgement_id" to RefTargetTypeV1.SAFETY_ACKNOWLEDGEMENT),
        ),
        EventNameV1.SCOPE_REACK_COMPLETED to spec(
            EventNameV1.SCOPE_REACK_COMPLETED,
            ScopeReackCompletedPropertiesV1::class,
            objectSchema(
                "scope_reack_completed",
                required("acknowledgement_id", UuidShapeV1),
                required("supersedes_acknowledgement_id", UuidShapeV1),
                required("content_version", SemVerShapeV1),
                required("content_digest", DigestShapeV1),
            ),
            ::ScopeReackCompletedPropertiesV1,
            once("scope_reack_completed", "acknowledgement_id"),
            refs(
                "acknowledgement_id" to RefTargetTypeV1.SAFETY_ACKNOWLEDGEMENT,
                "supersedes_acknowledgement_id" to RefTargetTypeV1.SAFETY_ACKNOWLEDGEMENT,
            ),
        ),
        EventNameV1.NOTIFICATION_PERMISSION_PROMPTED to spec(
            EventNameV1.NOTIFICATION_PERMISSION_PROMPTED,
            NotificationPermissionPromptedPropertiesV1::class,
            objectSchema(
                "notification_permission_prompted",
                required("attempt_id", UuidShapeV1),
                required("trigger", EnumShapeV1(listOf("automatic_onboarding", "explicit_user_retry"))),
            ),
            ::NotificationPermissionPromptedPropertiesV1,
            once("notification_permission_prompted", "attempt_id"),
        ),
        EventNameV1.NOTIFICATION_PERMISSION_UPDATED to notificationPermissionUpdatedSpec(),
        EventNameV1.ONBOARDING_COMPLETED to spec(
            EventNameV1.ONBOARDING_COMPLETED,
            OnboardingCompletedPropertiesV1::class,
            timingXorSchema(
                "onboarding_completed",
                durationKey = "duration_ms",
                reasonKey = "timing_invalid_reason",
                reasons = listOf("same_boot_unavailable", "elapsed_rollback", "overflow"),
                trailing = listOf(
                    required("activation_boot_marker", NonNegativeInt64ShapeV1),
                    required("activation_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
                    required("activation_clock_generation", NonNegativeInt64ShapeV1),
                    required("activation_wall_minus_elapsed_ms", Int64ShapeV1()),
                ),
            ),
            ::OnboardingCompletedPropertiesV1,
            once("onboarding_completed", "installation_id"),
        ),
        EventNameV1.CHECK_IN_STARTED to spec(
            EventNameV1.CHECK_IN_STARTED,
            CheckInStartedPropertiesV1::class,
            objectSchema(
                "check_in_started",
                required("check_in_flow_id", UuidShapeV1),
                required("kind", EnumShapeV1(listOf("new", "reconfirm"))),
                required("timing_start_boot_marker", NonNegativeInt64ShapeV1),
                required("timing_start_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
            ),
            ::CheckInStartedPropertiesV1,
            once("check_in_started", "check_in_flow_id"),
        ),
        EventNameV1.CHECK_IN_RECONFIRMATION_REQUIRED to spec(
            EventNameV1.CHECK_IN_RECONFIRMATION_REQUIRED,
            CheckInReconfirmationRequiredPropertiesV1::class,
            objectSchema(
                "check_in_reconfirmation_required",
                required("check_in_id", UuidShapeV1),
                required("age_ms", NullableShapeV1(NonNegativeInt64ShapeV1)),
                required("reason", reconfirmReasonShape),
                required("trigger", EnumShapeV1(listOf("home", "notification", "routine_start"))),
            ),
            ::CheckInReconfirmationRequiredPropertiesV1,
            repeat(EventNameV1.CHECK_IN_RECONFIRMATION_REQUIRED),
            refs("check_in_id" to RefTargetTypeV1.CHECK_IN),
        ),
        EventNameV1.REST_SUPPRESSION_SUPERSEDED to spec(
            EventNameV1.REST_SUPPRESSION_SUPERSEDED,
            RestSuppressionSupersededPropertiesV1::class,
            objectSchema(
                "rest_suppression_superseded",
                required("source_decision_id", UuidShapeV1),
                required("new_check_in_id", UuidShapeV1),
                required("new_result", EnumShapeV1(listOf("mode", "rest", "safety"))),
                required("future_fixed_slots_rescheduled", NonNegativeInt64ShapeV1),
                cross = { value, path ->
                    if (value.requiredString("new_result", path) != "mode" && value.requiredInt64("future_fixed_slots_rescheduled", path) != 0L) {
                        fail(path, "rest/safety result must not reschedule fixed slots")
                    }
                },
            ),
            ::RestSuppressionSupersededPropertiesV1,
            once("rest_suppression_superseded", "source_decision_id", "new_check_in_id"),
            refs(
                "source_decision_id" to RefTargetTypeV1.DECISION,
                "new_check_in_id" to RefTargetTypeV1.CHECK_IN,
            ),
        ),
        EventNameV1.WEEKLY_SUMMARY_GENERATED to spec(
            EventNameV1.WEEKLY_SUMMARY_GENERATED,
            WeeklySummaryGeneratedPropertiesV1::class,
            objectSchema(
                "weekly_summary_generated",
                required("week_start_local_date", DateShapeV1),
                required("summary_id", UuidShapeV1),
                required("qualified_break_days", NonNegativeInt64ShapeV1),
                required("completed_count", NonNegativeInt64ShapeV1),
                cross = { value, path -> DateWireV1.parse(value.requiredString("week_start_local_date", path)).requireMonday("$path.week_start_local_date") },
            ),
            ::WeeklySummaryGeneratedPropertiesV1,
            repeat(EventNameV1.WEEKLY_SUMMARY_GENERATED),
            refs("summary_id" to RefTargetTypeV1.WEEKLY_SUMMARY),
        ),
        EventNameV1.WEEKLY_SUMMARY_VIEWED to spec(
            EventNameV1.WEEKLY_SUMMARY_VIEWED,
            WeeklySummaryViewedPropertiesV1::class,
            objectSchema(
                "weekly_summary_viewed",
                required("summary_id", UuidShapeV1),
                required("week_start_local_date", DateShapeV1),
                cross = { value, path -> DateWireV1.parse(value.requiredString("week_start_local_date", path)).requireMonday("$path.week_start_local_date") },
            ),
            ::WeeklySummaryViewedPropertiesV1,
            repeat(EventNameV1.WEEKLY_SUMMARY_VIEWED),
            refs("summary_id" to RefTargetTypeV1.WEEKLY_SUMMARY),
        ),
        EventNameV1.EXPORT_STARTED to spec(
            EventNameV1.EXPORT_STARTED,
            ExportStartedPropertiesV1::class,
            objectSchema(
                "export_started",
                required("export_id", UuidShapeV1),
                required("export_schema_version", Int64ShapeV1(literal = 1)),
            ),
            ::ExportStartedPropertiesV1,
            once("export_started", "export_id"),
        ),
        EventNameV1.EXPORT_COMPLETED to spec(
            EventNameV1.EXPORT_COMPLETED,
            ExportCompletedPropertiesV1::class,
            objectSchema(
                "export_completed",
                required("export_id", UuidShapeV1),
                required("record_counts", ObjectShapeV1(RecordCountsSchemaV1)),
                required("byte_count", NonNegativeInt64ShapeV1),
            ),
            ::ExportCompletedPropertiesV1,
            once("export_terminal", "export_id"),
        ),
        EventNameV1.EXPORT_FAILED to spec(
            EventNameV1.EXPORT_FAILED,
            ExportFailedPropertiesV1::class,
            objectSchema(
                "export_failed",
                required("export_id", UuidShapeV1),
                required("error_code", EnumShapeV1(exportFailureCodes)),
            ),
            ::ExportFailedPropertiesV1,
            once("export_terminal", "export_id"),
        ),
        EventNameV1.WORK_SCHEDULE_SAVED to workScheduleSavedSpec(),
        EventNameV1.SCHEDULE_RECONCILED to spec(
            EventNameV1.SCHEDULE_RECONCILED,
            ScheduleReconciledPropertiesV1::class,
            objectSchema(
                "schedule_reconciled",
                required("reason", EnumShapeV1(scheduleReconcileReasons)),
                required("scheduled_count", NonNegativeInt64ShapeV1),
                required("cancelled_count", NonNegativeInt64ShapeV1),
                required("merged_count", NonNegativeInt64ShapeV1),
            ),
            ::ScheduleReconciledPropertiesV1,
            repeat(EventNameV1.SCHEDULE_RECONCILED),
        ),
        EventNameV1.CHECK_IN_SUBMITTED to checkInSubmittedSpec(),
        EventNameV1.DECISION_EVALUATED to decisionEvaluatedSpec(),
        EventNameV1.ROUTINE_START_BLOCKED to routineStartBlockedSpec(),
        EventNameV1.RECOMMENDATION_SHOWN to recommendationShownSpec(),
        EventNameV1.REST_SUPPRESSION_CREATED to spec(
            EventNameV1.REST_SUPPRESSION_CREATED,
            RestSuppressionCreatedPropertiesV1::class,
            sideEffectOriginSchema("rest_suppression_created", includeKindSource = false),
            ::RestSuppressionCreatedPropertiesV1,
            once("rest_suppression_created", "decision_id"),
        ),
        EventNameV1.ROUTINE_SELECTED to routineSelectedSpec(),
        EventNameV1.ROUTINE_PAUSED to elapsedSpec(EventNameV1.ROUTINE_PAUSED, RoutinePausedPropertiesV1::class, ::RoutinePausedPropertiesV1),
        EventNameV1.ROUTINE_RESUMED to elapsedSpec(EventNameV1.ROUTINE_RESUMED, RoutineResumedPropertiesV1::class, ::RoutineResumedPropertiesV1),
        EventNameV1.ROUTINE_RECOVERY_OFFERED to spec(
            EventNameV1.ROUTINE_RECOVERY_OFFERED,
            RoutineRecoveryOfferedPropertiesV1::class,
            objectSchema(
                "routine_recovery_offered",
                required("elapsed_ms", NonNegativeInt64ShapeV1),
                required("content_version", SemVerShapeV1),
            ),
            ::RoutineRecoveryOfferedPropertiesV1,
            repeat(EventNameV1.ROUTINE_RECOVERY_OFFERED),
        ),
        EventNameV1.ROUTINE_RECOVERY_FAILED to spec(
            EventNameV1.ROUTINE_RECOVERY_FAILED,
            RoutineRecoveryFailedPropertiesV1::class,
            objectSchema("routine_recovery_failed", required("reason", recoveryReasonShape)),
            ::RoutineRecoveryFailedPropertiesV1,
            once("routine_recovery_failed", "session_id"),
        ),
        EventNameV1.ROUTINE_STEP_SKIPPED to spec(
            EventNameV1.ROUTINE_STEP_SKIPPED,
            RoutineStepSkippedPropertiesV1::class,
            objectSchema(
                "routine_step_skipped",
                required("step_id", StringShapeV1),
                required("active_elapsed_ms", NonNegativeInt64ShapeV1),
            ),
            ::RoutineStepSkippedPropertiesV1,
            once("routine_step_skipped", "session_id", "step_id"),
        ),
        EventNameV1.ROUTINE_STOPPED to spec(
            EventNameV1.ROUTINE_STOPPED,
            RoutineStoppedPropertiesV1::class,
            objectSchema(
                "routine_stopped",
                required("elapsed_ms", NonNegativeInt64ShapeV1),
                required("pain_gate_status", EnumShapeV1(listOf("RESOLVED_NO", "RESOLVED_HOLD"))),
            ),
            ::RoutineStoppedPropertiesV1,
            once("routine_terminal", "session_id"),
        ),
        EventNameV1.ROUTINE_ABANDONED to spec(
            EventNameV1.ROUTINE_ABANDONED,
            RoutineAbandonedPropertiesV1::class,
            objectSchema(
                "routine_abandoned",
                required("reason", recoveryReasonShape),
                required("pain_gate_status", EnumShapeV1(listOf("PENDING"))),
            ),
            ::RoutineAbandonedPropertiesV1,
            once("routine_terminal", "session_id"),
        ),
        EventNameV1.ROUTINE_COMPLETED to routineCompletedSpec(),
        EventNameV1.PAIN_GATE_RESOLVED to spec(
            EventNameV1.PAIN_GATE_RESOLVED,
            PainGateResolvedPropertiesV1::class,
            objectSchema(
                "pain_gate_resolved",
                required("terminal_state", EnumShapeV1(listOf("completed", "stopped", "abandoned"))),
                required("new_or_worse_pain", EnumShapeV1(listOf("yes", "no"))),
                required("pain_gate_status", EnumShapeV1(listOf("RESOLVED_NO", "RESOLVED_HOLD"))),
                required("answered_at_or_after_origin_expiry", BooleanShapeV1()),
                cross = { value, path ->
                    val pain = value.requiredString("new_or_worse_pain", path)
                    val status = value.requiredString("pain_gate_status", path)
                    if ((pain == "yes") != (status == "RESOLVED_HOLD")) fail(path, "pain answer/status mismatch")
                },
            ),
            ::PainGateResolvedPropertiesV1,
            once("pain_gate_resolved", "session_id"),
        ),
        EventNameV1.FEEDBACK_UPDATED to feedbackUpdatedSpec(),
        EventNameV1.DAY_MODE_CAP_UPDATED to dayModeCapUpdatedSpec(),
        EventNameV1.REMINDER_POSTED to reminderPostedSpec(),
        EventNameV1.REMINDER_OPENED to spec(
            EventNameV1.REMINDER_OPENED,
            ReminderOpenedPropertiesV1::class,
            objectSchema(
                "reminder_opened",
                required("first_opened_at", ObjectShapeV1(LocalStampSchemaV1)),
                required("open_surface", EnumShapeV1(listOf("notification_body", "start_action"))),
            ),
            ::ReminderOpenedPropertiesV1,
            once("reminder_opened", "reminder_occurrence_id"),
        ),
        EventNameV1.REMINDER_SNOOZED to reminderSnoozedSpec(),
        EventNameV1.REMINDER_DISMISSED to spec(
            EventNameV1.REMINDER_DISMISSED,
            ReminderDismissedPropertiesV1::class,
            objectSchema("reminder_dismissed", required("dismissed_at", ObjectShapeV1(LocalStampSchemaV1))),
            ::ReminderDismissedPropertiesV1,
            once("reminder_dismissed", "reminder_occurrence_id"),
        ),
        EventNameV1.REMINDER_MERGED to reminderMergedSpec(),
        EventNameV1.REMINDER_CANCELLED to reminderCancelledSpec(),
        EventNameV1.REMINDER_BLOCKED_PERMISSION to spec(
            EventNameV1.REMINDER_BLOCKED_PERMISSION,
            ReminderBlockedPermissionPropertiesV1::class,
            objectSchema("reminder_blocked_permission", required("status", EnumShapeV1(listOf("BLOCKED_PERMISSION")))),
            ::ReminderBlockedPermissionPropertiesV1,
            once("reminder_delivery_resolution", "reminder_occurrence_id"),
        ),
        EventNameV1.REMINDER_SKIPPED to spec(
            EventNameV1.REMINDER_SKIPPED,
            ReminderSkippedPropertiesV1::class,
            objectSchema(
                "reminder_skipped",
                required("status", EnumShapeV1(reminderSkippedStatuses)),
                required("lateness_ms", NonNegativeInt64ShapeV1),
            ),
            ::ReminderSkippedPropertiesV1,
            once("reminder_delivery_resolution", "reminder_occurrence_id"),
        ),
        EventNameV1.REMINDER_SCHEDULED to reminderScheduledSpec(),
        EventNameV1.SAFETY_HOLD_CREATED to safetyHoldCreatedSpec(),
        EventNameV1.SAFETY_SCREEN_SHOWN to safetyScreenShownSpec(),
        EventNameV1.ROUTINE_STARTED to routineStartedSpec(),
    )

    init {
        require(all.size == 48)
        require(all.keys == EventNameV1.entries.toSet())
        require(all.values.map { it.propertiesType }.distinct().size == 48)
    }
}

private val modeShape = EnumShapeV1(listOf("RECOVER", "MAINTAIN", "BUILD"))
private val routineIdShape = EnumShapeV1(listOf("REC-01", "REC-02", "MAI-01", "MAI-02", "BUI-01", "BUI-02"))
private val reconfirmReasonShape = EnumShapeV1(listOf("schedule_changed", "ttl", "local_date_changed", "timezone_or_time_change", "clock_unknown"))
private val recoveryReasonShape = EnumShapeV1(listOf("reboot_or_clock_discontinuity", "work_window_or_date_expired", "content_unavailable_or_identity_mismatch"))
private val timingReasonShape = EnumShapeV1(listOf("same_boot_unavailable", "elapsed_rollback", "overflow", "background_over_10m"))
private val exportFailureCodes = listOf(
    "snapshot_read_failed", "json_encode_failed", "destination_open_failed", "destination_write_failed",
    "destination_flush_failed", "destination_close_failed", "provider_failed", "security_denied",
)
private val scheduleReconcileReasons = listOf(
    "schedule_edit", "boot", "timezone_change", "app_update", "permission_change", "safety_hold", "rest_only",
    "fresh_check_in_after_rest", "active_session", "pending_pain", "pain_resolved_no",
)
private val reminderSkippedStatuses = listOf(
    "SKIPPED_LATE", "SKIPPED_WORK_END", "SKIPPED_SAFETY_HOLD", "SKIPPED_REST",
    "SKIPPED_SESSION_GUARD", "SKIPPED_NOT_SELECTED_WORKDAY",
)

private fun objectSchema(
    name: String,
    vararg fields: WireFieldV1,
    cross: (StrictJsonObjectV1, String) -> Unit = { _, _ -> },
): ClosedObjectSchemaV1 = ClosedObjectSchemaV1("${name}PropertiesV1", fields.toList(), cross)

private fun timingXorSchema(
    name: String,
    durationKey: String,
    reasonKey: String,
    reasons: List<String>,
    leading: List<WireFieldV1> = emptyList(),
    trailing: List<WireFieldV1> = emptyList(),
    cross: (StrictJsonObjectV1, String) -> Unit = { _, _ -> },
): ClosedObjectSchemaV1 = ClosedObjectSchemaV1(
    "${name}PropertiesV1",
    leading + listOf(
        optional(durationKey, NonNegativeInt64ShapeV1),
        optional(reasonKey, EnumShapeV1(reasons)),
    ) + trailing,
) { value, path ->
    if (value.hasKey(durationKey) == value.hasKey(reasonKey)) fail(path, "exactly one of $durationKey/$reasonKey is required")
    cross(value, path)
}

private fun once(domain: String, vararg selectors: String): (EventPropertiesV1) -> EventIdempotencyPlanV1 = {
    EventIdempotencyPlanV1(EventIdempotencyKindV1.AT_MOST_ONCE, domain, selectors.toList())
}

private fun repeat(name: EventNameV1): (EventPropertiesV1) -> EventIdempotencyPlanV1 = {
    EventIdempotencyPlanV1(EventIdempotencyKindV1.REPEATABLE_BY_EVENT_ID, name.wire, listOf("event_id"))
}

private fun refs(vararg refs: Pair<String, RefTargetTypeV1>): EventRefPlanV1 =
    EventRefPlanV1(refs.map { (slot, target) -> EventAdditionalRefV1(slot, target, slot.startsWith("previous_") || slot.startsWith("supersedes_") || slot.startsWith("parent_")) })

private fun <P : EventPropertiesV1> spec(
    name: EventNameV1,
    type: kotlin.reflect.KClass<P>,
    schema: ClosedObjectSchemaV1,
    factory: (StrictJsonObjectV1) -> P,
    idempotency: (EventPropertiesV1) -> EventIdempotencyPlanV1,
    refs: EventRefPlanV1 = EventRefPlanV1(),
    conditional: (EventEnvelopeV1, P, String) -> Unit = { _, _, _ -> },
): EventSpecV1<P> = EventSpecV1(
    name = name,
    envelopeMask = EventContractRegistryV1.maskFor(name),
    propertiesType = type,
    propertyKeys = schema.keys,
    refPlan = refs,
    companionPlan = companionPlanFor(name),
    decodeProperties = { raw, path -> factory(StrictJsonObjectV1(schema.validateAndOrder(raw, path))) },
    encodeProperties = { properties, path -> StrictJsonObjectV1(schema.validateAndOrder(properties.body, path)) },
    validateEnvelopeAndProperties = { envelope, properties, path ->
        validateEnvelopeMask(name, EventContractRegistryV1.maskFor(name), envelope, path)
        schema.validateAndOrder(properties.body, "$path.properties")
        conditional(envelope, properties, path)
    },
    resolveIdempotency = { properties -> idempotency(properties) },
)

private fun validateEnvelopeMask(name: EventNameV1, mask: EventEnvelopeMaskV1, envelope: EventEnvelopeV1, path: String) {
    val slots = listOf(
        "decision_id" to (mask.decisionId to envelope.decisionId),
        "session_id" to (mask.sessionId to envelope.sessionId),
        "reminder_occurrence_id" to (mask.reminderOccurrenceId to envelope.reminderOccurrenceId),
        "schedule_version_id" to (mask.scheduleVersionId to envelope.scheduleVersionId),
        "source" to (mask.source to envelope.source),
    )
    slots.forEach { (slot, pair) ->
        val (rule, value) = pair
        when (rule) {
            EnvelopeSlotRule.REQUIRED -> if (value == null) fail(path, "$name requires envelope $slot")
            EnvelopeSlotRule.FORBIDDEN -> if (value != null) fail(path, "$name forbids envelope $slot")
            EnvelopeSlotRule.CONDITIONAL -> Unit
        }
    }
}

private fun companionPlanFor(name: EventNameV1): EventCompanionPlanV1 {
    fun role(role: String, type: CompanionSourceTypeV1, selector: String, conditional: Boolean = false) =
        RequiredCompanionRoleV1(role, type, selector, conditional)
    val roles = when (name) {
        EventNameV1.SCOPE_ACKNOWLEDGED -> listOf(role(CompanionRolesV1.PROFILE_ONBOARDING, CompanionSourceTypeV1.SAFETY_ACKNOWLEDGEMENT, "acknowledgement_id"))
        EventNameV1.SCOPE_REACK_COMPLETED -> listOf(role(CompanionRolesV1.ACK_REACK, CompanionSourceTypeV1.SAFETY_ACKNOWLEDGEMENT, "acknowledgement_id"))
        EventNameV1.ONBOARDING_COMPLETED -> listOf(role(CompanionRolesV1.PROFILE_ONBOARDING, CompanionSourceTypeV1.APP_PROFILE, "app_profile_singleton"))
        EventNameV1.CHECK_IN_SUBMITTED -> listOf(role(CompanionRolesV1.CHECK_IN_COMMIT, CompanionSourceTypeV1.CHECK_IN, "check_in_id"))
        EventNameV1.DECISION_EVALUATED -> listOf(role(CompanionRolesV1.DECISION_COMMIT, CompanionSourceTypeV1.DECISION, "decision_id"))
        EventNameV1.SAFETY_HOLD_CREATED -> listOf(
            role(CompanionRolesV1.DECISION_SIDE_EFFECT, CompanionSourceTypeV1.DECISION, "source_graph_decision", true),
            role(CompanionRolesV1.SESSION_FEEDBACK_SIDE_EFFECT, CompanionSourceTypeV1.SESSION, "session_id", true),
        )
        EventNameV1.REST_SUPPRESSION_CREATED -> listOf(role(CompanionRolesV1.DECISION_SIDE_EFFECT, CompanionSourceTypeV1.DECISION, "decision_id"))
        EventNameV1.ROUTINE_STARTED -> listOf(role(CompanionRolesV1.SESSION_START, CompanionSourceTypeV1.SESSION, "session_id"))
        EventNameV1.ROUTINE_STEP_SKIPPED -> listOf(role(CompanionRolesV1.SESSION_STEP_SKIP, CompanionSourceTypeV1.SESSION, "session_id"))
        EventNameV1.ROUTINE_COMPLETED,
        EventNameV1.ROUTINE_STOPPED,
        EventNameV1.ROUTINE_ABANDONED -> listOf(role(CompanionRolesV1.SESSION_TERMINAL, CompanionSourceTypeV1.SESSION, "session_id"))
        EventNameV1.PAIN_GATE_RESOLVED -> listOf(role(CompanionRolesV1.SESSION_PAIN_RESOLUTION, CompanionSourceTypeV1.SESSION, "session_id"))
        EventNameV1.FEEDBACK_UPDATED -> listOf(role(CompanionRolesV1.SESSION_FEEDBACK_TRANSITION, CompanionSourceTypeV1.SESSION, "session_id"))
        EventNameV1.DAY_MODE_CAP_UPDATED -> listOf(role(CompanionRolesV1.SESSION_FEEDBACK_SIDE_EFFECT, CompanionSourceTypeV1.SESSION, "session_id"))
        EventNameV1.REMINDER_SCHEDULED -> listOf(role(CompanionRolesV1.REMINDER_CREATE, CompanionSourceTypeV1.REMINDER_OCCURRENCE, "reminder_occurrence_id"))
        EventNameV1.REMINDER_SNOOZED -> listOf(
            role(CompanionRolesV1.REMINDER_SNOOZE_EDGE, CompanionSourceTypeV1.REMINDER_OCCURRENCE, "reminder_occurrence_id"),
            role(CompanionRolesV1.REMINDER_SNOOZE_EDGE, CompanionSourceTypeV1.REMINDER_OCCURRENCE, "snooze_occurrence_id"),
        )
        EventNameV1.REMINDER_POSTED -> listOf(role(CompanionRolesV1.REMINDER_DELIVERY, CompanionSourceTypeV1.REMINDER_OCCURRENCE, "reminder_occurrence_id"))
        EventNameV1.REMINDER_MERGED,
        EventNameV1.REMINDER_CANCELLED,
        EventNameV1.REMINDER_BLOCKED_PERMISSION,
        EventNameV1.REMINDER_SKIPPED -> listOf(role(CompanionRolesV1.REMINDER_RESOLUTION, CompanionSourceTypeV1.REMINDER_OCCURRENCE, "reminder_occurrence_id"))
        EventNameV1.REMINDER_OPENED,
        EventNameV1.REMINDER_DISMISSED -> listOf(role(CompanionRolesV1.REMINDER_INTERACTION, CompanionSourceTypeV1.REMINDER_OCCURRENCE, "reminder_occurrence_id"))
        EventNameV1.WEEKLY_SUMMARY_GENERATED -> listOf(role(CompanionRolesV1.WEEKLY_GENERATION, CompanionSourceTypeV1.WEEKLY_SUMMARY, "summary_id"))
        else -> emptyList()
    }
    return EventCompanionPlanV1(roles)
}

private fun notificationPermissionUpdatedSpec(): EventSpecV1<NotificationPermissionUpdatedPropertiesV1> {
    val schema = objectSchema(
        "notification_permission_updated",
        required("state", EnumShapeV1(listOf("granted", "denied", "unavailable"))),
        required("source", EnumShapeV1(listOf("system_prompt", "settings", "resume_check"))),
        required("attempt_id", NullableShapeV1(UuidShapeV1)),
        required("prompt_result", NullableShapeV1(EnumShapeV1(listOf("granted", "not_granted")))),
        cross = { value, path ->
            val source = value.requiredString("source", path)
            if (source == "system_prompt") {
                if (!value.hasNonNull("attempt_id") || !value.hasNonNull("prompt_result")) fail(path, "system_prompt requires attempt_id and prompt_result")
                val state = value.requiredString("state", path)
                val result = value.requiredString("prompt_result", path)
                if ((result == "granted" && state != "granted") || (result == "not_granted" && state != "denied")) fail(path, "prompt result/state mismatch")
            } else if (value.hasNonNull("attempt_id") || value.hasNonNull("prompt_result")) {
                fail(path, "settings/resume observation requires null attempt/result")
            }
        },
    )
    return spec(
        EventNameV1.NOTIFICATION_PERMISSION_UPDATED,
        NotificationPermissionUpdatedPropertiesV1::class,
        schema,
        ::NotificationPermissionUpdatedPropertiesV1,
        idempotency = { properties ->
            if (properties.body.requiredString("source", "notification_permission_updated") == "system_prompt") {
                EventIdempotencyPlanV1(EventIdempotencyKindV1.AT_MOST_ONCE, "notification_permission_system_result", listOf("attempt_id"))
            } else {
                EventIdempotencyPlanV1(EventIdempotencyKindV1.REPEATABLE_BY_EVENT_ID, EventNameV1.NOTIFICATION_PERMISSION_UPDATED.wire, listOf("event_id"))
            }
        },
    )
}

private fun workScheduleSavedSpec(): EventSpecV1<WorkScheduleSavedPropertiesV1> {
    val schema = objectSchema(
        "work_schedule_saved",
        required("previous_schedule_version_id", NullableShapeV1(UuidShapeV1)),
        required("enabled", BooleanShapeV1()),
        required("selected_weekday_count", Int64ShapeV1(1, 7)),
        required("work_start", TimeMinuteShapeV1),
        required("work_end", TimeMinuteShapeV1),
        required("reminder_count", Int64ShapeV1(1, 2)),
        required("change_source", EnumShapeV1(listOf("onboarding", "settings"))),
        required("active_decision_invalidated", BooleanShapeV1()),
        cross = { value, path ->
            if (TimeMinuteWireV1.parse(value.requiredString("work_start", path)) >= TimeMinuteWireV1.parse(value.requiredString("work_end", path))) {
                fail(path, "work_end must be later than work_start")
            }
            val previousScheduleId = value.nullableString("previous_schedule_version_id", path)
            when (value.requiredString("change_source", path)) {
                "onboarding" -> {
                    if (!value.requiredBoolean("enabled", path) ||
                        previousScheduleId != null ||
                        value.requiredBoolean("active_decision_invalidated", path)
                    ) {
                        fail(path, "onboarding schedule save requires enabled=true, no previous schedule and active_decision_invalidated=false")
                    }
                }
                "settings" -> if (previousScheduleId == null) {
                    fail(path, "settings schedule save requires previous_schedule_version_id")
                }
            }
        },
    )
    return spec(
        EventNameV1.WORK_SCHEDULE_SAVED,
        WorkScheduleSavedPropertiesV1::class,
        schema,
        ::WorkScheduleSavedPropertiesV1,
        once("work_schedule_saved", "schedule_version_id"),
        EventRefPlanV1(listOf(EventAdditionalRefV1("previous_schedule_version_id", RefTargetTypeV1.WORK_SCHEDULE_VERSION, conditional = true))),
    )
}

private fun checkInSubmittedSpec(): EventSpecV1<CheckInSubmittedPropertiesV1> {
    val schema = timingXorSchema(
        "check_in_submitted",
        "duration_ms",
        "timing_invalid_reason",
        listOf("same_boot_unavailable", "elapsed_rollback", "overflow", "background_over_10m"),
        leading = listOf(
            required("check_in_flow_id", UuidShapeV1),
            required("check_in_id", UuidShapeV1),
            required("kind", EnumShapeV1(listOf("new", "reconfirm"))),
            required("answers_kind", EnumShapeV1(listOf("red_flag_stop", "acute_stop", "full"))),
        ),
    )
    return spec(
        EventNameV1.CHECK_IN_SUBMITTED,
        CheckInSubmittedPropertiesV1::class,
        schema,
        ::CheckInSubmittedPropertiesV1,
        once("check_in_submitted", "check_in_flow_id"),
        refs("check_in_id" to RefTargetTypeV1.CHECK_IN),
    )
}

private val eventReasonCodeOrder = listOf(
    "SAF_LOCK_ACTIVE", "SAF_RED_FLAG_PRESENT", "SAF_INPUT_MISSING", "SAF_INPUT_INVALID", "SAF_ACUTE_ILLNESS",
    "SAF_ACUTE_NEW_OR_WORSENING_PAIN", "SAF_MEDICALLY_RESTRICTED", "SAF_INTENT_REST", "SAF_ENERGY_LOW",
    "SAF_STIFFNESS_NOTABLE", "SAF_BUILD_CONDITIONS", "SAF_MAINTAIN_DEFAULT", "SAF_DAY_MODE_CAP_APPLIED",
)
private val eventInvalidFieldOrder = listOf("red_flag", "acute_issue", "energy", "stiffness", "intent", "day_mode_cap")

private fun decisionEvaluatedSpec(): EventSpecV1<DecisionEvaluatedPropertiesV1> {
    val schema = objectSchema(
        "decision_evaluated",
        required("check_in_id", UuidShapeV1),
        required("result", EnumShapeV1(listOf("URGENT_STOP", "PAUSE_TODAY", "INCOMPLETE", "REST_ONLY", "RECOVER", "MAINTAIN", "BUILD"))),
        required("base_mode", NullableShapeV1(modeShape)),
        required("effective_mode", NullableShapeV1(modeShape)),
        required("reason_codes", ArrayShapeV1(EnumShapeV1(eventReasonCodeOrder)) { array, path -> array.requireUniqueStringsInCanonicalOrder(path, eventReasonCodeOrder) }),
        required("invalid_fields", ArrayShapeV1(EnumShapeV1(eventInvalidFieldOrder)) { array, path -> array.requireUniqueStringsInCanonicalOrder(path, eventInvalidFieldOrder) }),
        required("rule_version", Int64ShapeV1(literal = 1)),
        required("cap_applied", BooleanShapeV1()),
        cross = { value, path ->
            val result = value.requiredString("result", path)
            val base = value.nullableString("base_mode", path)
            val effective = value.nullableString("effective_mode", path)
            val invalid = value.requiredElement("invalid_fields", path).asArray("$path.invalid_fields")
            if (result in listOf("RECOVER", "MAINTAIN", "BUILD")) {
                if (base != result || effective == null || modeRank(effective) > modeRank(base)) fail(path, "result/mode matrix mismatch")
            } else if (base != null || effective != null) fail(path, "non-mode result must use null modes")
            val invalidFields = invalid.mapIndexed { index, element ->
                element.asString("$path.invalid_fields[$index]")
            }
            if (result == "INCOMPLETE") {
                if (invalidFields != listOf("day_mode_cap")) {
                    fail(path, "persisted INCOMPLETE requires exact invalid_fields=[day_mode_cap]")
                }
            } else if (invalidFields.isNotEmpty()) {
                fail(path, "invalid_fields must be empty outside persisted INCOMPLETE")
            }
            val capApplied = value.requiredBoolean("cap_applied", path)
            if (capApplied != (base != null && effective != null && modeRank(effective) < modeRank(base))) fail(path, "cap_applied mismatch")
        },
    )
    return spec(
        EventNameV1.DECISION_EVALUATED,
        DecisionEvaluatedPropertiesV1::class,
        schema,
        ::DecisionEvaluatedPropertiesV1,
        once("decision_evaluated", "decision_id"),
        refs("check_in_id" to RefTargetTypeV1.CHECK_IN),
    )
}

private fun routineStartBlockedSpec(): EventSpecV1<RoutineStartBlockedPropertiesV1> {
    val schema = objectSchema(
        "routine_start_blocked",
        required("gate", EnumShapeV1(listOf(
            "SAFETY_LOCKED", "PENDING_SAFETY_FEEDBACK", "SESSION_ALREADY_ACTIVE", "SCOPE_REACK_REQUIRED",
            "RECONFIRM_REQUIRED", "EXPIRED", "OUTCOME_HAS_NO_ROUTINE", "MODE_NOT_ALLOWED", "CONTRACT_ERROR",
        ))),
        optional("reason", reconfirmReasonShape),
        cross = { value, path ->
            if ((value.requiredString("gate", path) == "RECONFIRM_REQUIRED") != value.hasKey("reason")) {
                fail(path, "reason must be present iff gate=RECONFIRM_REQUIRED")
            }
        },
    )
    return spec(
        EventNameV1.ROUTINE_START_BLOCKED,
        RoutineStartBlockedPropertiesV1::class,
        schema,
        ::RoutineStartBlockedPropertiesV1,
        repeat(EventNameV1.ROUTINE_START_BLOCKED),
    )
}

private fun recommendationShownSpec(): EventSpecV1<RecommendationShownPropertiesV1> {
    val schema = objectSchema(
        "recommendation_shown",
        required("routine_id", routineIdShape),
        required("base_mode", modeShape),
        required("decision_effective_mode", modeShape),
        required("runtime_effective_mode", modeShape),
        required("cap_applied", BooleanShapeV1()),
        required("runtime_day_mode_cap_snapshot", NullableShapeV1(ObjectShapeV1(DayModeCapSnapshotSchemaV1))),
        cross = { value, path ->
            validateProjectionProperties(value, path)
            RoutineModeCatalogV1.requireMode(
                routineId = value.requiredString("routine_id", path),
                mode = value.requiredString("runtime_effective_mode", path),
                path = path,
                modeName = "runtime_effective_mode",
            )
        },
    )
    return spec(
        EventNameV1.RECOMMENDATION_SHOWN,
        RecommendationShownPropertiesV1::class,
        schema,
        ::RecommendationShownPropertiesV1,
        repeat(EventNameV1.RECOMMENDATION_SHOWN),
        EventRefPlanV1(listOf(
            EventAdditionalRefV1("runtime_day_mode_cap_snapshot.mode_trigger_session_id", RefTargetTypeV1.SESSION, true),
            EventAdditionalRefV1("runtime_day_mode_cap_snapshot.source_session_id", RefTargetTypeV1.SESSION, true),
        )),
    )
}

private fun sideEffectOriginSchema(name: String, includeKindSource: Boolean): ClosedObjectSchemaV1 {
    val fields = mutableListOf<WireFieldV1>()
    if (includeKindSource) {
        fields += required("kind", EnumShapeV1(listOf(
            "RED_FLAG", "ACUTE_ILLNESS", "NEW_OR_WORSENING_PAIN_OR_INJURY", "MEDICALLY_RESTRICTED", "POST_SESSION_NEW_OR_WORSE_PAIN",
        )))
        fields += required("source_type", EnumShapeV1(listOf("check_in", "session")))
        fields += optional("source_id", UuidShapeV1)
    }
    fields += listOf(
        required("origin_local_date", DateShapeV1),
        required("origin_timezone_id", ZoneIdShapeV1),
        required("expires_at_utc", InstantShapeV1),
        required("rule_version", Int64ShapeV1(literal = 1)),
    )
    return ClosedObjectSchemaV1("${name}PropertiesV1", fields)
}

private fun routineSelectedSpec(): EventSpecV1<RoutineSelectedPropertiesV1> {
    val schema = objectSchema(
        "routine_selected",
        required("routine_id", routineIdShape),
        required("routine_mode", modeShape),
        required("runtime_effective_mode", modeShape),
        required("selection", EnumShapeV1(listOf("recommended", "same_mode", "lighter_mode"))),
        required("runtime_day_mode_cap_snapshot", NullableShapeV1(ObjectShapeV1(DayModeCapSnapshotSchemaV1))),
        cross = { value, path ->
            val routine = value.requiredString("routine_mode", path)
            val runtime = value.requiredString("runtime_effective_mode", path)
            val selection = value.requiredString("selection", path)
            RoutineModeCatalogV1.requireMode(value.requiredString("routine_id", path), routine, path, "routine_mode")
            if (modeRank(routine) > modeRank(runtime)) fail(path, "selected routine exceeds runtime ceiling")
            if ((selection == "lighter_mode") != (modeRank(routine) < modeRank(runtime))) fail(path, "selection label/mode mismatch")
            if (selection in listOf("recommended", "same_mode") && routine != runtime) fail(path, "recommended/same_mode requires equal mode")
            value.requiredElement("runtime_day_mode_cap_snapshot", path).takeUnless { it === JsonNull }?.let { snapshotElement ->
                val snapshot = snapshotElement.asStrictObject("$path.runtime_day_mode_cap_snapshot")
                val before = snapshot.requiredString("max_mode", "$path.runtime_day_mode_cap_snapshot")
                if (modeRank(runtime) > modeRank(before)) fail(path, "runtime exceeds attached cap")
            }
        },
    )
    return spec(
        EventNameV1.ROUTINE_SELECTED,
        RoutineSelectedPropertiesV1::class,
        schema,
        ::RoutineSelectedPropertiesV1,
        repeat(EventNameV1.ROUTINE_SELECTED),
        EventRefPlanV1(listOf(
            EventAdditionalRefV1("runtime_day_mode_cap_snapshot.mode_trigger_session_id", RefTargetTypeV1.SESSION, true),
            EventAdditionalRefV1("runtime_day_mode_cap_snapshot.source_session_id", RefTargetTypeV1.SESSION, true),
        )),
    )
}

private fun <P : EventPropertiesV1> elapsedSpec(
    name: EventNameV1,
    type: kotlin.reflect.KClass<P>,
    factory: (StrictJsonObjectV1) -> P,
): EventSpecV1<P> = spec(
    name,
    type,
    objectSchema(name.wire, required("elapsed_ms", NonNegativeInt64ShapeV1)),
    factory,
    repeat(name),
)

private fun routineCompletedSpec(): EventSpecV1<RoutineCompletedPropertiesV1> = spec(
    EventNameV1.ROUTINE_COMPLETED,
    RoutineCompletedPropertiesV1::class,
    objectSchema(
        "routine_completed",
        required("routine_id", routineIdShape),
        required("duration_ms", NonNegativeInt64ShapeV1),
        required("step_skip_count", NonNegativeInt64ShapeV1),
        required("pain_gate_status", EnumShapeV1(listOf("PENDING"))),
        required("completion_boot_marker", NonNegativeInt64ShapeV1),
        required("completion_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
        required("completion_clock_generation", NonNegativeInt64ShapeV1),
        required("completion_wall_minus_elapsed_ms", Int64ShapeV1()),
    ),
    ::RoutineCompletedPropertiesV1,
    once("routine_terminal", "session_id"),
)

private fun feedbackUpdatedSpec(): EventSpecV1<FeedbackUpdatedPropertiesV1> {
    val fieldOrder = listOf("effort", "context_fit")
    val schema = objectSchema(
        "feedback_updated",
        required("updated_fields", ArrayShapeV1(EnumShapeV1(fieldOrder), 1, 2) { array, path -> array.requireUniqueStringsInCanonicalOrder(path, fieldOrder, nonEmpty = true) }),
        required("terminal_state", EnumShapeV1(listOf("completed", "stopped", "abandoned"))),
        required("effort", NullableShapeV1(EnumShapeV1(listOf("easy", "moderate", "too_hard")))),
        required("context_fit", NullableShapeV1(EnumShapeV1(listOf("yes", "no")))),
        required("feedback_complete", BooleanShapeV1()),
        required("cap_result", EnumShapeV1(listOf("applied", "not_too_hard", "pain_not_no", "origin_day_expired", "no_effort_transition"))),
        cross = { value, path ->
            val updated = value.requiredElement("updated_fields", path).asArray("$path.updated_fields").map { it.asString("$path.updated_fields") }
            if ("effort" in updated && value.nullableString("effort", path) == null) fail(path, "updated effort must be non-null")
            if ("context_fit" in updated && value.nullableString("context_fit", path) == null) fail(path, "updated context_fit must be non-null")
            val effort = value.nullableString("effort", path)
            val result = value.requiredString("cap_result", path)
            val allowedResults = when {
                "effort" !in updated -> setOf("no_effort_transition")
                effort in listOf("easy", "moderate") -> setOf("not_too_hard")
                else -> setOf("applied", "pain_not_no", "origin_day_expired")
            }
            if (result !in allowedResults) fail(path, "cap_result/effort transition matrix mismatch")
            if (value.requiredBoolean("feedback_complete", path) &&
                (effort == null || value.nullableString("context_fit", path) == null)
            ) {
                fail(path, "feedback_complete cannot be true while an optional feedback field is null")
            }
        },
    )
    return spec(
        EventNameV1.FEEDBACK_UPDATED,
        FeedbackUpdatedPropertiesV1::class,
        schema,
        ::FeedbackUpdatedPropertiesV1,
        once("feedback_updated", "session_id", "updated_fields"),
    )
}

private fun dayModeCapUpdatedSpec(): EventSpecV1<DayModeCapUpdatedPropertiesV1> = spec(
    EventNameV1.DAY_MODE_CAP_UPDATED,
    DayModeCapUpdatedPropertiesV1::class,
    objectSchema(
        "day_mode_cap_updated",
        required("expiry_source_session_id", UuidShapeV1),
        required("basis_mode", modeShape),
        required("previous_cap", NullableShapeV1(EnumShapeV1(listOf("RECOVER", "MAINTAIN")))),
        required("new_cap", EnumShapeV1(listOf("RECOVER", "MAINTAIN"))),
        required("deadline_source", EnumShapeV1(listOf("existing_later", "candidate_later", "same"))),
        required("origin_occurred_at_utc", InstantShapeV1),
        required("origin_local_date", DateShapeV1),
        required("origin_timezone_id", ZoneIdShapeV1),
        required("origin_utc_offset_minutes", Int64ShapeV1(-1080, 1080)),
        required("expires_at_utc", InstantShapeV1),
        required("rule_version", Int64ShapeV1(literal = 1)),
        cross = { value, path ->
            val instant = InstantWireV1.parse(value.requiredString("origin_occurred_at_utc", path))
            val date = DateWireV1.parse(value.requiredString("origin_local_date", path))
            LocalStampWireV1(instant, date, value.requiredString("origin_timezone_id", path), value.requiredInt64("origin_utc_offset_minutes", path))
            val previous = value.nullableString("previous_cap", path)
            val resulting = value.requiredString("new_cap", path)
            if (previous != null && modeRank(resulting) > modeRank(previous)) fail(path, "new cap must not be heavier than previous cap")
        },
    ),
    ::DayModeCapUpdatedPropertiesV1,
    once("day_mode_cap_updated", "session_id"),
    refs("expiry_source_session_id" to RefTargetTypeV1.SESSION),
)

private fun reminderPostedSpec(): EventSpecV1<ReminderPostedPropertiesV1> = spec(
    EventNameV1.REMINDER_POSTED,
    ReminderPostedPropertiesV1::class,
    objectSchema(
        "reminder_posted",
        required("kind", EnumShapeV1(listOf("fixed", "snooze"))),
        required("due_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("delivered_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("lateness_ms", NonNegativeInt64ShapeV1),
        cross = { value, path ->
            val due = value.requiredElement("due_at", path).asStrictObject("$path.due_at")
            val delivered = value.requiredElement("delivered_at", path).asStrictObject("$path.delivered_at")
            val dueInstant = InstantWireV1.parse(due.requiredString("occurred_at_utc", "$path.due_at"))
            val deliveredInstant = InstantWireV1.parse(delivered.requiredString("occurred_at_utc", "$path.delivered_at"))
            val calculated = try { deliveredInstant.instant.toEpochMilli() - dueInstant.instant.toEpochMilli() } catch (_: ArithmeticException) { fail(path, "lateness overflow") }
            if (calculated < 0 || calculated != value.requiredInt64("lateness_ms", path)) fail(path, "lateness_ms does not mirror due/delivered stamps")
            if (calculated > 3_600_000L) fail(path, "reminder_posted lateness_ms exceeds the one-hour delivery limit")
        },
    ),
    ::ReminderPostedPropertiesV1,
    once("reminder_delivery_resolution", "reminder_occurrence_id"),
)

private fun reminderSnoozedSpec(): EventSpecV1<ReminderSnoozedPropertiesV1> = spec(
    EventNameV1.REMINDER_SNOOZED,
    ReminderSnoozedPropertiesV1::class,
    objectSchema(
        "reminder_snoozed",
        required("snooze_occurrence_id", UuidShapeV1),
        required("duration_minutes", Int64ShapeV1(15, 60)),
        required("target_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("ordinal", Int64ShapeV1(literal = 0)),
        required("supersedes_occurrence_id", NullableShapeV1(UuidShapeV1)),
        cross = { value, path ->
            if (value.requiredInt64("duration_minutes", path) !in listOf(15L, 30L, 60L)) fail(path, "duration_minutes must be 15, 30 or 60")
            if (!value.isNull("supersedes_occurrence_id")) fail(path, "snooze supersedes_occurrence_id must be null")
        },
    ),
    ::ReminderSnoozedPropertiesV1,
    once("reminder_snoozed", "snooze_occurrence_id"),
    EventRefPlanV1(listOf(
        EventAdditionalRefV1("snooze_occurrence_id", RefTargetTypeV1.REMINDER_OCCURRENCE),
        EventAdditionalRefV1("supersedes_occurrence_id", RefTargetTypeV1.REMINDER_OCCURRENCE, true),
    )),
)

private fun reminderMergedSpec(): EventSpecV1<ReminderMergedPropertiesV1> = spec(
    EventNameV1.REMINDER_MERGED,
    ReminderMergedPropertiesV1::class,
    objectSchema(
        "reminder_merged",
        required("kept_occurrence_id", UuidShapeV1),
        required("distance_ms", Int64ShapeV1(0, 1_800_000)),
        required("tie_break", EnumShapeV1(listOf("earlier_due", "snooze_over_fixed"))),
        cross = { value, path ->
            if ((value.requiredInt64("distance_ms", path) == 0L) != (value.requiredString("tie_break", path) == "snooze_over_fixed")) {
                fail(path, "distance/tie_break matrix mismatch")
            }
        },
    ),
    ::ReminderMergedPropertiesV1,
    once("reminder_delivery_resolution", "reminder_occurrence_id"),
    refs("kept_occurrence_id" to RefTargetTypeV1.REMINDER_OCCURRENCE),
)

private fun reminderCancelledSpec(): EventSpecV1<ReminderCancelledPropertiesV1> = spec(
    EventNameV1.REMINDER_CANCELLED,
    ReminderCancelledPropertiesV1::class,
    objectSchema(
        "reminder_cancelled",
        required("reason", EnumShapeV1(listOf("schedule_edit", "permission_revoked", "timezone_change", "safety_hold", "rest_only", "active_session", "pending_pain"))),
        required("resulting_status", EnumShapeV1(listOf("CANCELLED", "BLOCKED_PERMISSION"))),
        cross = { value, path ->
            val permission = value.requiredString("reason", path) == "permission_revoked"
            val blocked = value.requiredString("resulting_status", path) == "BLOCKED_PERMISSION"
            if (permission != blocked) fail(path, "permission_revoked/resulting_status matrix mismatch")
        },
    ),
    ::ReminderCancelledPropertiesV1,
    once("reminder_delivery_resolution", "reminder_occurrence_id"),
)

private val logicalFixedKeySchema = ClosedObjectSchemaV1(
    "LogicalFixedKeyV1",
    listOf(
        required("schedule_version_id", UuidShapeV1),
        required("slot_index", Int64ShapeV1(0, 1)),
        required("local_date", DateShapeV1),
        required("kind", EnumShapeV1(listOf("fixed"))),
    ),
)

private fun reminderScheduledSpec(): EventSpecV1<ReminderScheduledPropertiesV1> {
    val schema = objectSchema(
        "reminder_scheduled",
        required("due_at", ObjectShapeV1(LocalStampSchemaV1)),
        required("kind", EnumShapeV1(listOf("fixed", "snooze"))),
        required("supersedes_occurrence_id", NullableShapeV1(UuidShapeV1)),
        optional("logical_fixed_key", ObjectShapeV1(logicalFixedKeySchema)),
        optional("generation", NonNegativeInt64ShapeV1),
        optional("creation_reason", EnumShapeV1(listOf("initial", "slot_reeligible"))),
        optional("parent_occurrence_id", UuidShapeV1),
        optional("ordinal", Int64ShapeV1(literal = 0)),
        cross = { value, path ->
            val fixedKeys = listOf("logical_fixed_key", "generation", "creation_reason")
            val snoozeKeys = listOf("parent_occurrence_id", "ordinal")
            if (value.requiredString("kind", path) == "fixed") {
                if (fixedKeys.any { !value.hasKey(it) } || snoozeKeys.any { value.hasKey(it) }) fail(path, "fixed scheduled branch mismatch")
                val generation = value.requiredInt64("generation", path)
                val reason = value.requiredString("creation_reason", path)
                if ((generation == 0L) != (reason == "initial")) fail(path, "generation/reason mismatch")
                if ((generation == 0L) != value.isNull("supersedes_occurrence_id")) fail(path, "generation/supersedes mismatch")
            } else {
                if (snoozeKeys.any { !value.hasKey(it) } || fixedKeys.any { value.hasKey(it) } || !value.isNull("supersedes_occurrence_id")) fail(path, "snooze scheduled branch mismatch")
            }
        },
    )
    return spec(
        EventNameV1.REMINDER_SCHEDULED,
        ReminderScheduledPropertiesV1::class,
        schema,
        ::ReminderScheduledPropertiesV1,
        once("reminder_scheduled", "reminder_occurrence_id"),
        EventRefPlanV1(listOf(
            EventAdditionalRefV1("parent_occurrence_id", RefTargetTypeV1.REMINDER_OCCURRENCE, true),
            EventAdditionalRefV1("supersedes_occurrence_id", RefTargetTypeV1.REMINDER_OCCURRENCE, true),
        )),
        conditional = { envelope, properties, path ->
            properties.body["logical_fixed_key"]?.let {
                val nested = it.asStrictObject("$path.properties.logical_fixed_key")
                if (nested.requiredString("schedule_version_id", "$path.properties.logical_fixed_key") != envelope.scheduleVersionId?.value) {
                    fail(path, "logical fixed schedule must equal envelope schedule")
                }
            }
        },
    )
}

private fun safetyHoldCreatedSpec(): EventSpecV1<SafetyHoldCreatedPropertiesV1> {
    val base = sideEffectOriginSchema("safety_hold_created", includeKindSource = true)
    val schema = ClosedObjectSchemaV1(base.name, base.fields) { value, path ->
        val kind = value.requiredString("kind", path)
        val source = value.requiredString("source_type", path)
        if (source == "check_in") {
            if (!value.hasKey("source_id")) fail(path, "check_in source requires source_id")
            if (kind == "POST_SESSION_NEW_OR_WORSE_PAIN") fail(path, "post-session kind requires session source")
        } else {
            if (value.hasKey("source_id")) fail(path, "session source forbids duplicate source_id property")
            if (kind != "POST_SESSION_NEW_OR_WORSE_PAIN") fail(path, "session source requires post-session hold kind")
        }
    }
    return spec(
        EventNameV1.SAFETY_HOLD_CREATED,
        SafetyHoldCreatedPropertiesV1::class,
        schema,
        ::SafetyHoldCreatedPropertiesV1,
        once("safety_hold_created", "source_type", "source_id"),
        EventRefPlanV1(listOf(EventAdditionalRefV1("source_id", RefTargetTypeV1.CHECK_IN, true))),
        conditional = { envelope, properties, path ->
            val source = properties.body.requiredString("source_type", "$path.properties")
            if ((source == "session") != (envelope.sessionId != null)) fail(path, "safety hold envelope/property source matrix mismatch")
        },
    )
}

private fun safetyScreenShownSpec(): EventSpecV1<SafetyScreenShownPropertiesV1> {
    val schema = objectSchema(
        "safety_screen_shown",
        required("result", EnumShapeV1(listOf("URGENT_STOP", "PAUSE_TODAY", "BLOCKED_FOR_TODAY"))),
        required("route_id", EnumShapeV1(listOf(
            "urgent_stop", "pause_acute_illness", "pause_new_or_worsening_pain_or_injury", "pause_medically_restricted",
            "blocked_red_flag", "blocked_acute_illness", "blocked_new_or_worsening_pain_or_injury", "blocked_medically_restricted",
            "blocked_post_session_new_or_worse_pain",
        ))),
        required("content_digest", DigestShapeV1),
        cross = { value, path ->
            val result = value.requiredString("result", path)
            val route = value.requiredString("route_id", path)
            if (result == "URGENT_STOP" && route != "urgent_stop") fail(path, "urgent route mismatch")
            if (result == "PAUSE_TODAY" && !route.startsWith("pause_")) fail(path, "pause route mismatch")
            if (result == "BLOCKED_FOR_TODAY" && !route.startsWith("blocked_")) fail(path, "blocked route mismatch")
        },
    )
    return spec(
        EventNameV1.SAFETY_SCREEN_SHOWN,
        SafetyScreenShownPropertiesV1::class,
        schema,
        ::SafetyScreenShownPropertiesV1,
        repeat(EventNameV1.SAFETY_SCREEN_SHOWN),
        conditional = { envelope, properties, path ->
            val result = properties.body.requiredString("result", "$path.properties")
            val route = properties.body.requiredString("route_id", "$path.properties")
            when {
                result in listOf("URGENT_STOP", "PAUSE_TODAY") -> if (envelope.decisionId == null || envelope.sessionId != null) fail(path, "immediate safety route requires Decision only")
                route == "blocked_post_session_new_or_worse_pain" -> if (envelope.sessionId == null || envelope.decisionId != null) fail(path, "post-session blocked route requires Session only")
                else -> if (envelope.decisionId != null || envelope.sessionId != null) fail(path, "check-in hold rerender forbids Decision/Session envelope")
            }
        },
    )
}

private fun routineStartedSpec(): EventSpecV1<RoutineStartedPropertiesV1> {
    val schema = timingXorSchema(
        "routine_started",
        "total_duration_ms",
        "total_timing_invalid_reason",
        listOf("same_boot_unavailable", "elapsed_rollback", "overflow", "background_over_10m"),
        leading = listOf(
            required("routine_id", routineIdShape),
            required("check_in_flow_id", UuidShapeV1),
            required("runtime_effective_mode_at_start", modeShape),
            required("is_selected_workday_at_start", BooleanShapeV1()),
            required("start_boot_marker", NonNegativeInt64ShapeV1),
            required("start_elapsed_realtime_ms", NonNegativeInt64ShapeV1),
            required("start_clock_generation", NonNegativeInt64ShapeV1),
            required("start_wall_minus_elapsed_ms", Int64ShapeV1()),
        ),
        cross = { value, path ->
            val routineMode = RoutineModeCatalogV1.modeFor(value.requiredString("routine_id", path), path)
            val runtimeMode = value.requiredString("runtime_effective_mode_at_start", path)
            if (modeRank(routineMode) > modeRank(runtimeMode)) {
                fail(path, "started routine exceeds runtime ceiling")
            }
        },
    )
    return spec(
        EventNameV1.ROUTINE_STARTED,
        RoutineStartedPropertiesV1::class,
        schema,
        ::RoutineStartedPropertiesV1,
        once("routine_started", "session_id"),
        conditional = { envelope, _, path ->
            when (envelope.source) {
                EventSourceV1.HOME -> if (envelope.reminderOccurrenceId != null) fail(path, "home start forbids reminder occurrence")
                EventSourceV1.REMINDER -> if (envelope.reminderOccurrenceId == null) fail(path, "reminder start requires occurrence")
                null -> fail(path, "routine_started requires source")
            }
        },
    )
}

private fun validateProjectionProperties(value: StrictJsonObjectV1, path: String) {
    val base = value.requiredString("base_mode", path)
    val decision = value.requiredString("decision_effective_mode", path)
    val runtime = value.requiredString("runtime_effective_mode", path)
    if (modeRank(runtime) > modeRank(decision) || modeRank(decision) > modeRank(base)) fail(path, "runtime <= decision <= base invariant failed")
    val capApplied = value.requiredBoolean("cap_applied", path)
    if (capApplied != (modeRank(runtime) < modeRank(base))) fail(path, "cap_applied must mirror runtime < base")
    val snapshotElement = value.requiredElement("runtime_day_mode_cap_snapshot", path)
    if ((snapshotElement !== JsonNull) != (modeRank(runtime) < modeRank(decision))) fail(path, "runtime cap snapshot conditional mismatch")
    if (snapshotElement !== JsonNull) {
        val snapshot = snapshotElement.asStrictObject("$path.runtime_day_mode_cap_snapshot")
        val cap = snapshot.requiredString("max_mode", "$path.runtime_day_mode_cap_snapshot")
        if (modeRank(runtime) != minOf(modeRank(decision), modeRank(cap))) fail(path, "runtime mode does not equal min(decision, cap)")
    }
}
