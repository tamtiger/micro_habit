package vn.nhip2phut.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import vn.nhip2phut.domain.checkin.CheckInLifecycleResult
import vn.nhip2phut.domain.checkin.ReconfirmationReasonV1
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.Stiffness
import vn.nhip2phut.domain.rule.DefaultRuleEngineV1
import vn.nhip2phut.domain.rule.DraftField
import vn.nhip2phut.domain.rule.PresentationRouteV1
import vn.nhip2phut.domain.rule.RuleInputDraftV1
import vn.nhip2phut.domain.rule.RuleResultV1
import vn.nhip2phut.domain.safety.SafetyHoldKind

class AppEntryRouteResolverV1Test {
    @Test
    fun `uncommitted onboarding wins over every requested deep link and downstream gate`() {
        AppEntryRequestV1.entries.forEach { requested ->
            val route = AppEntryRouteResolverV1.resolve(
                requested = requested,
                snapshot = readySnapshot(
                    onboardingCommitted = false,
                    activeSafetyHoldKind = SafetyHoldKind.RED_FLAG,
                    contractState = AppEntryContractStateV1.DATA_ERROR,
                    workWindowActive = false,
                    checkInLifecycle = CheckInLifecycleResult.Expired,
                    decision = urgentStop(),
                ),
            )

            assertEquals(AppEntryRouteV1.Onboarding, route, "requested=$requested")
        }
    }

    @Test
    fun `active hold wins after onboarding and cannot be bypassed by home check-in or routine deep links`() {
        SafetyHoldKind.entries.forEach { kind ->
            AppEntryRequestV1.entries.forEach { requested ->
                val route = AppEntryRouteResolverV1.resolve(
                    requested = requested,
                    snapshot = readySnapshot(
                        activeSafetyHoldKind = kind,
                        contractState = AppEntryContractStateV1.CONTENT_UNAVAILABLE,
                        workWindowActive = false,
                        checkInLifecycle = CheckInLifecycleResult.Expired,
                        decision = buildMode(),
                    ),
                )

                assertEquals(AppEntryRouteV1.SafetyHold(kind), route, "kind=$kind requested=$requested")
            }
        }
    }

    @Test
    fun `missing typed content and authenticated data errors fail closed`() {
        listOf(
            AppEntryContractStateV1.CONTENT_UNAVAILABLE,
            AppEntryContractStateV1.DATA_ERROR,
        ).forEach { contractState ->
            val route = AppEntryRouteResolverV1.resolve(
                requested = AppEntryRequestV1.ROUTINE,
                snapshot = readySnapshot(
                    contractState = contractState,
                    decision = buildMode(),
                ),
            )

            val error = assertIs<AppEntryRouteV1.DataError>(route)
            assertEquals(contractState, error.contractState)
        }
    }

    @Test
    fun `schedule mismatch precedes work-window expiry and keeps the exact reconfirm reason`() {
        val route = AppEntryRouteResolverV1.resolve(
            requested = AppEntryRequestV1.ROUTINE,
            snapshot = readySnapshot(
                workWindowActive = false,
                checkInLifecycle = CheckInLifecycleResult.ReconfirmRequired(
                    ReconfirmationReasonV1.SCHEDULE_CHANGED,
                ),
                decision = buildMode(),
            ),
        )

        assertEquals(
            AppEntryRouteV1.ReconfirmCheckIn(ReconfirmationReasonV1.SCHEDULE_CHANGED),
            route,
        )
    }

    @Test
    fun `outside active work window never opens a new or existing check-in`() {
        val withoutCheckIn = AppEntryRouteResolverV1.resolve(
            requested = AppEntryRequestV1.CHECK_IN,
            snapshot = readySnapshot(workWindowActive = false),
        )
        val expiredDecision = AppEntryRouteResolverV1.resolve(
            requested = AppEntryRequestV1.ROUTINE,
            snapshot = readySnapshot(
                checkInLifecycle = CheckInLifecycleResult.Expired,
                decision = buildMode(),
            ),
        )

        assertEquals(AppEntryRouteV1.WorkWindowClosed, withoutCheckIn)
        assertEquals(AppEntryRouteV1.WorkWindowClosed, expiredDecision)
    }

    @Test
    fun `date ttl time mapping and unknown clock all route to explicit reconfirm`() {
        listOf(
            ReconfirmationReasonV1.LOCAL_DATE_CHANGED,
            ReconfirmationReasonV1.TTL,
            ReconfirmationReasonV1.TIMEZONE_OR_TIME_CHANGE,
            ReconfirmationReasonV1.CLOCK_UNKNOWN,
        ).forEach { reason ->
            val route = AppEntryRouteResolverV1.resolve(
                requested = AppEntryRequestV1.ROUTINE,
                snapshot = readySnapshot(
                    checkInLifecycle = CheckInLifecycleResult.ReconfirmRequired(reason),
                    decision = buildMode(),
                ),
            )

            assertEquals(AppEntryRouteV1.ReconfirmCheckIn(reason), route, "reason=$reason")
        }
    }

    @Test
    fun `check-in and routine requests without a current decision start at the red-flag gate`() {
        listOf(AppEntryRequestV1.CHECK_IN, AppEntryRequestV1.ROUTINE).forEach { requested ->
            val route = AppEntryRouteResolverV1.resolve(
                requested = requested,
                snapshot = readySnapshot(),
            )

            assertEquals(AppEntryRouteV1.CheckInRedFlag, route, "requested=$requested")
        }
    }

    @Test
    fun `canonical no-mode outcomes resolve only to check-in safety or rest routes`() {
        val urgent = routeFor(urgentStop())
        val acuteIllness = routeFor(acuteStop(AcuteIssue.ACUTE_ILLNESS))
        val worseningPain = routeFor(acuteStop(AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY))
        val medicallyRestricted = routeFor(acuteStop(AcuteIssue.MEDICALLY_RESTRICTED))
        val rest = routeFor(restOnly())
        val missingRed = routeFor(validDraft().copy(redFlag = DraftField.Missing).evaluate())
        val missingFullField = routeFor(validDraft().copy(energy = DraftField.Missing).evaluate())
        val invalidConstraint = routeFor(validDraft().copy(dayModeCap = DraftField.Invalid).evaluate())

        assertEquals(AppEntryRouteV1.UrgentStop, urgent)
        assertEquals(
            AppEntryRouteV1.AcuteStop(PresentationRouteV1.PAUSE_ACUTE_ILLNESS),
            acuteIllness,
        )
        assertEquals(
            AppEntryRouteV1.AcuteStop(
                PresentationRouteV1.PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY,
            ),
            worseningPain,
        )
        assertEquals(
            AppEntryRouteV1.AcuteStop(PresentationRouteV1.PAUSE_MEDICALLY_RESTRICTED),
            medicallyRestricted,
        )
        assertEquals(AppEntryRouteV1.RestDay, rest)
        assertEquals(AppEntryRouteV1.CheckInRedFlag, missingRed)
        assertEquals(AppEntryRouteV1.CheckInFullForm, missingFullField)
        assertIs<AppEntryRouteV1.DataError>(invalidConstraint)

        assertFalse(
            listOf(
                urgent,
                acuteIllness,
                worseningPain,
                medicallyRestricted,
                rest,
                missingRed,
                missingFullField,
                invalidConstraint,
            ).any { it is AppEntryRouteV1.Recommendation },
        )
    }

    @Test
    fun `fresh mode decision is the only tested path to recommendation`() {
        val route = routeFor(buildMode())

        assertIs<AppEntryRouteV1.Recommendation>(route)
    }

    private fun routeFor(result: RuleResultV1): AppEntryRouteV1 =
        AppEntryRouteResolverV1.resolve(
            requested = AppEntryRequestV1.ROUTINE,
            snapshot = readySnapshot(
                checkInLifecycle = CheckInLifecycleResult.Fresh,
                decision = result,
            ),
        )

    private fun readySnapshot(
        onboardingCommitted: Boolean = true,
        activeSafetyHoldKind: SafetyHoldKind? = null,
        contractState: AppEntryContractStateV1 = AppEntryContractStateV1.READY,
        workWindowActive: Boolean = true,
        checkInLifecycle: CheckInLifecycleResult? = null,
        decision: RuleResultV1? = null,
    ) = AppEntrySnapshotV1(
        onboardingCommitted = onboardingCommitted,
        activeSafetyHoldKind = activeSafetyHoldKind,
        contractState = contractState,
        workWindowActive = workWindowActive,
        checkInLifecycle = checkInLifecycle,
        decision = decision,
    )

    private fun urgentStop(): RuleResultV1 =
        validDraft().copy(redFlag = DraftField.Valid(true)).evaluate()

    private fun acuteStop(issue: AcuteIssue): RuleResultV1 =
        validDraft().copy(acuteIssue = DraftField.Valid(issue)).evaluate()

    private fun restOnly(): RuleResultV1 =
        validDraft().copy(intent = DraftField.Valid(Intent.REST)).evaluate()

    private fun buildMode(): RuleResultV1 = validDraft().evaluate()

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
}
