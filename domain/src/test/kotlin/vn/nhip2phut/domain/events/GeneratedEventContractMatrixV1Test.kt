package vn.nhip2phut.domain.events

import kotlinx.serialization.json.*
import vn.nhip2phut.domain.wire.v1.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratedEventContractMatrixV1Test {
    private val id = UuidWireV1.parse("00000000-0000-4000-8000-000000000001")
    private val relatedId = UuidWireV1.parse("00000000-0000-4000-8000-000000000002")
    private val stamp = LocalStampWireV1(InstantWireV1.parse("2026-08-27T08:00:00.000Z"), DateWireV1.parse("2026-08-27"), "UTC", 0)
    private val monday = DateWireV1.parse("2026-08-24")
    private val semver = SemVerWireV1.parse("1.0.0")
    private val digest = Sha256DigestWireV1.parse("0".repeat(64))

    @Test
    fun all48TypedDraftsRoundTripThroughTheirCanonicalSpec() {
        val drafts = typedProperties()
        assertEquals(EventNameV1.entries.toSet(), drafts.keys)
        assertEquals(48, drafts.values.map { it::class }.distinct().size)

        drafts.forEach { (name, properties) ->
            val event = ProductEventWireV1(name, envelopeFor(name), properties)
            val encoded = ClosedCodecV1.encodeEvent(event)
            assertEquals(event, ClosedCodecV1.decodeEvent(encoded), name.wire)
            assertEquals(
                EventIdempotencyCodecV1.logicalKey(event).parts.map { it.name },
                EventContractRegistryV1.specFor(name).idempotencyAny(properties).orderedSelectors,
                name.wire,
            )
        }
    }

    @Test
    fun all48RejectGeneratedClosedSchemaAndEnvelopeMutations() {
        typedProperties().forEach { (name, properties) ->
            val valid = ClosedCodecV1.encodeEvent(ProductEventWireV1(name, envelopeFor(name), properties))
            val root = Json.parseToJsonElement(valid).jsonObject
            val propertyObject = root.getValue("properties").jsonObject
            val mutants = mutableListOf<Pair<String, String>>()
            val acceptedNullFlips = mutableListOf<Pair<String, String>>()
            mutants += "unknown-non-null" to mutateProperties(root) {
                JsonObject(linkedMapOf("__unknown" to JsonPrimitive(true)) + it)
            }
            mutants += "unknown-null" to mutateProperties(root) {
                JsonObject(linkedMapOf("__unknown" to JsonNull) + it)
            }
            propertyObject.forEach { (key, value) ->
                mutants += "missing-property:$key" to mutateProperties(root) {
                    JsonObject(it.filterKeys { candidate -> candidate != key })
                }
                mutants += "duplicate-property:$key" to duplicateProperty(valid, key)
                mutants += "wrong-type:$key" to mutateProperties(root) {
                    JsonObject(it + (key to wrongTypeFor(value)))
                }
                if (value is JsonNull) {
                    mutants += "unexpected-non-null:$key" to mutateProperties(root) {
                        JsonObject(it + (key to nonNullFlipFor(name, key)))
                    }
                } else if (allowsIndependentNull(name, key)) {
                    val nullable = mutateProperties(root) { JsonObject(it + (key to JsonNull)) }
                    acceptedNullFlips += "allowed-null:$key" to nullable
                } else {
                    mutants += "unexpected-null:$key" to mutateProperties(root) {
                        JsonObject(it + (key to JsonNull))
                    }
                }
            }
            mutants += "wrong-case-name" to JsonObject(root + ("name" to JsonPrimitive(name.wire.uppercase()))).toString()
            envelopeMutants(root, EventContractRegistryV1.maskFor(name)).forEach { mutants += it }
            assertEquals(
                3 + propertyObject.size * 4 + 5,
                mutants.size + acceptedNullFlips.size,
                "${name.wire}: every present property must have four closed-schema mutations plus name/envelope coverage",
            )
            mutants.forEach { (label, mutant) ->
                val failure = assertFailsWith<WireContractException>("${name.wire}: $label") {
                    ClosedCodecV1.decodeEvent(mutant)
                }
                if (label.startsWith("duplicate-property:")) {
                    assertContains(failure.message.orEmpty(), "duplicate", ignoreCase = true)
                }
            }
            acceptedNullFlips.forEach { (label, mutant) ->
                val decoded = ClosedCodecV1.decodeEvent(mutant)
                assertEquals(JsonNull, decoded.properties.body[label.substringAfter(':')], "${name.wire}: $label")
            }
        }
    }

    @Test
    fun generatedCompanionPlansAreExecutableForEveryDeclaredRole() {
        typedProperties().forEach { (name, properties) ->
            val event = ProductEventWireV1(name, envelopeFor(name), properties)
            val applicableRoles = applicableCompanionRoles(event)
            val visitedRoles = mutableListOf<RequiredCompanionRoleV1>()
            EventCompanionPlanExecutorV1.requireCompanions(event) { role, _ ->
                visitedRoles += role
                true
            }
            assertEquals(applicableRoles, visitedRoles, "${name.wire}: executable companion role cardinality")

            applicableRoles.forEach { missingRole ->
                assertFailsWith<WireContractException>(name.wire) {
                    EventCompanionPlanExecutorV1.requireCompanions(event) { role, _ -> role != missingRole }
                }
            }

            val derivedRoles = mutableListOf<RequiredCompanionRoleV1>()
            val claims = EventCompanionClaimResolverV1.resolve(event) { role, _ ->
                derivedRoles += role
                when (role.selector) {
                    "source_graph_decision" -> id.value
                    else -> null
                }
            }
            assertEquals(
                applicableRoles.map { role ->
                    NormalizedCompanionClaimV1(role.role, role.sourceType, expectedCompanionSourceId(role, event))
                },
                claims,
                "${name.wire}: companion selectors and cardinality",
            )
            assertEquals(
                applicableRoles.filter { it.selector == "source_graph_decision" },
                derivedRoles,
                "${name.wire}: only graph-derived selectors may use the derived resolver",
            )
        }
    }

    @Test
    fun everyConditionalEnvelopeBranchAcceptsOnlyItsActiveShape() {
        val safetyHoldCheckIn = ProductEventWireV1(
            EventNameV1.SAFETY_HOLD_CREATED,
            envelopeFor(EventNameV1.SAFETY_HOLD_CREATED),
            SafetyHoldCreatedPropertiesV1(
                SafetyHoldKindV1.RED_FLAG,
                SafetyHoldSourceV1.CheckIn(id),
                stamp.localDate,
                stamp.zoneId,
                stamp.occurredAtUtc,
            ),
        )
        val safetyHoldSession = ProductEventWireV1(
            EventNameV1.SAFETY_HOLD_CREATED,
            envelopeFor(EventNameV1.SAFETY_HOLD_CREATED).copy(sessionId = id),
            SafetyHoldCreatedPropertiesV1(
                SafetyHoldKindV1.POST_SESSION_NEW_OR_WORSE_PAIN,
                SafetyHoldSourceV1.Session,
                stamp.localDate,
                stamp.zoneId,
                stamp.occurredAtUtc,
            ),
        )
        assertCanonicalRoundTrip(safetyHoldCheckIn)
        assertCanonicalRoundTrip(safetyHoldSession)
        assertEquals(
            listOf(
                NormalizedCompanionClaimV1(
                    CompanionRolesV1.SESSION_FEEDBACK_SIDE_EFFECT,
                    CompanionSourceTypeV1.SESSION,
                    id.value,
                ),
            ),
            EventCompanionClaimResolverV1.resolve(safetyHoldSession),
            "session-source safety hold must activate exactly one direct Session companion claim",
        )
        assertEnvelopeMutationsRejected(safetyHoldCheckIn, mapOf("session_id" to JsonPrimitive(id.value)))
        assertEnvelopeMutationsRejected(safetyHoldSession, mapOf("session_id" to JsonNull))

        val immediateSafetyProperties = listOf(
            SafetyScreenShownPropertiesV1(SafetyScreenResultV1.URGENT_STOP, SafetyRouteV1.URGENT_STOP, digest),
            SafetyScreenShownPropertiesV1(SafetyScreenResultV1.PAUSE_TODAY, SafetyRouteV1.PAUSE_ACUTE_ILLNESS, digest),
        )
        immediateSafetyProperties.forEach { properties ->
            val immediate = ProductEventWireV1(
                EventNameV1.SAFETY_SCREEN_SHOWN,
                envelopeFor(EventNameV1.SAFETY_SCREEN_SHOWN).copy(decisionId = id),
                properties,
            )
            assertCanonicalRoundTrip(immediate)
            assertEnvelopeMutationsRejected(immediate, mapOf("decision_id" to JsonNull))
            assertEnvelopeMutationsRejected(immediate, mapOf("decision_id" to JsonNull, "session_id" to JsonPrimitive(id.value)))
            assertEnvelopeMutationsRejected(immediate, mapOf("session_id" to JsonPrimitive(id.value)))
        }

        val postSessionBlocked = ProductEventWireV1(
            EventNameV1.SAFETY_SCREEN_SHOWN,
            envelopeFor(EventNameV1.SAFETY_SCREEN_SHOWN).copy(sessionId = id),
            SafetyScreenShownPropertiesV1(
                SafetyScreenResultV1.BLOCKED_FOR_TODAY,
                SafetyRouteV1.BLOCKED_POST_SESSION_NEW_OR_WORSE_PAIN,
                digest,
            ),
        )
        assertCanonicalRoundTrip(postSessionBlocked)
        assertEnvelopeMutationsRejected(postSessionBlocked, mapOf("session_id" to JsonNull))
        assertEnvelopeMutationsRejected(postSessionBlocked, mapOf("decision_id" to JsonPrimitive(id.value), "session_id" to JsonNull))
        assertEnvelopeMutationsRejected(postSessionBlocked, mapOf("decision_id" to JsonPrimitive(id.value)))

        val checkInHoldBlocked = ProductEventWireV1(
            EventNameV1.SAFETY_SCREEN_SHOWN,
            envelopeFor(EventNameV1.SAFETY_SCREEN_SHOWN),
            SafetyScreenShownPropertiesV1(
                SafetyScreenResultV1.BLOCKED_FOR_TODAY,
                SafetyRouteV1.BLOCKED_RED_FLAG,
                digest,
            ),
        )
        assertCanonicalRoundTrip(checkInHoldBlocked)
        assertEnvelopeMutationsRejected(checkInHoldBlocked, mapOf("decision_id" to JsonPrimitive(id.value)))
        assertEnvelopeMutationsRejected(checkInHoldBlocked, mapOf("session_id" to JsonPrimitive(id.value)))
        assertEnvelopeMutationsRejected(
            checkInHoldBlocked,
            mapOf("decision_id" to JsonPrimitive(id.value), "session_id" to JsonPrimitive(id.value)),
        )

        val startedProperties = typedProperties().getValue(EventNameV1.ROUTINE_STARTED)
        val homeStart = ProductEventWireV1(
            EventNameV1.ROUTINE_STARTED,
            envelopeFor(EventNameV1.ROUTINE_STARTED),
            startedProperties,
        )
        val reminderStart = ProductEventWireV1(
            EventNameV1.ROUTINE_STARTED,
            envelopeFor(EventNameV1.ROUTINE_STARTED).copy(
                reminderOccurrenceId = id,
                source = EventSourceV1.REMINDER,
            ),
            startedProperties,
        )
        assertCanonicalRoundTrip(homeStart)
        assertCanonicalRoundTrip(reminderStart)
        assertEnvelopeMutationsRejected(homeStart, mapOf("reminder_occurrence_id" to JsonPrimitive(id.value)))
        assertEnvelopeMutationsRejected(reminderStart, mapOf("reminder_occurrence_id" to JsonNull))
    }

    @Test
    fun reminderSnoozedDeclaresBothSourceAndChildCompanionClaims() {
        val roles = EventContractRegistryV1
            .specFor(EventNameV1.REMINDER_SNOOZED)
            .companionPlan
            .roles

        assertEquals(
            listOf(
                RequiredCompanionRoleV1(
                    role = "reminder_snooze_edge",
                    sourceType = CompanionSourceTypeV1.REMINDER_OCCURRENCE,
                    selector = "reminder_occurrence_id",
                ),
                RequiredCompanionRoleV1(
                    role = "reminder_snooze_edge",
                    sourceType = CompanionSourceTypeV1.REMINDER_OCCURRENCE,
                    selector = "snooze_occurrence_id",
                ),
            ),
            roles,
        )
    }

    @Test
    fun reminderSnoozedNormalizesBothSourceAndChildClaimsFromTheRegistry() {
        val sourceId = UuidWireV1.parse("00000000-0000-4000-8000-000000000081")
        val childId = UuidWireV1.parse("00000000-0000-4000-8000-000000000082")
        val event = ProductEventWireV1(
            name = EventNameV1.REMINDER_SNOOZED,
            envelope = EventEnvelopeV1(id, stamp, id, null, null, sourceId, null, null),
            properties = ReminderSnoozedPropertiesV1(childId, 15, stamp),
        )

        assertEquals(
            listOf(
                NormalizedCompanionClaimV1("reminder_snooze_edge", CompanionSourceTypeV1.REMINDER_OCCURRENCE, sourceId.value),
                NormalizedCompanionClaimV1("reminder_snooze_edge", CompanionSourceTypeV1.REMINDER_OCCURRENCE, childId.value),
            ),
            EventCompanionClaimResolverV1.resolve(event),
        )
    }

    @Test
    fun typedDraftStillExecutesMirrorAndConditionalRules() {
        val posted = ReminderPostedPropertiesV1(ReminderKindV1.FIXED, stamp, stamp, 1)
        @Suppress("UNCHECKED_CAST")
        val postedSpec = EventContractRegistryV1.specFor(EventNameV1.REMINDER_POSTED) as EventSpecV1<ReminderPostedPropertiesV1>
        assertFailsWith<WireContractException> { postedSpec.validate(envelopeFor(EventNameV1.REMINDER_POSTED), posted) }

        val pain = PainGateResolvedPropertiesV1(TerminalStateV1.COMPLETED, PainAnswerV1.YES, PainGateStatusV1.RESOLVED_NO, false)
        @Suppress("UNCHECKED_CAST")
        val painSpec = EventContractRegistryV1.specFor(EventNameV1.PAIN_GATE_RESOLVED) as EventSpecV1<PainGateResolvedPropertiesV1>
        assertFailsWith<WireContractException> { painSpec.validate(envelopeFor(EventNameV1.PAIN_GATE_RESOLVED), pain) }

        val scheduled = ReminderScheduledPropertiesV1(
            stamp,
            ReminderScheduleBranchV1.Fixed(LogicalFixedKeyV1(UuidWireV1.parse("00000000-0000-4000-8000-000000000099"), 0, stamp.localDate), 0, ReminderCreationReasonV1.INITIAL, null),
        )
        @Suppress("UNCHECKED_CAST")
        val scheduledSpec = EventContractRegistryV1.specFor(EventNameV1.REMINDER_SCHEDULED) as EventSpecV1<ReminderScheduledPropertiesV1>
        assertFailsWith<WireContractException> { scheduledSpec.validate(envelopeFor(EventNameV1.REMINDER_SCHEDULED), scheduled) }
    }

    @Test
    fun reminderLatenessAndScheduleSaveSourceMatricesAreClosed() {
        val due = stamp
        val equalityDelivery = LocalStampWireV1(
            InstantWireV1.parse("2026-08-27T09:00:00.000Z"),
            stamp.localDate,
            stamp.zoneId,
            stamp.utcOffsetMinutes,
        )
        val lateDelivery = LocalStampWireV1(
            InstantWireV1.parse("2026-08-27T09:00:00.001Z"),
            stamp.localDate,
            stamp.zoneId,
            stamp.utcOffsetMinutes,
        )
        @Suppress("UNCHECKED_CAST")
        val postedSpec = EventContractRegistryV1.specFor(EventNameV1.REMINDER_POSTED) as EventSpecV1<ReminderPostedPropertiesV1>
        postedSpec.validate(
            envelopeFor(EventNameV1.REMINDER_POSTED),
            ReminderPostedPropertiesV1(ReminderKindV1.FIXED, due, equalityDelivery, 3_600_000),
        )
        assertFailsWith<WireContractException> {
            postedSpec.validate(
                envelopeFor(EventNameV1.REMINDER_POSTED),
                ReminderPostedPropertiesV1(ReminderKindV1.FIXED, due, lateDelivery, 3_600_001),
            )
        }

        @Suppress("UNCHECKED_CAST")
        val savedSpec = EventContractRegistryV1.specFor(EventNameV1.WORK_SCHEDULE_SAVED) as EventSpecV1<WorkScheduleSavedPropertiesV1>
        listOf(
            WorkScheduleSavedPropertiesV1(null, false, 1, TimeMinuteWireV1.parse("09:00"), TimeMinuteWireV1.parse("17:00"), 1, ScheduleChangeSourceV1.ONBOARDING, false),
            WorkScheduleSavedPropertiesV1(null, true, 1, TimeMinuteWireV1.parse("09:00"), TimeMinuteWireV1.parse("17:00"), 1, ScheduleChangeSourceV1.SETTINGS, false),
            WorkScheduleSavedPropertiesV1(id, true, 1, TimeMinuteWireV1.parse("09:00"), TimeMinuteWireV1.parse("17:00"), 1, ScheduleChangeSourceV1.ONBOARDING, false),
            WorkScheduleSavedPropertiesV1(null, true, 1, TimeMinuteWireV1.parse("09:00"), TimeMinuteWireV1.parse("17:00"), 1, ScheduleChangeSourceV1.ONBOARDING, true),
        ).forEach { mutant ->
            assertFailsWith<WireContractException> {
                savedSpec.validate(envelopeFor(EventNameV1.WORK_SCHEDULE_SAVED), mutant)
            }
        }
    }

    private fun typedProperties(): Map<EventNameV1, EventPropertiesV1> = linkedMapOf(
        EventNameV1.APP_FIRST_OPENED to AppFirstOpenedPropertiesV1(id),
        EventNameV1.ONBOARDING_STARTED to OnboardingStartedPropertiesV1(1, 2),
        EventNameV1.AGE_GATE_ANSWERED to AgeGateAnsweredPropertiesV1(),
        EventNameV1.SCOPE_ACKNOWLEDGED to ScopeAcknowledgedPropertiesV1(id, contentVersion = semver, contentDigest = digest),
        EventNameV1.SCOPE_REACK_REQUIRED to ScopeReackRequiredPropertiesV1(id, semver, digest, semver, digest, ScopeReackTriggerV1.HOME),
        EventNameV1.SCOPE_REACK_COMPLETED to ScopeReackCompletedPropertiesV1(id, id, semver, digest),
        EventNameV1.NOTIFICATION_PERMISSION_PROMPTED to NotificationPermissionPromptedPropertiesV1(id, NotificationPromptTriggerV1.AUTOMATIC_ONBOARDING),
        EventNameV1.NOTIFICATION_PERMISSION_UPDATED to NotificationPermissionUpdatedPropertiesV1(PermissionStateV1.GRANTED, PermissionUpdateSourceV1.SYSTEM_PROMPT, id, PromptResultV1.GRANTED),
        EventNameV1.ONBOARDING_COMPLETED to OnboardingCompletedPropertiesV1(EventTimingV1.Duration(1), 1, 2, 3, 4),
        EventNameV1.CHECK_IN_STARTED to CheckInStartedPropertiesV1(id, CheckInKindV1.NEW, 1, 2),
        EventNameV1.CHECK_IN_RECONFIRMATION_REQUIRED to CheckInReconfirmationRequiredPropertiesV1(id, 1, ReconfirmReasonV1.TTL, EntryTriggerV1.HOME),
        EventNameV1.REST_SUPPRESSION_SUPERSEDED to RestSuppressionSupersededPropertiesV1(id, id, RestReplacementResultV1.MODE, 1),
        EventNameV1.WEEKLY_SUMMARY_GENERATED to WeeklySummaryGeneratedPropertiesV1(monday, id, 1, 1),
        EventNameV1.WEEKLY_SUMMARY_VIEWED to WeeklySummaryViewedPropertiesV1(id, monday),
        EventNameV1.EXPORT_STARTED to ExportStartedPropertiesV1(id),
        EventNameV1.EXPORT_COMPLETED to ExportCompletedPropertiesV1(id, RecordCountsWireV1(1, 1, 1, 1, 1, 1, 1, 1, 1), 1),
        EventNameV1.EXPORT_FAILED to ExportFailedPropertiesV1(id, ExportFailureCodeV1.JSON_ENCODE_FAILED),
        EventNameV1.WORK_SCHEDULE_SAVED to WorkScheduleSavedPropertiesV1(null, true, 1, TimeMinuteWireV1.parse("09:00"), TimeMinuteWireV1.parse("17:00"), 1, ScheduleChangeSourceV1.ONBOARDING, false),
        EventNameV1.SCHEDULE_RECONCILED to ScheduleReconciledPropertiesV1(ScheduleReconcileReasonV1.BOOT, 1, 0, 0),
        EventNameV1.CHECK_IN_SUBMITTED to CheckInSubmittedPropertiesV1(id, id, CheckInKindV1.NEW, AnswersKindV1.FULL, EventTimingV1.Duration(1)),
        EventNameV1.DECISION_EVALUATED to DecisionEvaluatedPropertiesV1(id, RuleResultV1.RECOVER, ModeV1.RECOVER, ModeV1.RECOVER, listOf(ReasonCodeV1.SAF_ENERGY_LOW), emptyList(), false),
        EventNameV1.ROUTINE_START_BLOCKED to RoutineStartBlockedPropertiesV1(StartGateV1.CONTRACT_ERROR),
        EventNameV1.RECOMMENDATION_SHOWN to RecommendationShownPropertiesV1(RoutineIdV1.REC_01, ModeV1.RECOVER, ModeV1.RECOVER, ModeV1.RECOVER, false, null),
        EventNameV1.REST_SUPPRESSION_CREATED to RestSuppressionCreatedPropertiesV1(stamp.localDate, stamp.zoneId, stamp.occurredAtUtc),
        EventNameV1.ROUTINE_SELECTED to RoutineSelectedPropertiesV1(RoutineIdV1.MAI_01, ModeV1.MAINTAIN, ModeV1.MAINTAIN, RoutineSelectionV1.RECOMMENDED, null),
        EventNameV1.ROUTINE_PAUSED to RoutinePausedPropertiesV1(1),
        EventNameV1.ROUTINE_RESUMED to RoutineResumedPropertiesV1(1),
        EventNameV1.ROUTINE_RECOVERY_OFFERED to RoutineRecoveryOfferedPropertiesV1(1, semver),
        EventNameV1.ROUTINE_RECOVERY_FAILED to RoutineRecoveryFailedPropertiesV1(RecoveryReasonV1.REBOOT_OR_CLOCK_DISCONTINUITY),
        EventNameV1.ROUTINE_STEP_SKIPPED to RoutineStepSkippedPropertiesV1("step-1", 1),
        EventNameV1.ROUTINE_STOPPED to RoutineStoppedPropertiesV1(1, PainGateStatusV1.RESOLVED_NO),
        EventNameV1.ROUTINE_ABANDONED to RoutineAbandonedPropertiesV1(RecoveryReasonV1.REBOOT_OR_CLOCK_DISCONTINUITY),
        EventNameV1.ROUTINE_COMPLETED to RoutineCompletedPropertiesV1(RoutineIdV1.REC_01, 1, 0, 1, 2, 3, 4),
        EventNameV1.PAIN_GATE_RESOLVED to PainGateResolvedPropertiesV1(TerminalStateV1.COMPLETED, PainAnswerV1.NO, PainGateStatusV1.RESOLVED_NO, false),
        EventNameV1.FEEDBACK_UPDATED to FeedbackUpdatedPropertiesV1(
            listOf(UpdatedFieldV1.EFFORT, UpdatedFieldV1.CONTEXT_FIT),
            TerminalStateV1.COMPLETED,
            EffortV1.EASY,
            ContextFitV1.YES,
            true,
            CapResultV1.NOT_TOO_HARD,
        ),
        EventNameV1.DAY_MODE_CAP_UPDATED to DayModeCapUpdatedPropertiesV1(id, ModeV1.MAINTAIN, null, ModeV1.MAINTAIN, DeadlineSourceV1.CANDIDATE_LATER, stamp, stamp.occurredAtUtc),
        EventNameV1.REMINDER_POSTED to ReminderPostedPropertiesV1(ReminderKindV1.FIXED, stamp, stamp, 0),
        EventNameV1.REMINDER_OPENED to ReminderOpenedPropertiesV1(stamp, OpenSurfaceV1.NOTIFICATION_BODY),
        EventNameV1.REMINDER_SNOOZED to ReminderSnoozedPropertiesV1(relatedId, 15, stamp),
        EventNameV1.REMINDER_DISMISSED to ReminderDismissedPropertiesV1(stamp),
        EventNameV1.REMINDER_MERGED to ReminderMergedPropertiesV1(id, 1, MergeTieBreakV1.EARLIER_DUE),
        EventNameV1.REMINDER_CANCELLED to ReminderCancelledPropertiesV1(ReminderCancelReasonV1.SCHEDULE_EDIT, ReminderResultStatusV1.CANCELLED),
        EventNameV1.REMINDER_BLOCKED_PERMISSION to ReminderBlockedPermissionPropertiesV1(),
        EventNameV1.REMINDER_SKIPPED to ReminderSkippedPropertiesV1(ReminderSkippedStatusV1.SKIPPED_LATE, 3_600_001),
        EventNameV1.REMINDER_SCHEDULED to ReminderScheduledPropertiesV1(stamp, ReminderScheduleBranchV1.Fixed(LogicalFixedKeyV1(id, 0, stamp.localDate), 0, ReminderCreationReasonV1.INITIAL, null)),
        EventNameV1.SAFETY_HOLD_CREATED to SafetyHoldCreatedPropertiesV1(SafetyHoldKindV1.RED_FLAG, SafetyHoldSourceV1.CheckIn(id), stamp.localDate, stamp.zoneId, stamp.occurredAtUtc),
        EventNameV1.SAFETY_SCREEN_SHOWN to SafetyScreenShownPropertiesV1(SafetyScreenResultV1.BLOCKED_FOR_TODAY, SafetyRouteV1.BLOCKED_RED_FLAG, digest),
        EventNameV1.ROUTINE_STARTED to RoutineStartedPropertiesV1(RoutineIdV1.REC_01, id, ModeV1.RECOVER, true, 1, 2, 3, 4, EventTimingV1.Duration(1)),
    )

    private fun envelopeFor(name: EventNameV1): EventEnvelopeV1 {
        val mask = EventContractRegistryV1.maskFor(name)
        fun value(rule: EnvelopeSlotRule) = if (rule == EnvelopeSlotRule.REQUIRED) id else null
        return EventEnvelopeV1(
            id, stamp, id, value(mask.decisionId), value(mask.sessionId), value(mask.reminderOccurrenceId), value(mask.scheduleVersionId),
            if (mask.source == EnvelopeSlotRule.REQUIRED) EventSourceV1.HOME else null,
        )
    }

    private fun mutateProperties(root: JsonObject, mutation: (JsonObject) -> JsonObject): String =
        JsonObject(root + ("properties" to mutation(root.getValue("properties").jsonObject))).toString()

    private fun wrongTypeFor(value: JsonElement): JsonElement = when (value) {
        JsonNull -> JsonArray(emptyList())
        is JsonObject -> JsonPrimitive(false)
        is JsonArray -> JsonObject(emptyMap())
        is JsonPrimitive -> JsonObject(emptyMap())
    }

    private fun nonNullFlipFor(name: EventNameV1, key: String): JsonElement = when (name to key) {
        EventNameV1.WORK_SCHEDULE_SAVED to "previous_schedule_version_id" -> JsonPrimitive(id.value)
        EventNameV1.RECOMMENDATION_SHOWN to "runtime_day_mode_cap_snapshot",
        EventNameV1.ROUTINE_SELECTED to "runtime_day_mode_cap_snapshot" -> capSnapshot().toJson().element
        EventNameV1.DAY_MODE_CAP_UPDATED to "previous_cap" -> JsonPrimitive(ModeV1.RECOVER.wire)
        EventNameV1.REMINDER_SNOOZED to "supersedes_occurrence_id",
        EventNameV1.REMINDER_SCHEDULED to "supersedes_occurrence_id" -> JsonPrimitive(id.value)
        else -> error("$name.$key: nullable fixture needs an explicit valid-shape non-null mutation")
    }

    private fun allowsIndependentNull(name: EventNameV1, key: String): Boolean =
        name == EventNameV1.CHECK_IN_RECONFIRMATION_REQUIRED && key == "age_ms"

    private fun capSnapshot() = EventDayModeCapSnapshotV1(
        occurred = stamp,
        maxMode = ModeV1.RECOVER,
        modeTriggerSessionId = id,
        sourceSessionId = id,
        expiresAtUtc = stamp.occurredAtUtc,
        clockIntegrity = EventClockIntegrityEvidenceV1(
            originBootMarker = 1,
            createdElapsedRealtimeMs = 1,
            monotonicDeadlineMs = 2,
            remainingElapsedMsAtLastCheckpoint = 1,
            originalDurationMs = 1,
        ),
    )

    private fun duplicateProperty(valid: String, key: String): String {
        val propertiesAt = valid.indexOf("\"properties\":{") + "\"properties\":{".length
        return valid.substring(0, propertiesAt) + "\"$key\":null," + valid.substring(propertiesAt)
    }

    private fun assertCanonicalRoundTrip(event: ProductEventWireV1) {
        assertEquals(event, ClosedCodecV1.decodeEvent(ClosedCodecV1.encodeEvent(event)), event.name.wire)
    }

    private fun assertEnvelopeMutationsRejected(
        validEvent: ProductEventWireV1,
        overrides: Map<String, JsonElement>,
    ) {
        val valid = ClosedCodecV1.encodeEvent(validEvent)
        val root = Json.parseToJsonElement(valid).jsonObject
        val mutant = JsonObject(root + overrides).toString()
        assertFailsWith<WireContractException>("${validEvent.name.wire}: $overrides") {
            ClosedCodecV1.decodeEvent(mutant)
        }
    }

    private fun applicableCompanionRoles(event: ProductEventWireV1): List<RequiredCompanionRoleV1> =
        EventContractRegistryV1.specFor(event.name).companionPlan.roles.filter { role ->
            if (!role.conditional || event.name != EventNameV1.SAFETY_HOLD_CREATED) {
                true
            } else {
                val sourceType = requireNotNull(event.properties.body["source_type"]).jsonPrimitive.content
                when (role.sourceType) {
                    CompanionSourceTypeV1.DECISION -> sourceType == ConstraintSourceTypeV1.CHECK_IN.wire
                    CompanionSourceTypeV1.SESSION -> sourceType == ConstraintSourceTypeV1.SESSION.wire
                    else -> true
                }
            }
        }

    private fun expectedCompanionSourceId(
        role: RequiredCompanionRoleV1,
        event: ProductEventWireV1,
    ): String = when (role.selector) {
        "app_profile_singleton" -> "1"
        "source_graph_decision" -> id.value
        "decision_id" -> requireNotNull(event.envelope.decisionId).value
        "session_id" -> requireNotNull(event.envelope.sessionId).value
        "reminder_occurrence_id" -> requireNotNull(event.envelope.reminderOccurrenceId).value
        "schedule_version_id" -> requireNotNull(event.envelope.scheduleVersionId).value
        else -> requireNotNull(event.properties.body[role.selector]).jsonPrimitive.content
    }

    private fun envelopeMutants(root: JsonObject, mask: EventEnvelopeMaskV1): List<Pair<String, String>> {
        val uuid = JsonPrimitive(id.value)
        val mutations = listOf(
            Triple("decision_id", mask.decisionId, uuid), Triple("session_id", mask.sessionId, uuid),
            Triple("reminder_occurrence_id", mask.reminderOccurrenceId, uuid), Triple("schedule_version_id", mask.scheduleVersionId, uuid),
            Triple("source", mask.source, JsonPrimitive("home")),
        )
        return mutations.map { (key, rule, forbiddenValue) ->
            val mutantValue = when (rule) {
                EnvelopeSlotRule.REQUIRED -> JsonNull
                EnvelopeSlotRule.FORBIDDEN -> forbiddenValue
                EnvelopeSlotRule.CONDITIONAL -> if (root[key] === JsonNull) forbiddenValue else JsonNull
            }
            "envelope-${rule.name.lowercase()}:$key" to JsonObject(root + (key to mutantValue)).toString()
        }
    }
}
