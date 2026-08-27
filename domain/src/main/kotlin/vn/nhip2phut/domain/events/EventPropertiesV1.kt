package vn.nhip2phut.domain.events

import vn.nhip2phut.domain.wire.v1.LocalStampWireV1
import vn.nhip2phut.domain.wire.v1.StrictJsonObjectV1
import vn.nhip2phut.domain.wire.v1.UuidWireV1
import vn.nhip2phut.domain.wire.v1.asString
import vn.nhip2phut.domain.wire.v1.requiredString
import kotlin.reflect.KClass

sealed interface EventPropertiesV1 {
    val body: StrictJsonObjectV1
}

data class AppFirstOpenedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class OnboardingStartedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class AgeGateAnsweredPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ScopeAcknowledgedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ScopeReackRequiredPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ScopeReackCompletedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class NotificationPermissionPromptedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class NotificationPermissionUpdatedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class OnboardingCompletedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class CheckInStartedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class CheckInReconfirmationRequiredPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RestSuppressionSupersededPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class WeeklySummaryGeneratedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class WeeklySummaryViewedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ExportStartedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ExportCompletedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ExportFailedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class WorkScheduleSavedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ScheduleReconciledPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class CheckInSubmittedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class DecisionEvaluatedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineStartBlockedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RecommendationShownPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RestSuppressionCreatedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineSelectedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutinePausedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineResumedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineRecoveryOfferedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineRecoveryFailedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineStepSkippedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineStoppedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineAbandonedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineCompletedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class PainGateResolvedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class FeedbackUpdatedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class DayModeCapUpdatedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderPostedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderOpenedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderSnoozedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderDismissedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderMergedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderCancelledPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderBlockedPermissionPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderSkippedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class ReminderScheduledPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class SafetyHoldCreatedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class SafetyScreenShownPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1
data class RoutineStartedPropertiesV1 internal constructor(override val body: StrictJsonObjectV1) : EventPropertiesV1

enum class EventSourceV1(val wire: String) {
    HOME("home"),
    REMINDER("reminder");

    companion object {
        fun fromWire(value: String): EventSourceV1? = entries.firstOrNull { it.wire == value }
    }
}

data class EventEnvelopeV1(
    val eventId: UuidWireV1,
    val occurred: LocalStampWireV1,
    val installationId: UuidWireV1,
    val decisionId: UuidWireV1?,
    val sessionId: UuidWireV1?,
    val reminderOccurrenceId: UuidWireV1?,
    val scheduleVersionId: UuidWireV1?,
    val source: EventSourceV1?,
) {
    val eventSchemaVersion: Int = 1
}

data class ProductEventWireV1(
    val name: EventNameV1,
    val envelope: EventEnvelopeV1,
    val properties: EventPropertiesV1,
)

enum class EventIdempotencyKindV1 { AT_MOST_ONCE, REPEATABLE_BY_EVENT_ID }

data class EventIdempotencyPlanV1(
    val kind: EventIdempotencyKindV1,
    val domain: String,
    val orderedSelectors: List<String>,
)

enum class RefTargetTypeV1(val wire: String) {
    APP_PROFILE("app_profile"),
    SAFETY_ACKNOWLEDGEMENT("safety_acknowledgement"),
    WORK_SCHEDULE_VERSION("work_schedule_version"),
    CHECK_IN("check_in"),
    DECISION("decision"),
    SESSION("session"),
    REMINDER_OCCURRENCE("reminder_occurrence"),
    WEEKLY_SUMMARY("weekly_summary"),
}

data class EventAdditionalRefV1(
    val logicalSlot: String,
    val target: RefTargetTypeV1,
    val conditional: Boolean = false,
)

data class EventRefPlanV1(val additionalRefs: List<EventAdditionalRefV1> = emptyList())

enum class CompanionSourceTypeV1(val wire: String) {
    APP_PROFILE("app_profile"),
    SAFETY_ACKNOWLEDGEMENT("safety_acknowledgement"),
    CHECK_IN("check_in"),
    DECISION("decision"),
    SESSION("session"),
    REMINDER_OCCURRENCE("reminder_occurrence"),
    WEEKLY_SUMMARY("weekly_summary"),
}

data class RequiredCompanionRoleV1(
    val role: String,
    val sourceType: CompanionSourceTypeV1,
    val selector: String,
    val conditional: Boolean = false,
)

data class EventCompanionPlanV1(val roles: List<RequiredCompanionRoleV1> = emptyList())

internal data class NormalizedCompanionClaimV1(
    val role: String,
    val sourceType: CompanionSourceTypeV1,
    val sourceId: String,
)

internal object CompanionRolesV1 {
    const val PROFILE_ONBOARDING = "profile_onboarding"
    const val ACK_REACK = "ack_reack"
    const val CHECK_IN_COMMIT = "check_in_commit"
    const val DECISION_COMMIT = "decision_commit"
    const val DECISION_SIDE_EFFECT = "decision_side_effect"
    const val SESSION_START = "session_start"
    const val SESSION_STEP_SKIP = "session_step_skip"
    const val SESSION_TERMINAL = "session_terminal"
    const val SESSION_PAIN_RESOLUTION = "session_pain_resolution"
    const val SESSION_FEEDBACK_TRANSITION = "session_feedback_transition"
    const val SESSION_FEEDBACK_SIDE_EFFECT = "session_feedback_side_effect"
    const val REMINDER_CREATE = "reminder_create"
    const val REMINDER_SNOOZE_EDGE = "reminder_snooze_edge"
    const val REMINDER_DELIVERY = "reminder_delivery"
    const val REMINDER_INTERACTION = "reminder_interaction"
    const val REMINDER_RESOLUTION = "reminder_resolution"
    const val WEEKLY_GENERATION = "weekly_generation"
}

internal object EventCompanionClaimResolverV1 {
    fun resolve(
        event: ProductEventWireV1,
        resolveDerived: (RequiredCompanionRoleV1, ProductEventWireV1) -> String? = { _, _ -> null },
    ): List<NormalizedCompanionClaimV1> {
        val spec = EventContractRegistryV1.specFor(event.name)
        spec.validateAny(event.envelope, event.properties, "${event.name.wire}.companion-claims")
        return spec.companionPlan.roles
            .filter { companionRoleApplies(it, event) }
            .map { role ->
                val rawSourceId = resolveSelector(role.selector, event)
                    ?: resolveDerived(role, event)
                    ?: throw vn.nhip2phut.domain.wire.v1.WireContractException(
                        "${event.name.wire}: companion selector '${role.selector}' is unresolved",
                    )
                val normalizedSourceId = when (role.sourceType) {
                    CompanionSourceTypeV1.APP_PROFILE -> {
                        if (rawSourceId != APP_PROFILE_SINGLETON_ID) {
                            throw vn.nhip2phut.domain.wire.v1.WireContractException(
                                "${event.name.wire}: app-profile companion must use singleton ID 1",
                            )
                        }
                        APP_PROFILE_SINGLETON_ID
                    }

                    else -> UuidWireV1.parse(rawSourceId).value
                }
                NormalizedCompanionClaimV1(role.role, role.sourceType, normalizedSourceId)
            }
            .distinct()
    }

    private fun resolveSelector(selector: String, event: ProductEventWireV1): String? = when (selector) {
        APP_PROFILE_SELECTOR -> APP_PROFILE_SINGLETON_ID
        "installation_id" -> event.envelope.installationId.value
        "decision_id" -> event.envelope.decisionId?.value
        "session_id" -> event.envelope.sessionId?.value
        "reminder_occurrence_id" -> event.envelope.reminderOccurrenceId?.value
        "schedule_version_id" -> event.envelope.scheduleVersionId?.value
        else -> event.properties.body[selector]
            ?.takeUnless { it === kotlinx.serialization.json.JsonNull }
            ?.asString("${event.name.wire}.properties.$selector")
    }

    private const val APP_PROFILE_SELECTOR = "app_profile_singleton"
    private const val APP_PROFILE_SINGLETON_ID = "1"
}

class EventSpecV1<P : EventPropertiesV1> internal constructor(
    val name: EventNameV1,
    val envelopeMask: EventEnvelopeMaskV1,
    val propertiesType: KClass<P>,
    val propertyKeys: List<String>,
    val refPlan: EventRefPlanV1,
    val companionPlan: EventCompanionPlanV1,
    internal val decodeProperties: (StrictJsonObjectV1, String) -> P,
    internal val encodeProperties: (P, String) -> StrictJsonObjectV1,
    internal val validateEnvelopeAndProperties: (EventEnvelopeV1, P, String) -> Unit,
    internal val resolveIdempotency: (P) -> EventIdempotencyPlanV1,
) {
    fun idempotencyFor(properties: P): EventIdempotencyPlanV1 = resolveIdempotency(properties)

    fun validate(envelope: EventEnvelopeV1, properties: P) =
        validateEnvelopeAndProperties(envelope, properties, "${name.wire}.draft")

    internal fun decodeAny(raw: StrictJsonObjectV1, path: String): EventPropertiesV1 = decodeProperties(raw, path)

    internal fun encodeAny(properties: EventPropertiesV1, path: String): StrictJsonObjectV1 {
        if (properties::class != propertiesType) {
            throw vn.nhip2phut.domain.wire.v1.WireContractException(
                "$path: ${name.wire} requires ${propertiesType.simpleName}, got ${properties::class.simpleName}",
            )
        }
        @Suppress("UNCHECKED_CAST")
        return encodeProperties(properties as P, path)
    }

    internal fun validateAny(envelope: EventEnvelopeV1, properties: EventPropertiesV1, path: String) {
        if (properties::class != propertiesType) {
            throw vn.nhip2phut.domain.wire.v1.WireContractException("$path: property DTO does not match ${name.wire}")
        }
        @Suppress("UNCHECKED_CAST")
        validateEnvelopeAndProperties(envelope, properties as P, path)
    }

    internal fun idempotencyAny(properties: EventPropertiesV1): EventIdempotencyPlanV1 {
        if (properties::class != propertiesType) {
            throw vn.nhip2phut.domain.wire.v1.WireContractException("property DTO does not match ${name.wire}")
        }
        @Suppress("UNCHECKED_CAST")
        return resolveIdempotency(properties as P)
    }
}

/** Executes companion plans without coupling the domain contract to a storage implementation. */
object EventCompanionPlanExecutorV1 {
    fun requireCompanions(
        event: ProductEventWireV1,
        exists: (RequiredCompanionRoleV1, ProductEventWireV1) -> Boolean,
    ) {
        val spec = EventContractRegistryV1.specFor(event.name)
        spec.validateAny(event.envelope, event.properties, "${event.name.wire}.companion-plan")
        spec.companionPlan.roles.filter { companionRoleApplies(it, event) }.forEach { role ->
            if (!exists(role, event)) {
                throw vn.nhip2phut.domain.wire.v1.WireContractException(
                    "${event.name.wire}: missing required companion '${role.role}'",
                )
            }
        }
    }

}

private fun companionRoleApplies(role: RequiredCompanionRoleV1, event: ProductEventWireV1): Boolean {
    if (!role.conditional) return true
    if (event.name != EventNameV1.SAFETY_HOLD_CREATED) return true
    val source = event.properties.body.requiredString("source_type", "safety_hold_created.properties")
    return when (role.sourceType) {
        CompanionSourceTypeV1.DECISION -> source == "check_in"
        CompanionSourceTypeV1.SESSION -> source == "session"
        else -> true
    }
}

class TypedProductEventDraft<P : EventPropertiesV1> private constructor(
    val spec: EventSpecV1<P>,
    val envelope: EventEnvelopeV1,
    val properties: P,
) {
    val eventId: UuidWireV1 get() = envelope.eventId

    companion object {
        fun <P : EventPropertiesV1> create(
            spec: EventSpecV1<P>,
            envelope: EventEnvelopeV1,
            properties: P,
        ): TypedProductEventDraft<P> {
            spec.validateEnvelopeAndProperties(envelope, properties, "TypedProductEventDraft")
            return TypedProductEventDraft(spec, envelope, properties)
        }
    }
}
