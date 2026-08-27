package vn.nhip2phut.domain.events

enum class EnvelopeSlotRule {
    REQUIRED,
    CONDITIONAL,
    FORBIDDEN,
}

data class EventEnvelopeMaskV1(
    val decisionId: EnvelopeSlotRule,
    val sessionId: EnvelopeSlotRule,
    val reminderOccurrenceId: EnvelopeSlotRule,
    val scheduleVersionId: EnvelopeSlotRule,
    val source: EnvelopeSlotRule,
)

enum class EventNameV1(val wire: String) {
    APP_FIRST_OPENED("app_first_opened"),
    ONBOARDING_STARTED("onboarding_started"),
    AGE_GATE_ANSWERED("age_gate_answered"),
    SCOPE_ACKNOWLEDGED("scope_acknowledged"),
    SCOPE_REACK_REQUIRED("scope_reack_required"),
    SCOPE_REACK_COMPLETED("scope_reack_completed"),
    NOTIFICATION_PERMISSION_PROMPTED("notification_permission_prompted"),
    NOTIFICATION_PERMISSION_UPDATED("notification_permission_updated"),
    ONBOARDING_COMPLETED("onboarding_completed"),
    CHECK_IN_STARTED("check_in_started"),
    CHECK_IN_RECONFIRMATION_REQUIRED("check_in_reconfirmation_required"),
    REST_SUPPRESSION_SUPERSEDED("rest_suppression_superseded"),
    WEEKLY_SUMMARY_GENERATED("weekly_summary_generated"),
    WEEKLY_SUMMARY_VIEWED("weekly_summary_viewed"),
    EXPORT_STARTED("export_started"),
    EXPORT_COMPLETED("export_completed"),
    EXPORT_FAILED("export_failed"),
    WORK_SCHEDULE_SAVED("work_schedule_saved"),
    SCHEDULE_RECONCILED("schedule_reconciled"),
    CHECK_IN_SUBMITTED("check_in_submitted"),
    DECISION_EVALUATED("decision_evaluated"),
    ROUTINE_START_BLOCKED("routine_start_blocked"),
    RECOMMENDATION_SHOWN("recommendation_shown"),
    REST_SUPPRESSION_CREATED("rest_suppression_created"),
    ROUTINE_SELECTED("routine_selected"),
    ROUTINE_PAUSED("routine_paused"),
    ROUTINE_RESUMED("routine_resumed"),
    ROUTINE_RECOVERY_OFFERED("routine_recovery_offered"),
    ROUTINE_RECOVERY_FAILED("routine_recovery_failed"),
    ROUTINE_STEP_SKIPPED("routine_step_skipped"),
    ROUTINE_STOPPED("routine_stopped"),
    ROUTINE_ABANDONED("routine_abandoned"),
    ROUTINE_COMPLETED("routine_completed"),
    PAIN_GATE_RESOLVED("pain_gate_resolved"),
    FEEDBACK_UPDATED("feedback_updated"),
    DAY_MODE_CAP_UPDATED("day_mode_cap_updated"),
    REMINDER_POSTED("reminder_posted"),
    REMINDER_OPENED("reminder_opened"),
    REMINDER_SNOOZED("reminder_snoozed"),
    REMINDER_DISMISSED("reminder_dismissed"),
    REMINDER_MERGED("reminder_merged"),
    REMINDER_CANCELLED("reminder_cancelled"),
    REMINDER_BLOCKED_PERMISSION("reminder_blocked_permission"),
    REMINDER_SKIPPED("reminder_skipped"),
    REMINDER_SCHEDULED("reminder_scheduled"),
    SAFETY_HOLD_CREATED("safety_hold_created"),
    SAFETY_SCREEN_SHOWN("safety_screen_shown"),
    ROUTINE_STARTED("routine_started");

    companion object {
        fun fromWire(wire: String): EventNameV1? = values().firstOrNull { it.wire == wire }
    }
}

object EventContractRegistryV1 {
    private val noEnvelopeSlots = EventEnvelopeMaskV1(
        decisionId = EnvelopeSlotRule.FORBIDDEN,
        sessionId = EnvelopeSlotRule.FORBIDDEN,
        reminderOccurrenceId = EnvelopeSlotRule.FORBIDDEN,
        scheduleVersionId = EnvelopeSlotRule.FORBIDDEN,
        source = EnvelopeSlotRule.FORBIDDEN,
    )

    private val scheduleOnly = noEnvelopeSlots.copy(scheduleVersionId = EnvelopeSlotRule.REQUIRED)
    private val decisionAndSchedule = noEnvelopeSlots.copy(
        decisionId = EnvelopeSlotRule.REQUIRED,
        scheduleVersionId = EnvelopeSlotRule.REQUIRED,
    )
    private val decisionOnly = noEnvelopeSlots.copy(decisionId = EnvelopeSlotRule.REQUIRED)
    private val sessionOnly = noEnvelopeSlots.copy(sessionId = EnvelopeSlotRule.REQUIRED)
    private val reminderOnly = noEnvelopeSlots.copy(reminderOccurrenceId = EnvelopeSlotRule.REQUIRED)
    private val reminderAndSchedule = noEnvelopeSlots.copy(
        reminderOccurrenceId = EnvelopeSlotRule.REQUIRED,
        scheduleVersionId = EnvelopeSlotRule.REQUIRED,
    )
    private val safetyHoldCreated = noEnvelopeSlots.copy(sessionId = EnvelopeSlotRule.CONDITIONAL)
    private val safetyScreenShown = noEnvelopeSlots.copy(
        decisionId = EnvelopeSlotRule.CONDITIONAL,
        sessionId = EnvelopeSlotRule.CONDITIONAL,
    )
    private val routineStarted = EventEnvelopeMaskV1(
        decisionId = EnvelopeSlotRule.REQUIRED,
        sessionId = EnvelopeSlotRule.REQUIRED,
        reminderOccurrenceId = EnvelopeSlotRule.CONDITIONAL,
        scheduleVersionId = EnvelopeSlotRule.REQUIRED,
        source = EnvelopeSlotRule.REQUIRED,
    )

    val masks: Map<EventNameV1, EventEnvelopeMaskV1> = buildMap {
        putAll(
            listOf(
                EventNameV1.APP_FIRST_OPENED,
                EventNameV1.ONBOARDING_STARTED,
                EventNameV1.AGE_GATE_ANSWERED,
                EventNameV1.SCOPE_ACKNOWLEDGED,
                EventNameV1.SCOPE_REACK_REQUIRED,
                EventNameV1.SCOPE_REACK_COMPLETED,
                EventNameV1.NOTIFICATION_PERMISSION_PROMPTED,
                EventNameV1.NOTIFICATION_PERMISSION_UPDATED,
                EventNameV1.ONBOARDING_COMPLETED,
                EventNameV1.CHECK_IN_STARTED,
                EventNameV1.CHECK_IN_RECONFIRMATION_REQUIRED,
                EventNameV1.REST_SUPPRESSION_SUPERSEDED,
                EventNameV1.WEEKLY_SUMMARY_GENERATED,
                EventNameV1.WEEKLY_SUMMARY_VIEWED,
                EventNameV1.EXPORT_STARTED,
                EventNameV1.EXPORT_COMPLETED,
                EventNameV1.EXPORT_FAILED,
            ).associateWith { noEnvelopeSlots },
        )
        putAll(listOf(EventNameV1.WORK_SCHEDULE_SAVED, EventNameV1.SCHEDULE_RECONCILED, EventNameV1.CHECK_IN_SUBMITTED).associateWith { scheduleOnly })
        putAll(listOf(EventNameV1.DECISION_EVALUATED, EventNameV1.ROUTINE_START_BLOCKED).associateWith { decisionAndSchedule })
        putAll(listOf(EventNameV1.RECOMMENDATION_SHOWN, EventNameV1.REST_SUPPRESSION_CREATED, EventNameV1.ROUTINE_SELECTED).associateWith { decisionOnly })
        putAll(
            listOf(
                EventNameV1.ROUTINE_PAUSED,
                EventNameV1.ROUTINE_RESUMED,
                EventNameV1.ROUTINE_RECOVERY_OFFERED,
                EventNameV1.ROUTINE_RECOVERY_FAILED,
                EventNameV1.ROUTINE_STEP_SKIPPED,
                EventNameV1.ROUTINE_STOPPED,
                EventNameV1.ROUTINE_ABANDONED,
                EventNameV1.ROUTINE_COMPLETED,
                EventNameV1.PAIN_GATE_RESOLVED,
                EventNameV1.FEEDBACK_UPDATED,
                EventNameV1.DAY_MODE_CAP_UPDATED,
            ).associateWith { sessionOnly },
        )
        putAll(
            listOf(
                EventNameV1.REMINDER_POSTED,
                EventNameV1.REMINDER_OPENED,
                EventNameV1.REMINDER_SNOOZED,
                EventNameV1.REMINDER_DISMISSED,
                EventNameV1.REMINDER_MERGED,
                EventNameV1.REMINDER_CANCELLED,
                EventNameV1.REMINDER_BLOCKED_PERMISSION,
                EventNameV1.REMINDER_SKIPPED,
            ).associateWith { reminderOnly },
        )
        put(EventNameV1.REMINDER_SCHEDULED, reminderAndSchedule)
        put(EventNameV1.SAFETY_HOLD_CREATED, safetyHoldCreated)
        put(EventNameV1.SAFETY_SCREEN_SHOWN, safetyScreenShown)
        put(EventNameV1.ROUTINE_STARTED, routineStarted)
    }

    init {
        require(EventNameV1.values().size == 48) { "EventNameV1 must cover exactly 48 events." }
        require(masks.size == EventNameV1.values().size) { "Each event must have exactly one envelope mask." }
    }

    fun maskFor(name: EventNameV1): EventEnvelopeMaskV1 = masks.getValue(name)
}

