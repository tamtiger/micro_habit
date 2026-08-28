package vn.nhip2phut.app.checkin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import vn.nhip2phut.app.navigation.AppEntryContractStateV1
import vn.nhip2phut.app.navigation.AppEntryRouteV1
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.Stiffness
import vn.nhip2phut.domain.rule.DefaultRuleEngineV1
import vn.nhip2phut.domain.rule.DraftField
import vn.nhip2phut.domain.rule.PresentationRouteV1
import vn.nhip2phut.domain.rule.ReasonCode
import vn.nhip2phut.domain.rule.RuleInputDraftV1
import vn.nhip2phut.domain.rule.RuleResultV1
import vn.nhip2phut.domain.safety.SafetyHoldKind

class SafetyRestPresenterV1Test {
    @Test
    fun `urgent and each acute pause keep the exact safety reason and never expose routine CTAs`() {
        val cases = listOf(
            Case(
                decision = validDraft().copy(redFlag = DraftField.Valid(true)).evaluate(),
                route = AppEntryRouteV1.UrgentStop,
                reason = ReasonCode.SAF_RED_FLAG_PRESENT,
                holdKind = SafetyHoldKind.RED_FLAG,
            ),
            Case(
                decision = acute(AcuteIssue.ACUTE_ILLNESS),
                route = AppEntryRouteV1.AcuteStop(PresentationRouteV1.PAUSE_ACUTE_ILLNESS),
                reason = ReasonCode.SAF_ACUTE_ILLNESS,
                holdKind = SafetyHoldKind.ACUTE_ILLNESS,
            ),
            Case(
                decision = acute(AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY),
                route = AppEntryRouteV1.AcuteStop(
                    PresentationRouteV1.PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY,
                ),
                reason = ReasonCode.SAF_ACUTE_NEW_OR_WORSENING_PAIN,
                holdKind = SafetyHoldKind.NEW_OR_WORSENING_PAIN_OR_INJURY,
            ),
            Case(
                decision = acute(AcuteIssue.MEDICALLY_RESTRICTED),
                route = AppEntryRouteV1.AcuteStop(PresentationRouteV1.PAUSE_MEDICALLY_RESTRICTED),
                reason = ReasonCode.SAF_MEDICALLY_RESTRICTED,
                holdKind = SafetyHoldKind.MEDICALLY_RESTRICTED,
            ),
        )

        cases.forEach { case ->
            val presentation = SafetyRestPresenterV1.present(
                SafetyRestPresentationSourceV1.Decision(case.decision),
            )

            assertEquals(case.route, presentation.route)
            assertEquals(listOf(case.reason), presentation.reasonCodes)
            assertEquals(case.holdKind, presentation.safetyHoldKind)
            assertNoRoutineCtas(presentation)
        }
    }

    @Test
    fun `every authenticated hold kind maps to its typed blocked route and lock reason`() {
        SafetyHoldKind.entries.forEach { kind ->
            val locked = validDraft().copy(safetyLockActive = true).evaluate()
            val presentation = SafetyRestPresenterV1.present(
                SafetyRestPresentationSourceV1.Decision(
                    result = locked,
                    activeHoldKind = kind,
                ),
            )

            assertEquals(AppEntryRouteV1.SafetyHold(kind), presentation.route)
            assertEquals(listOf(ReasonCode.SAF_LOCK_ACTIVE), presentation.reasonCodes)
            assertEquals(kind, presentation.safetyHoldKind)
            assertNoRoutineCtas(presentation)
        }
    }

    @Test
    fun `Rest is a suppression outcome rather than a safety hold`() {
        val rest = validDraft().copy(intent = DraftField.Valid(Intent.REST)).evaluate()

        val presentation = SafetyRestPresenterV1.present(
            SafetyRestPresentationSourceV1.Decision(rest),
        )

        assertEquals(AppEntryRouteV1.RestDay, presentation.route)
        assertEquals(listOf(ReasonCode.SAF_INTENT_REST), presentation.reasonCodes)
        assertNull(presentation.safetyHoldKind)
        assertFalse(presentation.createsSafetyHold)
        assertNoRoutineCtas(presentation)
    }

    @Test
    fun `form incomplete constraint incomplete and data error keep exact fail-closed reasons`() {
        val cases = listOf(
            Triple(
                SafetyRestPresentationSourceV1.Decision(
                    validDraft().copy(redFlag = DraftField.Missing).evaluate(),
                ),
                AppEntryRouteV1.CheckInRedFlag,
                listOf(ReasonCode.SAF_INPUT_MISSING),
            ),
            Triple(
                SafetyRestPresentationSourceV1.Decision(
                    validDraft().copy(energy = DraftField.Invalid).evaluate(),
                ),
                AppEntryRouteV1.CheckInFullForm,
                listOf(ReasonCode.SAF_INPUT_INVALID),
            ),
            Triple(
                SafetyRestPresentationSourceV1.Decision(
                    validDraft().copy(dayModeCap = DraftField.Invalid).evaluate(),
                ),
                AppEntryRouteV1.DataError(AppEntryContractStateV1.DATA_ERROR),
                listOf(ReasonCode.SAF_INPUT_INVALID),
            ),
            Triple(
                SafetyRestPresentationSourceV1.DataError(AppEntryContractStateV1.DATA_ERROR),
                AppEntryRouteV1.DataError(AppEntryContractStateV1.DATA_ERROR),
                emptyList(),
            ),
        )

        cases.forEach { (source, expectedRoute, expectedReasons) ->
            val presentation = SafetyRestPresenterV1.present(source)

            assertEquals(expectedRoute, presentation.route)
            assertEquals(expectedReasons, presentation.reasonCodes)
            assertNull(presentation.safetyHoldKind)
            assertNoRoutineCtas(presentation)
        }
    }

    private fun assertNoRoutineCtas(presentation: SafetyRestPresentationV1) {
        assertNull(presentation.modeCta)
        assertNull(presentation.routineCta)
        assertIs<AppEntryRouteV1>(presentation.route)
    }

    private fun acute(issue: AcuteIssue): RuleResultV1 =
        validDraft().copy(acuteIssue = DraftField.Valid(issue)).evaluate()

    private fun RuleInputDraftV1.evaluate(): RuleResultV1 = DefaultRuleEngineV1.evaluate(this)

    private fun validDraft() = RuleInputDraftV1(
        safetyLockActive = false,
        redFlag = DraftField.Valid(false),
        acuteIssue = DraftField.Valid(AcuteIssue.NONE),
        energy = DraftField.Valid(Energy.GOOD),
        stiffness = DraftField.Valid(Stiffness.MILD),
        intent = DraftField.Valid(Intent.MODERATE),
        dayModeCap = DraftField.Valid(null),
    )

    private data class Case(
        val decision: RuleResultV1,
        val route: AppEntryRouteV1,
        val reason: ReasonCode,
        val holdKind: SafetyHoldKind,
    )
}
