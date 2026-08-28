package vn.nhip2phut.app.checkin

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import vn.nhip2phut.domain.checkin.PersistedCheckInAnswersV1
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.Stiffness

class CheckInFlowReducerV1Test {
    @Test
    fun `new check-in starts with red flag unanswered and no persistence effect`() {
        val started = CheckInFlowReducerV1.start(CheckInFlowStartV1.New)

        val redFlag = assertIs<CheckInFlowStateV1.RedFlag>(started.state)
        assertNull(redFlag.selected)
        assertNull(redFlag.parentCheckInId)
        assertNull(started.effect)
    }

    @Test
    fun `later answers cannot bypass the unanswered red-flag gate`() {
        val started = CheckInFlowReducerV1.start(CheckInFlowStartV1.New)
        val bypass = CheckInFlowReducerV1.reduce(
            started.state,
            CheckInFlowActionV1.SetEnergy(Energy.GOOD),
        )

        assertEquals(started.state, bypass.state)
        assertNull(bypass.effect)
    }

    @Test
    fun `red flag true short-circuits immediately to canonical red stop`() {
        val started = CheckInFlowReducerV1.start(CheckInFlowStartV1.New)
        val stopped = CheckInFlowReducerV1.reduce(
            started.state,
            CheckInFlowActionV1.AnswerRedFlag(true),
        )

        assertIs<CheckInFlowStateV1.Submitting>(stopped.state)
        val effect = assertIs<CheckInFlowEffectV1.EvaluateCheckIn>(stopped.effect)
        assertNull(effect.parentCheckInId)
        assertEquals(PersistedCheckInAnswersV1.RedFlagStop, effect.answers)
    }

    @Test
    fun `red flag false opens the acute gate with no preselection`() {
        val started = CheckInFlowReducerV1.start(CheckInFlowStartV1.New)
        val acute = CheckInFlowReducerV1.reduce(
            started.state,
            CheckInFlowActionV1.AnswerRedFlag(false),
        )

        val acuteState = assertIs<CheckInFlowStateV1.AcuteGate>(acute.state)
        assertNull(acuteState.selected)
        assertNull(acute.effect)
    }

    @Test
    fun `each non-none acute issue short-circuits before full fields`() {
        listOf(
            AcuteIssue.ACUTE_ILLNESS,
            AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY,
            AcuteIssue.MEDICALLY_RESTRICTED,
        ).forEach { issue ->
            val acute = newAcuteGate()
            val stopped = CheckInFlowReducerV1.reduce(
                acute,
                CheckInFlowActionV1.AnswerAcuteIssue(issue),
            )

            assertIs<CheckInFlowStateV1.Submitting>(stopped.state)
            val effect = assertIs<CheckInFlowEffectV1.EvaluateCheckIn>(stopped.effect)
            assertNull(effect.parentCheckInId)
            assertEquals(PersistedCheckInAnswersV1.AcuteStop(issue), effect.answers)
        }
    }

    @Test
    fun `full fields appear only after explicit acute none and start unanswered`() {
        val acute = newAcuteGate()
        val full = CheckInFlowReducerV1.reduce(
            acute,
            CheckInFlowActionV1.AnswerAcuteIssue(AcuteIssue.NONE),
        )

        val form = assertIs<CheckInFlowStateV1.FullForm>(full.state)
        assertEquals(false, form.redFlag)
        assertEquals(AcuteIssue.NONE, form.acuteIssue)
        assertNull(form.energy)
        assertNull(form.stiffness)
        assertNull(form.intent)
        assertNull(full.effect)
    }

    @Test
    fun `incomplete full form never emits evaluate effect`() {
        val full = newFullForm()
        val withEnergy = CheckInFlowReducerV1.reduce(
            full,
            CheckInFlowActionV1.SetEnergy(Energy.OKAY),
        )
        val attempted = CheckInFlowReducerV1.reduce(
            withEnergy.state,
            CheckInFlowActionV1.Submit,
        )

        assertIs<CheckInFlowStateV1.FullForm>(attempted.state)
        assertNull(attempted.effect)
    }

    @Test
    fun `complete full form submits canonical full answers`() {
        val full = newFullForm()
        val withEnergy = CheckInFlowReducerV1.reduce(
            full,
            CheckInFlowActionV1.SetEnergy(Energy.GOOD),
        )
        val withStiffness = CheckInFlowReducerV1.reduce(
            withEnergy.state,
            CheckInFlowActionV1.SetStiffness(Stiffness.MILD),
        )
        val withIntent = CheckInFlowReducerV1.reduce(
            withStiffness.state,
            CheckInFlowActionV1.SetIntent(Intent.MODERATE),
        )
        val submitted = CheckInFlowReducerV1.reduce(
            withIntent.state,
            CheckInFlowActionV1.Submit,
        )

        assertIs<CheckInFlowStateV1.Submitting>(submitted.state)
        val effect = assertIs<CheckInFlowEffectV1.EvaluateCheckIn>(submitted.effect)
        assertNull(effect.parentCheckInId)
        assertEquals(
            PersistedCheckInAnswersV1.Full(
                energy = Energy.GOOD,
                stiffness = Stiffness.MILD,
                intent = Intent.MODERATE,
            ),
            effect.answers,
        )
    }

    @Test
    fun `reconfirmation prefills all five fields but revisits red and acute gates before explicit submit`() {
        val parentCheckInId = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val previousAnswers = PersistedCheckInAnswersV1.Full(
            energy = Energy.OKAY,
            stiffness = Stiffness.NOTABLE,
            intent = Intent.GENTLE,
        )

        val started = CheckInFlowReducerV1.start(
            CheckInFlowStartV1.Reconfirm(
                parentCheckInId = parentCheckInId,
                answers = previousAnswers,
            ),
        )

        val redGate = assertIs<CheckInFlowStateV1.RedFlag>(started.state)
        assertEquals(parentCheckInId, redGate.parentCheckInId)
        assertEquals(false, redGate.selected)
        assertNull(started.effect)

        val acute = CheckInFlowReducerV1.reduce(
            redGate,
            CheckInFlowActionV1.AnswerRedFlag(false),
        )
        val acuteGate = assertIs<CheckInFlowStateV1.AcuteGate>(acute.state)
        assertEquals(AcuteIssue.NONE, acuteGate.selected)
        assertNull(acute.effect)

        val full = CheckInFlowReducerV1.reduce(
            acuteGate,
            CheckInFlowActionV1.AnswerAcuteIssue(AcuteIssue.NONE),
        )
        val form = assertIs<CheckInFlowStateV1.FullForm>(full.state)
        assertEquals(parentCheckInId, form.parentCheckInId)
        assertEquals(false, form.redFlag)
        assertEquals(AcuteIssue.NONE, form.acuteIssue)
        assertEquals(Energy.OKAY, form.energy)
        assertEquals(Stiffness.NOTABLE, form.stiffness)
        assertEquals(Intent.GENTLE, form.intent)
        assertNull(full.effect)

        val submitted = CheckInFlowReducerV1.reduce(form, CheckInFlowActionV1.Submit)
        val effect = assertIs<CheckInFlowEffectV1.EvaluateCheckIn>(submitted.effect)
        assertEquals(parentCheckInId, effect.parentCheckInId)
        assertEquals(previousAnswers, effect.answers)
    }

    private fun newAcuteGate(): CheckInFlowStateV1.AcuteGate {
        val started = CheckInFlowReducerV1.start(CheckInFlowStartV1.New)
        val acute = CheckInFlowReducerV1.reduce(
            started.state,
            CheckInFlowActionV1.AnswerRedFlag(false),
        )
        return assertIs(acute.state)
    }

    private fun newFullForm(): CheckInFlowStateV1.FullForm {
        val full = CheckInFlowReducerV1.reduce(
            newAcuteGate(),
            CheckInFlowActionV1.AnswerAcuteIssue(AcuteIssue.NONE),
        )
        return assertIs(full.state)
    }
}
