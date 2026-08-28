package vn.nhip2phut.domain.rule

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.DayModeCapMode
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.Mode
import vn.nhip2phut.domain.model.Stiffness

class RuleEngineV1Test {
    private val engine: RuleEngineV1 = DefaultRuleEngineV1

    @Test
    fun `all 1296 valid inputs follow the canonical first-match table`() {
        var evaluatedCases = 0

        for (safetyLockActive in listOf(false, true)) {
            for (redFlag in listOf(false, true)) {
                for (acuteIssue in AcuteIssue.entries) {
                    for (energy in Energy.entries) {
                        for (stiffness in Stiffness.entries) {
                            for (intent in Intent.entries) {
                                for (dayModeCap in listOf(null, DayModeCapMode.MAINTAIN, DayModeCapMode.RECOVER)) {
                                    val input = validInput(
                                        safetyLockActive = safetyLockActive,
                                        redFlag = redFlag,
                                        acuteIssue = acuteIssue,
                                        energy = energy,
                                        stiffness = stiffness,
                                        intent = intent,
                                        dayModeCap = dayModeCap,
                                    )

                                    assertEquals(
                                        expectedValidResult(
                                            safetyLockActive = safetyLockActive,
                                            redFlag = redFlag,
                                            acuteIssue = acuteIssue,
                                            energy = energy,
                                            stiffness = stiffness,
                                            intent = intent,
                                            dayModeCap = dayModeCap,
                                        ),
                                        engine.evaluate(input),
                                        "Unexpected result for $input",
                                    )
                                    evaluatedCases += 1
                                }
                            }
                        }
                    }
                }
            }
        }

        assertEquals(1_296, evaluatedCases)
    }

    @Test
    fun `lazy validation preserves safety precedence and canonical invalid-field order`() {
        assertEquals(
            blockedResult(),
            engine.evaluate(
                RuleInputDraftV1(
                    safetyLockActive = true,
                    redFlag = DraftField.Invalid,
                    acuteIssue = DraftField.Missing,
                    energy = DraftField.Invalid,
                    stiffness = DraftField.Missing,
                    intent = DraftField.Invalid,
                    dayModeCap = DraftField.Invalid,
                ),
            ),
        )

        assertEquals(
            urgentResult(),
            engine.evaluate(
                RuleInputDraftV1(
                    safetyLockActive = false,
                    redFlag = DraftField.Valid(true),
                    acuteIssue = DraftField.Invalid,
                    energy = DraftField.Missing,
                    stiffness = DraftField.Invalid,
                    intent = DraftField.Missing,
                    dayModeCap = DraftField.Invalid,
                ),
            ),
        )

        assertIncomplete(
            input = validInput().copy(redFlag = DraftField.Missing),
            reason = ReasonCode.SAF_INPUT_MISSING,
            invalidFields = listOf(RuleInputField.RED_FLAG),
            route = PresentationRouteV1.INCOMPLETE_FORM,
        )
        assertIncomplete(
            input = validInput().copy(redFlag = DraftField.Invalid),
            reason = ReasonCode.SAF_INPUT_INVALID,
            invalidFields = listOf(RuleInputField.RED_FLAG),
            route = PresentationRouteV1.INCOMPLETE_FORM,
        )
        assertIncomplete(
            input = validInput().copy(acuteIssue = DraftField.Missing),
            reason = ReasonCode.SAF_INPUT_MISSING,
            invalidFields = listOf(RuleInputField.ACUTE_ISSUE),
            route = PresentationRouteV1.INCOMPLETE_FORM,
        )
        assertIncomplete(
            input = validInput().copy(acuteIssue = DraftField.Invalid),
            reason = ReasonCode.SAF_INPUT_INVALID,
            invalidFields = listOf(RuleInputField.ACUTE_ISSUE),
            route = PresentationRouteV1.INCOMPLETE_FORM,
        )

        for ((acuteIssue, expectedReason, expectedRoute) in listOf(
            Triple(
                AcuteIssue.ACUTE_ILLNESS,
                ReasonCode.SAF_ACUTE_ILLNESS,
                PresentationRouteV1.PAUSE_ACUTE_ILLNESS,
            ),
            Triple(
                AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY,
                ReasonCode.SAF_ACUTE_NEW_OR_WORSENING_PAIN,
                PresentationRouteV1.PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY,
            ),
            Triple(
                AcuteIssue.MEDICALLY_RESTRICTED,
                ReasonCode.SAF_MEDICALLY_RESTRICTED,
                PresentationRouteV1.PAUSE_MEDICALLY_RESTRICTED,
            ),
        )) {
            val input = RuleInputDraftV1(
                safetyLockActive = false,
                redFlag = DraftField.Valid(false),
                acuteIssue = DraftField.Valid(acuteIssue),
                energy = DraftField.Missing,
                stiffness = DraftField.Invalid,
                intent = DraftField.Missing,
                dayModeCap = DraftField.Invalid,
            )
            assertEquals(
                noModeResult(
                    outcome = RuleOutcome.PAUSE_TODAY,
                    reasons = listOf(expectedReason),
                    presentationRoute = expectedRoute,
                ),
                engine.evaluate(input),
            )
        }

        assertIncomplete(
            input = validInput().copy(
                energy = DraftField.Invalid,
                stiffness = DraftField.Missing,
                intent = DraftField.Invalid,
                dayModeCap = DraftField.Invalid,
            ),
            reason = ReasonCode.SAF_INPUT_INVALID,
            invalidFields = listOf(
                RuleInputField.ENERGY,
                RuleInputField.STIFFNESS,
                RuleInputField.INTENT,
                RuleInputField.DAY_MODE_CAP,
            ),
            route = PresentationRouteV1.INCOMPLETE_FORM,
        )
        assertIncomplete(
            input = validInput().copy(
                stiffness = DraftField.Missing,
                intent = DraftField.Invalid,
                dayModeCap = DraftField.Invalid,
            ),
            reason = ReasonCode.SAF_INPUT_MISSING,
            invalidFields = listOf(
                RuleInputField.STIFFNESS,
                RuleInputField.INTENT,
                RuleInputField.DAY_MODE_CAP,
            ),
            route = PresentationRouteV1.INCOMPLETE_FORM,
        )
        assertIncomplete(
            input = validInput().copy(dayModeCap = DraftField.Invalid),
            reason = ReasonCode.SAF_INPUT_INVALID,
            invalidFields = listOf(RuleInputField.DAY_MODE_CAP),
            route = PresentationRouteV1.INCOMPLETE_CONSTRAINT_DATA,
        )

        assertEquals(
            engine.evaluate(validInput(dayModeCap = null)),
            engine.evaluate(validInput().copy(dayModeCap = DraftField.Missing)),
            "An absent optional cap must normalize to no active cap",
        )
    }

    @Test
    fun `all no-mode outcomes expose no mode or routine-equivalent authorization`() {
        val noModeResults = listOf(
            engine.evaluate(validInput(safetyLockActive = true)),
            engine.evaluate(validInput(redFlag = true)),
            engine.evaluate(validInput(acuteIssue = AcuteIssue.ACUTE_ILLNESS)),
            engine.evaluate(validInput().copy(energy = DraftField.Invalid)),
            engine.evaluate(validInput(intent = Intent.REST)),
        )

        assertEquals(
            listOf(
                RuleOutcome.BLOCKED_FOR_TODAY,
                RuleOutcome.URGENT_STOP,
                RuleOutcome.PAUSE_TODAY,
                RuleOutcome.INCOMPLETE,
                RuleOutcome.REST_ONLY,
            ),
            noModeResults.map { it.outcome },
        )
        noModeResults.forEach { result ->
            assertNull(result.baseMode)
            assertNull(result.effectiveMode)
            assertTrue(result.allowedModes.isEmpty())
        }
    }

    @Test
    fun `fixed-seed property suite is deterministic and never raises mode across 10000 cases`() {
        val random = Random(0x51AF_0001)
        var evaluatedCases = 0

        repeat(10_000) {
            val input = randomDraft(random)
            val first = engine.evaluate(input)
            val second = engine.evaluate(input.copy())

            assertEquals(first, second, "Determinism failed for $input")
            assertEquals(RULE_VERSION_V1, first.ruleVersion)
            assertCanonicalResult(first)

            if (input.safetyLockActive) {
                assertEquals(RuleOutcome.BLOCKED_FOR_TODAY, first.outcome)
            } else if (input.redFlag == DraftField.Valid(true)) {
                assertEquals(RuleOutcome.URGENT_STOP, first.outcome)
            }

            val baseInput = validInput(
                redFlag = false,
                acuteIssue = AcuteIssue.NONE,
                energy = Energy.entries[random.nextInt(Energy.entries.size)],
                stiffness = Stiffness.entries[random.nextInt(Stiffness.entries.size)],
                intent = Intent.entries[random.nextInt(Intent.entries.size)],
                dayModeCap = null,
            )
            val uncapped = engine.evaluate(baseInput)
            val maintainCap = engine.evaluate(
                baseInput.copy(dayModeCap = DraftField.Valid(DayModeCapMode.MAINTAIN)),
            )
            val recoverCap = engine.evaluate(
                baseInput.copy(dayModeCap = DraftField.Valid(DayModeCapMode.RECOVER)),
            )

            assertEquals(uncapped.outcome, maintainCap.outcome)
            assertEquals(uncapped.outcome, recoverCap.outcome)
            assertEquals(uncapped.baseMode, maintainCap.baseMode)
            assertEquals(uncapped.baseMode, recoverCap.baseMode)
            assertModeNotHigher(maintainCap.effectiveMode, uncapped.effectiveMode)
            assertModeNotHigher(recoverCap.effectiveMode, maintainCap.effectiveMode)
            evaluatedCases += 1
        }

        assertEquals(10_000, evaluatedCases)
    }

    private fun assertIncomplete(
        input: RuleInputDraftV1,
        reason: ReasonCode,
        invalidFields: List<RuleInputField>,
        route: PresentationRouteV1,
    ) {
        assertEquals(
            noModeResult(
                outcome = RuleOutcome.INCOMPLETE,
                reasons = listOf(reason),
                invalidFields = invalidFields,
                presentationRoute = route,
            ),
            engine.evaluate(input),
        )
    }

    private fun assertCanonicalResult(result: RuleResultV1) {
        assertEquals(result.reasonCodes.distinct(), result.reasonCodes)
        assertEquals(result.reasonCodes.sortedBy { it.ordinal }, result.reasonCodes)
        assertEquals(result.invalidFields.distinct(), result.invalidFields)
        assertEquals(result.invalidFields.sortedBy { it.ordinal }, result.invalidFields)

        val expectedBaseMode = when (result.outcome) {
            RuleOutcome.RECOVER -> Mode.RECOVER
            RuleOutcome.MAINTAIN -> Mode.MAINTAIN
            RuleOutcome.BUILD -> Mode.BUILD
            else -> null
        }
        assertEquals(expectedBaseMode, result.baseMode)

        if (result.baseMode == null) {
            assertNull(result.effectiveMode)
            assertTrue(result.allowedModes.isEmpty())
        } else {
            val effectiveMode = requireNotNull(result.effectiveMode)
            assertModeNotHigher(effectiveMode, result.baseMode)
            assertEquals(allowedModes(effectiveMode), result.allowedModes)
            val capApplied = modeRank(effectiveMode) < modeRank(result.baseMode)
            assertEquals(capApplied, ReasonCode.SAF_DAY_MODE_CAP_APPLIED in result.reasonCodes)
            if (capApplied) {
                assertEquals(ReasonCode.SAF_DAY_MODE_CAP_APPLIED, result.reasonCodes.last())
            }
        }

        val expectedRoute = when (result.outcome) {
            RuleOutcome.BLOCKED_FOR_TODAY -> PresentationRouteV1.BLOCKED_HOLD
            RuleOutcome.URGENT_STOP -> PresentationRouteV1.URGENT_STOP
            RuleOutcome.PAUSE_TODAY -> result.presentationRoute
            RuleOutcome.INCOMPLETE -> if (result.invalidFields == listOf(RuleInputField.DAY_MODE_CAP)) {
                PresentationRouteV1.INCOMPLETE_CONSTRAINT_DATA
            } else {
                PresentationRouteV1.INCOMPLETE_FORM
            }
            RuleOutcome.REST_ONLY -> PresentationRouteV1.REST_ONLY
            RuleOutcome.RECOVER,
            RuleOutcome.MAINTAIN,
            RuleOutcome.BUILD,
            -> PresentationRouteV1.MODE_RECOMMENDATION
        }
        assertEquals(expectedRoute, result.presentationRoute)
    }

    private fun assertModeNotHigher(actual: Mode?, ceiling: Mode?) {
        if (actual == null || ceiling == null) {
            assertEquals(ceiling, actual)
            return
        }
        assertTrue(
            modeRank(actual) <= modeRank(ceiling),
            "$actual must not be higher than $ceiling",
        )
    }

    private fun randomDraft(random: Random): RuleInputDraftV1 = RuleInputDraftV1(
        safetyLockActive = random.nextBoolean(),
        redFlag = random.field(listOf(false, true)),
        acuteIssue = random.field(AcuteIssue.entries),
        energy = random.field(Energy.entries),
        stiffness = random.field(Stiffness.entries),
        intent = random.field(Intent.entries),
        dayModeCap = random.field(listOf(null, DayModeCapMode.RECOVER, DayModeCapMode.MAINTAIN)),
    )

    private fun <T> Random.field(values: List<T>): DraftField<T> = when (nextInt(values.size + 2)) {
        0 -> DraftField.Missing
        1 -> DraftField.Invalid
        else -> DraftField.Valid(values[nextInt(values.size)])
    }

    private fun validInput(
        safetyLockActive: Boolean = false,
        redFlag: Boolean = false,
        acuteIssue: AcuteIssue = AcuteIssue.NONE,
        energy: Energy = Energy.OKAY,
        stiffness: Stiffness = Stiffness.NONE,
        intent: Intent = Intent.GENTLE,
        dayModeCap: DayModeCapMode? = null,
    ): RuleInputDraftV1 = RuleInputDraftV1(
        safetyLockActive = safetyLockActive,
        redFlag = DraftField.Valid(redFlag),
        acuteIssue = DraftField.Valid(acuteIssue),
        energy = DraftField.Valid(energy),
        stiffness = DraftField.Valid(stiffness),
        intent = DraftField.Valid(intent),
        dayModeCap = DraftField.Valid(dayModeCap),
    )

    private fun expectedValidResult(
        safetyLockActive: Boolean,
        redFlag: Boolean,
        acuteIssue: AcuteIssue,
        energy: Energy,
        stiffness: Stiffness,
        intent: Intent,
        dayModeCap: DayModeCapMode?,
    ): RuleResultV1 {
        val uncapped = when {
            safetyLockActive -> blockedResult()
            redFlag -> urgentResult()
            acuteIssue == AcuteIssue.ACUTE_ILLNESS -> noModeResult(
                outcome = RuleOutcome.PAUSE_TODAY,
                reasons = listOf(ReasonCode.SAF_ACUTE_ILLNESS),
                presentationRoute = PresentationRouteV1.PAUSE_ACUTE_ILLNESS,
            )
            acuteIssue == AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY -> noModeResult(
                outcome = RuleOutcome.PAUSE_TODAY,
                reasons = listOf(ReasonCode.SAF_ACUTE_NEW_OR_WORSENING_PAIN),
                presentationRoute = PresentationRouteV1.PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY,
            )
            acuteIssue == AcuteIssue.MEDICALLY_RESTRICTED -> noModeResult(
                outcome = RuleOutcome.PAUSE_TODAY,
                reasons = listOf(ReasonCode.SAF_MEDICALLY_RESTRICTED),
                presentationRoute = PresentationRouteV1.PAUSE_MEDICALLY_RESTRICTED,
            )
            intent == Intent.REST -> noModeResult(
                outcome = RuleOutcome.REST_ONLY,
                reasons = listOf(ReasonCode.SAF_INTENT_REST),
                presentationRoute = PresentationRouteV1.REST_ONLY,
            )
            energy == Energy.LOW || stiffness == Stiffness.NOTABLE -> modeResult(
                outcome = RuleOutcome.RECOVER,
                baseMode = Mode.RECOVER,
                reasons = buildList {
                    if (energy == Energy.LOW) add(ReasonCode.SAF_ENERGY_LOW)
                    if (stiffness == Stiffness.NOTABLE) add(ReasonCode.SAF_STIFFNESS_NOTABLE)
                },
            )
            energy == Energy.GOOD &&
                stiffness in setOf(Stiffness.NONE, Stiffness.MILD) &&
                intent == Intent.MODERATE -> modeResult(
                outcome = RuleOutcome.BUILD,
                baseMode = Mode.BUILD,
                reasons = listOf(ReasonCode.SAF_BUILD_CONDITIONS),
            )
            else -> modeResult(
                outcome = RuleOutcome.MAINTAIN,
                baseMode = Mode.MAINTAIN,
                reasons = listOf(ReasonCode.SAF_MAINTAIN_DEFAULT),
            )
        }

        val baseMode = uncapped.baseMode ?: return uncapped
        val capMode = when (dayModeCap) {
            null -> null
            DayModeCapMode.RECOVER -> Mode.RECOVER
            DayModeCapMode.MAINTAIN -> Mode.MAINTAIN
        }
        val effectiveMode = if (capMode == null || modeRank(baseMode) <= modeRank(capMode)) {
            baseMode
        } else {
            capMode
        }
        return uncapped.copy(
            effectiveMode = effectiveMode,
            allowedModes = allowedModes(effectiveMode),
            reasonCodes = uncapped.reasonCodes + listOfNotNull(
                ReasonCode.SAF_DAY_MODE_CAP_APPLIED.takeIf { effectiveMode != baseMode },
            ),
        )
    }

    private fun blockedResult(): RuleResultV1 = noModeResult(
        outcome = RuleOutcome.BLOCKED_FOR_TODAY,
        reasons = listOf(ReasonCode.SAF_LOCK_ACTIVE),
        presentationRoute = PresentationRouteV1.BLOCKED_HOLD,
    )

    private fun urgentResult(): RuleResultV1 = noModeResult(
        outcome = RuleOutcome.URGENT_STOP,
        reasons = listOf(ReasonCode.SAF_RED_FLAG_PRESENT),
        presentationRoute = PresentationRouteV1.URGENT_STOP,
    )

    private fun noModeResult(
        outcome: RuleOutcome,
        reasons: List<ReasonCode>,
        invalidFields: List<RuleInputField> = emptyList(),
        presentationRoute: PresentationRouteV1,
    ): RuleResultV1 = RuleResultV1(
        ruleVersion = RULE_VERSION_V1,
        outcome = outcome,
        baseMode = null,
        effectiveMode = null,
        allowedModes = emptyList(),
        reasonCodes = reasons,
        invalidFields = invalidFields,
        presentationRoute = presentationRoute,
    )

    private fun modeResult(
        outcome: RuleOutcome,
        baseMode: Mode,
        reasons: List<ReasonCode>,
    ): RuleResultV1 = RuleResultV1(
        ruleVersion = RULE_VERSION_V1,
        outcome = outcome,
        baseMode = baseMode,
        effectiveMode = baseMode,
        allowedModes = allowedModes(baseMode),
        reasonCodes = reasons,
        invalidFields = emptyList(),
        presentationRoute = PresentationRouteV1.MODE_RECOMMENDATION,
    )

    private fun allowedModes(mode: Mode): List<Mode> = when (mode) {
        Mode.RECOVER -> listOf(Mode.RECOVER)
        Mode.MAINTAIN -> listOf(Mode.MAINTAIN, Mode.RECOVER)
        Mode.BUILD -> listOf(Mode.BUILD, Mode.MAINTAIN, Mode.RECOVER)
    }

    private fun modeRank(mode: Mode): Int = when (mode) {
        Mode.RECOVER -> 0
        Mode.MAINTAIN -> 1
        Mode.BUILD -> 2
    }
}
