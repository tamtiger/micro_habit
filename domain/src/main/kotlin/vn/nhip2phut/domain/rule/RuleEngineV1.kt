package vn.nhip2phut.domain.rule

import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.DayModeCapMode
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.Mode
import vn.nhip2phut.domain.model.Stiffness

const val RULE_VERSION_V1: Int = 1

sealed interface DraftField<out T> {
    data object Missing : DraftField<Nothing>

    data object Invalid : DraftField<Nothing>

    data class Valid<T>(val value: T) : DraftField<T>
}

data class RuleInputDraftV1(
    val safetyLockActive: Boolean,
    val redFlag: DraftField<Boolean>,
    val acuteIssue: DraftField<AcuteIssue>,
    val energy: DraftField<Energy>,
    val stiffness: DraftField<Stiffness>,
    val intent: DraftField<Intent>,
    val dayModeCap: DraftField<DayModeCapMode?>,
)

enum class RuleOutcome {
    BLOCKED_FOR_TODAY,
    URGENT_STOP,
    PAUSE_TODAY,
    INCOMPLETE,
    REST_ONLY,
    RECOVER,
    MAINTAIN,
    BUILD,
}

enum class ReasonCode {
    SAF_LOCK_ACTIVE,
    SAF_RED_FLAG_PRESENT,
    SAF_INPUT_MISSING,
    SAF_INPUT_INVALID,
    SAF_ACUTE_ILLNESS,
    SAF_ACUTE_NEW_OR_WORSENING_PAIN,
    SAF_MEDICALLY_RESTRICTED,
    SAF_INTENT_REST,
    SAF_ENERGY_LOW,
    SAF_STIFFNESS_NOTABLE,
    SAF_BUILD_CONDITIONS,
    SAF_MAINTAIN_DEFAULT,
    SAF_DAY_MODE_CAP_APPLIED,
}

enum class RuleInputField(val serializedName: String) {
    RED_FLAG("red_flag"),
    ACUTE_ISSUE("acute_issue"),
    ENERGY("energy"),
    STIFFNESS("stiffness"),
    INTENT("intent"),
    DAY_MODE_CAP("day_mode_cap"),
}

enum class PresentationRouteV1 {
    BLOCKED_HOLD,
    URGENT_STOP,
    PAUSE_ACUTE_ILLNESS,
    PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY,
    PAUSE_MEDICALLY_RESTRICTED,
    INCOMPLETE_FORM,
    INCOMPLETE_CONSTRAINT_DATA,
    REST_ONLY,
    MODE_RECOMMENDATION,
}

data class RuleResultV1(
    val ruleVersion: Int,
    val outcome: RuleOutcome,
    val baseMode: Mode?,
    val effectiveMode: Mode?,
    val allowedModes: List<Mode>,
    val reasonCodes: List<ReasonCode>,
    val invalidFields: List<RuleInputField>,
    val presentationRoute: PresentationRouteV1,
)

fun interface RuleEngineV1 {
    fun evaluate(draft: RuleInputDraftV1): RuleResultV1
}

object DefaultRuleEngineV1 : RuleEngineV1 {
    override fun evaluate(draft: RuleInputDraftV1): RuleResultV1 {
        if (draft.safetyLockActive) {
            return noModeResult(
                outcome = RuleOutcome.BLOCKED_FOR_TODAY,
                reason = ReasonCode.SAF_LOCK_ACTIVE,
                route = PresentationRouteV1.BLOCKED_HOLD,
            )
        }

        when (val redFlag = draft.redFlag) {
            DraftField.Missing -> return incomplete(
                reason = ReasonCode.SAF_INPUT_MISSING,
                invalidFields = listOf(RuleInputField.RED_FLAG),
            )
            DraftField.Invalid -> return incomplete(
                reason = ReasonCode.SAF_INPUT_INVALID,
                invalidFields = listOf(RuleInputField.RED_FLAG),
            )
            is DraftField.Valid -> if (redFlag.value) {
                return noModeResult(
                    outcome = RuleOutcome.URGENT_STOP,
                    reason = ReasonCode.SAF_RED_FLAG_PRESENT,
                    route = PresentationRouteV1.URGENT_STOP,
                )
            }
        }

        when (val acuteIssue = draft.acuteIssue) {
            DraftField.Missing -> return incomplete(
                reason = ReasonCode.SAF_INPUT_MISSING,
                invalidFields = listOf(RuleInputField.ACUTE_ISSUE),
            )
            DraftField.Invalid -> return incomplete(
                reason = ReasonCode.SAF_INPUT_INVALID,
                invalidFields = listOf(RuleInputField.ACUTE_ISSUE),
            )
            is DraftField.Valid -> when (acuteIssue.value) {
                AcuteIssue.NONE -> Unit
                AcuteIssue.ACUTE_ILLNESS -> return pauseToday(
                    reason = ReasonCode.SAF_ACUTE_ILLNESS,
                    route = PresentationRouteV1.PAUSE_ACUTE_ILLNESS,
                )
                AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY -> return pauseToday(
                    reason = ReasonCode.SAF_ACUTE_NEW_OR_WORSENING_PAIN,
                    route = PresentationRouteV1.PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY,
                )
                AcuteIssue.MEDICALLY_RESTRICTED -> return pauseToday(
                    reason = ReasonCode.SAF_MEDICALLY_RESTRICTED,
                    route = PresentationRouteV1.PAUSE_MEDICALLY_RESTRICTED,
                )
            }
        }

        val fieldErrors = buildList {
            draft.energy.errorOrNull(RuleInputField.ENERGY)?.let(::add)
            draft.stiffness.errorOrNull(RuleInputField.STIFFNESS)?.let(::add)
            draft.intent.errorOrNull(RuleInputField.INTENT)?.let(::add)
            if (draft.dayModeCap is DraftField.Invalid) {
                add(FieldError(RuleInputField.DAY_MODE_CAP, FieldErrorKind.INVALID))
            }
        }
        if (fieldErrors.isNotEmpty()) {
            return incomplete(
                reason = when (fieldErrors.first().kind) {
                    FieldErrorKind.MISSING -> ReasonCode.SAF_INPUT_MISSING
                    FieldErrorKind.INVALID -> ReasonCode.SAF_INPUT_INVALID
                },
                invalidFields = fieldErrors.map { it.field },
            )
        }

        val energy = draft.energy.requiredValue()
        val stiffness = draft.stiffness.requiredValue()
        val intent = draft.intent.requiredValue()
        val dayModeCap = when (val cap = draft.dayModeCap) {
            DraftField.Missing -> null
            DraftField.Invalid -> error("Invalid cap must be rejected before evaluation")
            is DraftField.Valid -> cap.value
        }

        val baseResult = when {
            intent == Intent.REST -> noModeResult(
                outcome = RuleOutcome.REST_ONLY,
                reason = ReasonCode.SAF_INTENT_REST,
                route = PresentationRouteV1.REST_ONLY,
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

        return applyCap(baseResult, dayModeCap)
    }

    private fun applyCap(result: RuleResultV1, cap: DayModeCapMode?): RuleResultV1 {
        val baseMode = result.baseMode ?: return result
        val effectiveMode = when (cap) {
            null -> baseMode
            DayModeCapMode.RECOVER -> Mode.RECOVER
            DayModeCapMode.MAINTAIN -> when (baseMode) {
                Mode.BUILD -> Mode.MAINTAIN
                Mode.MAINTAIN -> Mode.MAINTAIN
                Mode.RECOVER -> Mode.RECOVER
            }
        }
        return result.copy(
            effectiveMode = effectiveMode,
            allowedModes = allowedModes(effectiveMode),
            reasonCodes = result.reasonCodes + listOfNotNull(
                ReasonCode.SAF_DAY_MODE_CAP_APPLIED.takeIf { effectiveMode != baseMode },
            ),
        )
    }

    private fun pauseToday(
        reason: ReasonCode,
        route: PresentationRouteV1,
    ): RuleResultV1 = noModeResult(
        outcome = RuleOutcome.PAUSE_TODAY,
        reason = reason,
        route = route,
    )

    private fun incomplete(
        reason: ReasonCode,
        invalidFields: List<RuleInputField>,
    ): RuleResultV1 = RuleResultV1(
        ruleVersion = RULE_VERSION_V1,
        outcome = RuleOutcome.INCOMPLETE,
        baseMode = null,
        effectiveMode = null,
        allowedModes = emptyList(),
        reasonCodes = listOf(reason),
        invalidFields = invalidFields,
        presentationRoute = if (invalidFields == listOf(RuleInputField.DAY_MODE_CAP)) {
            PresentationRouteV1.INCOMPLETE_CONSTRAINT_DATA
        } else {
            PresentationRouteV1.INCOMPLETE_FORM
        },
    )

    private fun noModeResult(
        outcome: RuleOutcome,
        reason: ReasonCode,
        route: PresentationRouteV1,
    ): RuleResultV1 = RuleResultV1(
        ruleVersion = RULE_VERSION_V1,
        outcome = outcome,
        baseMode = null,
        effectiveMode = null,
        allowedModes = emptyList(),
        reasonCodes = listOf(reason),
        invalidFields = emptyList(),
        presentationRoute = route,
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

    private fun <T> DraftField<T>.errorOrNull(field: RuleInputField): FieldError? = when (this) {
        DraftField.Missing -> FieldError(field, FieldErrorKind.MISSING)
        DraftField.Invalid -> FieldError(field, FieldErrorKind.INVALID)
        is DraftField.Valid -> null
    }

    private fun <T> DraftField<T>.requiredValue(): T = when (this) {
        is DraftField.Valid -> value
        DraftField.Missing,
        DraftField.Invalid,
        -> error("Required field must be valid after canonical validation")
    }

    private enum class FieldErrorKind {
        MISSING,
        INVALID,
    }

    private data class FieldError(
        val field: RuleInputField,
        val kind: FieldErrorKind,
    )
}
