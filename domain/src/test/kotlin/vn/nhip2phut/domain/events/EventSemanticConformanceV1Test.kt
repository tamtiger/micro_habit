package vn.nhip2phut.domain.events

import vn.nhip2phut.domain.wire.v1.ClosedCodecV1
import vn.nhip2phut.domain.wire.v1.DateWireV1
import vn.nhip2phut.domain.wire.v1.InstantWireV1
import vn.nhip2phut.domain.wire.v1.LocalStampWireV1
import vn.nhip2phut.domain.wire.v1.UuidWireV1
import vn.nhip2phut.domain.wire.v1.WireContractException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EventSemanticConformanceV1Test {
    private val id = UuidWireV1.parse("00000000-0000-4000-8000-000000000001")
    private val stamp = LocalStampWireV1(
        occurredAtUtc = InstantWireV1.parse("2026-08-27T08:00:00.000Z"),
        localDate = DateWireV1.parse("2026-08-27"),
        zoneId = "UTC",
        utcOffsetMinutes = 0,
    )

    @Test
    fun persistedIncompleteDecisionRequiresOnlyTheCorruptCapField() {
        val properties = DecisionEvaluatedPropertiesV1(
            checkInId = id,
            result = RuleResultV1.INCOMPLETE,
            baseMode = null,
            effectiveMode = null,
            reasonCodes = listOf(ReasonCodeV1.SAF_INPUT_INVALID),
            invalidFields = listOf(InvalidFieldV1.ENERGY),
            capApplied = false,
        )

        assertFailsWith<WireContractException> {
            ClosedCodecV1.encodeEvent(ProductEventWireV1(EventNameV1.DECISION_EVALUATED, envelopeFor(EventNameV1.DECISION_EVALUATED), properties))
        }
    }

    @Test
    fun recommendationRoutineIdMustBelongToItsCanonicalMode() {
        val properties = RecommendationShownPropertiesV1(
            RoutineIdV1.BUI_01,
            ModeV1.RECOVER,
            ModeV1.RECOVER,
            ModeV1.RECOVER,
            capApplied = false,
            runtimeDayModeCapSnapshot = null,
        )

        assertFailsWith<WireContractException> {
            ClosedCodecV1.encodeEvent(ProductEventWireV1(EventNameV1.RECOMMENDATION_SHOWN, envelopeFor(EventNameV1.RECOMMENDATION_SHOWN), properties))
        }
    }

    @Test
    fun selectedRoutineIdMustBelongToItsSignedRoutineMode() {
        val properties = RoutineSelectedPropertiesV1(
            RoutineIdV1.BUI_01,
            ModeV1.RECOVER,
            ModeV1.RECOVER,
            RoutineSelectionV1.RECOMMENDED,
            runtimeDayModeCapSnapshot = null,
        )

        assertFailsWith<WireContractException> {
            ClosedCodecV1.encodeEvent(ProductEventWireV1(EventNameV1.ROUTINE_SELECTED, envelopeFor(EventNameV1.ROUTINE_SELECTED), properties))
        }
    }

    @Test
    fun startedRoutineIdMustBelongToItsRuntimeMode() {
        val properties = RoutineStartedPropertiesV1(
            RoutineIdV1.BUI_01,
            id,
            ModeV1.RECOVER,
            isSelectedWorkdayAtStart = true,
            startBootMarker = 1,
            startElapsedRealtimeMs = 2,
            startClockGeneration = 3,
            startWallMinusElapsedMs = 4,
            totalTiming = EventTimingV1.Duration(1),
        )

        assertFailsWith<WireContractException> {
            ClosedCodecV1.encodeEvent(ProductEventWireV1(EventNameV1.ROUTINE_STARTED, envelopeFor(EventNameV1.ROUTINE_STARTED), properties))
        }
    }

    private fun envelopeFor(name: EventNameV1): EventEnvelopeV1 {
        val mask = EventContractRegistryV1.maskFor(name)
        fun idFor(rule: EnvelopeSlotRule) = if (rule == EnvelopeSlotRule.REQUIRED) id else null
        return EventEnvelopeV1(
            eventId = id,
            occurred = stamp,
            installationId = id,
            decisionId = idFor(mask.decisionId),
            sessionId = idFor(mask.sessionId),
            reminderOccurrenceId = idFor(mask.reminderOccurrenceId),
            scheduleVersionId = idFor(mask.scheduleVersionId),
            source = if (mask.source == EnvelopeSlotRule.REQUIRED) EventSourceV1.HOME else null,
        )
    }
}
