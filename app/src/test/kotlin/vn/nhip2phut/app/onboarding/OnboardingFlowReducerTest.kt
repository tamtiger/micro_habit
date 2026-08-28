package vn.nhip2phut.app.onboarding

import vn.nhip2phut.domain.model.LocalStamp
import vn.nhip2phut.domain.onboarding.SafetyContentIdentityV1
import vn.nhip2phut.domain.onboarding.ValidatedInitialScheduleV1
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingFlowReducerTest {
    private val safetyContent = SafetyContentIdentityV1.debugFixture(
        manifestVersion = "1.0.0",
        globalSafetyDigestSha256 = "b".repeat(64),
    )
    private val acknowledgementStamp = LocalStamp(
        instant = Instant.parse("2026-08-28T01:00:00.000Z"),
        localDate = LocalDate.parse("2026-08-28"),
        zoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
        utcOffsetMinutes = 420,
    )
    private val schedule = ValidatedInitialScheduleV1.create(
        selectedWeekdays = setOf(1, 2, 3, 4, 5),
        workStart = "09:00",
        workEnd = "17:00",
        reminderTimes = listOf("10:30", "15:30"),
    ).getOrThrow()
    private val stagedEligibility = StagedEligibilityV1(
        adultConfirmed = true,
        eligibilityScopeConfirmed = true,
        safetyContentIdentity = safetyContent,
        acknowledgedAt = acknowledgementStamp,
    )

    @Test
    fun `full eligible flow moves Welcome to Age to Scope to Schedule without persistence`() {
        val age = OnboardingFlowReducerV1.reduce(
            OnboardingStateV1.Welcome,
            OnboardingActionV1.Begin,
        )
        assertIs<OnboardingStateV1.Age>(age.state)
        assertNull(age.effect)

        val scope = OnboardingFlowReducerV1.reduce(
            age.state,
            OnboardingActionV1.ConfirmAdult,
        )
        val initialScope = assertIs<OnboardingStateV1.Scope>(scope.state)
        assertTrue(initialScope.adultConfirmed)
        assertFalse(initialScope.acknowledgementChecked)
        assertNull(initialScope.eligibilityAnswer)
        assertNull(scope.effect)

        val checked = OnboardingFlowReducerV1.reduce(
            initialScope,
            OnboardingActionV1.SetAcknowledgement(true),
        )
        val checkedScope = assertIs<OnboardingStateV1.Scope>(checked.state)
        assertTrue(checkedScope.acknowledgementChecked)
        assertNull(checkedScope.eligibilityAnswer)
        assertNull(checked.effect)

        val eligible = OnboardingFlowReducerV1.reduce(
            checkedScope,
            OnboardingActionV1.SetEligibility(EligibilityAnswerV1.ELIGIBLE),
        )
        val eligibleScope = assertIs<OnboardingStateV1.Scope>(eligible.state)
        assertTrue(eligibleScope.acknowledgementChecked)
        assertEquals(EligibilityAnswerV1.ELIGIBLE, eligibleScope.eligibilityAnswer)
        assertNull(eligible.effect)

        val staged = OnboardingFlowReducerV1.reduce(
            eligibleScope,
            OnboardingActionV1.ContinueEligibleScope(safetyContent, acknowledgementStamp),
        )
        val scheduleState = assertIs<OnboardingStateV1.Schedule>(staged.state)
        assertEquals(stagedEligibility, scheduleState.stagedEligibility)
        assertNull(scheduleState.validatedSchedule)
        assertNull(scheduleState.validationError)
        assertNull(staged.effect)
    }

    @Test
    fun `minor path is safe exit and never emits persistence`() {
        val age = OnboardingFlowReducerV1.reduce(OnboardingStateV1.Welcome, OnboardingActionV1.Begin)
        val exit = OnboardingFlowReducerV1.reduce(age.state, OnboardingActionV1.ConfirmMinor)

        assertIs<OnboardingStateV1.MinorSafeExit>(exit.state)
        assertNull(exit.effect)
    }

    @Test
    fun `scope ineligible and unsure answers are separate safe exits and never emit persistence`() {
        listOf(
            EligibilityAnswerV1.INELIGIBLE,
            EligibilityAnswerV1.UNSURE,
        ).forEach { answer ->
            val scope = OnboardingStateV1.Scope(
                adultConfirmed = true,
                acknowledgementChecked = true,
                eligibilityAnswer = null,
            )
            val exit = OnboardingFlowReducerV1.reduce(
                scope,
                OnboardingActionV1.SetEligibility(answer),
            )

            assertIs<OnboardingStateV1.ScopeSafeExit>(exit.state)
            assertNull(exit.effect)
        }
    }

    @Test
    fun `continue is a no-op until both acknowledgement and eligibility are complete`() {
        val acknowledgementMissing = OnboardingStateV1.Scope(
            adultConfirmed = true,
            acknowledgementChecked = false,
            eligibilityAnswer = EligibilityAnswerV1.ELIGIBLE,
        )
        val eligibilityMissing = OnboardingStateV1.Scope(
            adultConfirmed = true,
            acknowledgementChecked = true,
            eligibilityAnswer = null,
        )
        val continueAction =
            OnboardingActionV1.ContinueEligibleScope(safetyContent, acknowledgementStamp)

        listOf(acknowledgementMissing, eligibilityMissing).forEach { incomplete ->
            val transition = OnboardingFlowReducerV1.reduce(incomplete, continueAction)

            assertEquals(incomplete, transition.state)
            assertNull(transition.effect)
        }
    }

    @Test
    fun `eligible schedule save deep copies exact staged identity stamp and schedule into one command`() {
        val saving = OnboardingFlowReducerV1.reduce(
            scheduleState(),
            OnboardingActionV1.SaveSchedule(schedule),
        )

        val savingState = assertIs<OnboardingStateV1.Saving>(saving.state)
        val effect = assertIs<OnboardingEffectV1.CommitInitialOnboarding>(saving.effect)
        assertEquals(stagedEligibility, effect.command.stagedEligibility)
        assertEquals(safetyContent, effect.command.stagedEligibility.safetyContentIdentity)
        assertEquals(acknowledgementStamp, effect.command.stagedEligibility.acknowledgedAt)
        assertEquals(schedule, effect.command.schedule)
        assertEquals(effect.command, savingState.command)
    }

    @Test
    fun `typed invalid schedule stays local and never emits persistence`() {
        val validationError = OnboardingScheduleErrorV1.WORK_END_NOT_AFTER_START

        val invalid = OnboardingFlowReducerV1.reduce(
            scheduleState(),
            OnboardingActionV1.ScheduleValidationFailed(validationError),
        )

        val invalidState = assertIs<OnboardingStateV1.Schedule>(invalid.state)
        assertEquals(stagedEligibility, invalidState.stagedEligibility)
        assertNull(invalidState.validatedSchedule)
        assertEquals(validationError, invalidState.validationError)
        assertNull(invalid.effect)
    }

    @Test
    fun `double save while saving emits only the first commit command`() {
        val first = OnboardingFlowReducerV1.reduce(
            scheduleState(),
            OnboardingActionV1.SaveSchedule(schedule),
        )
        assertIs<OnboardingEffectV1.CommitInitialOnboarding>(first.effect)

        val duplicate = OnboardingFlowReducerV1.reduce(
            first.state,
            OnboardingActionV1.SaveSchedule(schedule),
        )

        assertEquals(first.state, duplicate.state)
        assertNull(duplicate.effect)
    }

    @Test
    fun `commit failure returns to schedule with staged identity and validated draft retained`() {
        val first = OnboardingFlowReducerV1.reduce(
            scheduleState(),
            OnboardingActionV1.SaveSchedule(schedule),
        )
        val firstEffect = assertIs<OnboardingEffectV1.CommitInitialOnboarding>(first.effect)

        val failed = OnboardingFlowReducerV1.reduce(
            first.state,
            OnboardingActionV1.CommitFailed("disk"),
        )

        val retryState = assertIs<OnboardingStateV1.Schedule>(failed.state)
        assertEquals(stagedEligibility, retryState.stagedEligibility)
        assertEquals(schedule, retryState.validatedSchedule)
        assertNull(retryState.validationError)
        assertNull(failed.effect)

        val retry = OnboardingFlowReducerV1.reduce(
            retryState,
            OnboardingActionV1.SaveSchedule(schedule),
        )
        val retryEffect = assertIs<OnboardingEffectV1.CommitInitialOnboarding>(retry.effect)
        assertEquals(firstEffect.command, retryEffect.command)
    }

    @Test
    fun `permission primer is reachable only after full commit succeeds`() {
        val saving = OnboardingFlowReducerV1.reduce(
            scheduleState(),
            OnboardingActionV1.SaveSchedule(schedule),
        )

        val failed = OnboardingFlowReducerV1.reduce(
            saving.state,
            OnboardingActionV1.CommitFailed("disk"),
        )
        assertIs<OnboardingStateV1.Schedule>(failed.state)
        assertNull(failed.effect)

        val succeeded = OnboardingFlowReducerV1.reduce(
            saving.state,
            OnboardingActionV1.CommitSucceeded,
        )
        assertIs<OnboardingStateV1.PermissionPrimer>(succeeded.state)
        assertNull(succeeded.effect)
    }

    @Test
    fun `commit callbacks outside Saving are stale and success reaches primer exactly once`() {
        val scheduleState = scheduleState()

        val staleSuccess = OnboardingFlowReducerV1.reduce(
            scheduleState,
            OnboardingActionV1.CommitSucceeded,
        )
        assertEquals(scheduleState, staleSuccess.state)
        assertNull(staleSuccess.effect)

        val staleFailure = OnboardingFlowReducerV1.reduce(
            scheduleState,
            OnboardingActionV1.CommitFailed("late"),
        )
        assertEquals(scheduleState, staleFailure.state)
        assertNull(staleFailure.effect)

        val saving = OnboardingFlowReducerV1.reduce(
            scheduleState,
            OnboardingActionV1.SaveSchedule(schedule),
        )
        val succeeded = OnboardingFlowReducerV1.reduce(
            saving.state,
            OnboardingActionV1.CommitSucceeded,
        )
        val primer = assertIs<OnboardingStateV1.PermissionPrimer>(succeeded.state)

        val duplicateSuccess = OnboardingFlowReducerV1.reduce(
            primer,
            OnboardingActionV1.CommitSucceeded,
        )
        assertEquals(primer, duplicateSuccess.state)
        assertNull(duplicateSuccess.effect)

        val lateFailure = OnboardingFlowReducerV1.reduce(
            primer,
            OnboardingActionV1.CommitFailed("late"),
        )
        assertEquals(primer, lateFailure.state)
        assertNull(lateFailure.effect)
    }

    @Test
    fun `process loss before commit discards staged RAM and restarts at Welcome`() {
        val staged = OnboardingFlowReducerV1.reduce(
            OnboardingStateV1.Scope(
                adultConfirmed = true,
                acknowledgementChecked = true,
                eligibilityAnswer = EligibilityAnswerV1.ELIGIBLE,
            ),
            OnboardingActionV1.ContinueEligibleScope(safetyContent, acknowledgementStamp),
        )
        assertIs<OnboardingStateV1.Schedule>(staged.state)

        val recreated = OnboardingFlowReducerV1.initialState(profileCommitted = false)

        assertEquals(OnboardingStateV1.Welcome, recreated)
    }

    @Test
    fun `deep links cannot bypass uncommitted onboarding`() {
        assertEquals(
            EntryRouteV1.ONBOARDING,
            OnboardingEntryGateV1.resolve(profileCommitted = false, requested = EntryRouteV1.CHECK_IN),
        )
        assertEquals(
            EntryRouteV1.CHECK_IN,
            OnboardingEntryGateV1.resolve(profileCommitted = true, requested = EntryRouteV1.CHECK_IN),
        )
    }

    private fun scheduleState(): OnboardingStateV1.Schedule = OnboardingStateV1.Schedule(
        stagedEligibility = stagedEligibility,
        validatedSchedule = null,
        validationError = null,
    )
}
