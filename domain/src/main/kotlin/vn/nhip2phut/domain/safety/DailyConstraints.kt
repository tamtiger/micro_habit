package vn.nhip2phut.domain.safety

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.model.LocalStamp

enum class SafetyHoldKind {
    RED_FLAG,
    ACUTE_ILLNESS,
    NEW_OR_WORSENING_PAIN_OR_INJURY,
    MEDICALLY_RESTRICTED,
    POST_SESSION_NEW_OR_WORSE_PAIN,
}

enum class ConstraintSourceType {
    CHECK_IN,
    SESSION,
}

class ConstraintTimeOverflowException(cause: ArithmeticException) :
    IllegalArgumentException("Daily constraint clock evidence overflowed.", cause)

data class ClockEvidence private constructor(
    val originBootMarker: Long,
    val createdElapsedRealtimeMillis: Long,
    val monotonicDeadlineMillis: Long,
    val remainingElapsedMillisAtLastCheckpoint: Long,
    val originalDurationMillis: Long,
) {
    internal fun checkpoint(remainingElapsedMillis: Long): ClockEvidence {
        require(remainingElapsedMillis in 0..remainingElapsedMillisAtLastCheckpoint) {
            "Checkpoint remaining time cannot increase on the same monotonic anchor."
        }
        return ClockEvidence(
            originBootMarker = originBootMarker,
            createdElapsedRealtimeMillis = createdElapsedRealtimeMillis,
            monotonicDeadlineMillis = monotonicDeadlineMillis,
            remainingElapsedMillisAtLastCheckpoint = remainingElapsedMillis,
            originalDurationMillis = originalDurationMillis,
        )
    }

    internal fun rebase(
        currentBootMarker: Long,
        currentElapsedRealtimeMillis: Long,
        conservativeRemainingMillis: Long,
    ): ClockEvidence {
        require(currentBootMarker >= 0) { "Boot marker must be nonnegative." }
        require(currentElapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
        require(conservativeRemainingMillis in 0..originalDurationMillis) {
            "Rebased remaining time must be inside the original duration."
        }
        val deadline = try {
            Math.addExact(currentElapsedRealtimeMillis, conservativeRemainingMillis)
        } catch (error: ArithmeticException) {
            throw ConstraintTimeOverflowException(error)
        }
        return ClockEvidence(
            originBootMarker = currentBootMarker,
            createdElapsedRealtimeMillis = currentElapsedRealtimeMillis,
            monotonicDeadlineMillis = deadline,
            remainingElapsedMillisAtLastCheckpoint = conservativeRemainingMillis,
            originalDurationMillis = originalDurationMillis,
        )
    }

    companion object {
        fun restore(
            originBootMarker: Long,
            createdElapsedRealtimeMillis: Long,
            monotonicDeadlineMillis: Long,
            remainingElapsedMillisAtLastCheckpoint: Long,
            originalDurationMillis: Long,
        ): ClockEvidence {
            require(originBootMarker >= 0) { "Boot marker must be nonnegative." }
            require(createdElapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
            require(originalDurationMillis >= 0) { "Original duration must be nonnegative." }
            require(remainingElapsedMillisAtLastCheckpoint in 0..originalDurationMillis) {
                "Checkpoint remaining time must be inside the original duration."
            }
            require(monotonicDeadlineMillis >= createdElapsedRealtimeMillis) {
                "Monotonic deadline cannot precede its current-boot anchor."
            }
            val anchoredDuration = monotonicDeadlineMillis - createdElapsedRealtimeMillis
            require(anchoredDuration <= originalDurationMillis) {
                "Current-boot anchored duration cannot exceed the immutable original duration."
            }
            require(remainingElapsedMillisAtLastCheckpoint <= anchoredDuration) {
                "Checkpoint remaining time cannot exceed the current-boot anchored duration."
            }
            return ClockEvidence(
                originBootMarker,
                createdElapsedRealtimeMillis,
                monotonicDeadlineMillis,
                remainingElapsedMillisAtLastCheckpoint,
                originalDurationMillis,
            )
        }
    }
}

data class SafetyHold(
    val kind: SafetyHoldKind,
    val sourceType: ConstraintSourceType,
    val sourceId: UUID,
    val ruleVersion: Int,
    val occurred: LocalStamp,
    val expiresAtUtc: Instant,
    val clockEvidence: ClockEvidence,
)

data class RestDaySuppression(
    val sourceDecisionId: UUID,
    val ruleVersion: Int,
    val occurred: LocalStamp,
    val expiresAtUtc: Instant,
    val clockEvidence: ClockEvidence,
)

object SafetyHoldFactory {
    fun redFlag(checkInId: UUID, snapshot: ClockSnapshot): SafetyHold =
        create(SafetyHoldKind.RED_FLAG, ConstraintSourceType.CHECK_IN, checkInId, snapshot)

    fun acute(acuteIssue: AcuteIssue, checkInId: UUID, snapshot: ClockSnapshot): SafetyHold {
        val kind = when (acuteIssue) {
            AcuteIssue.NONE -> throw IllegalArgumentException("Acute hold requires a non-NONE issue.")
            AcuteIssue.ACUTE_ILLNESS -> SafetyHoldKind.ACUTE_ILLNESS
            AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY ->
                SafetyHoldKind.NEW_OR_WORSENING_PAIN_OR_INJURY
            AcuteIssue.MEDICALLY_RESTRICTED -> SafetyHoldKind.MEDICALLY_RESTRICTED
        }
        return create(kind, ConstraintSourceType.CHECK_IN, checkInId, snapshot)
    }

    fun postSessionPain(sessionId: UUID, snapshot: ClockSnapshot): SafetyHold =
        create(
            SafetyHoldKind.POST_SESSION_NEW_OR_WORSE_PAIN,
            ConstraintSourceType.SESSION,
            sessionId,
            snapshot,
        )

    private fun create(
        kind: SafetyHoldKind,
        sourceType: ConstraintSourceType,
        sourceId: UUID,
        snapshot: ClockSnapshot,
    ): SafetyHold {
        val timing = constraintTiming(snapshot)
        return SafetyHold(
            kind = kind,
            sourceType = sourceType,
            sourceId = sourceId,
            ruleVersion = RULE_VERSION_V1,
            occurred = timing.occurred,
            expiresAtUtc = timing.expiresAtUtc,
            clockEvidence = timing.clockEvidence,
        )
    }
}

object RestDaySuppressionFactory {
    fun create(decisionId: UUID, snapshot: ClockSnapshot): RestDaySuppression {
        val timing = constraintTiming(snapshot)
        return RestDaySuppression(
            sourceDecisionId = decisionId,
            ruleVersion = RULE_VERSION_V1,
            occurred = timing.occurred,
            expiresAtUtc = timing.expiresAtUtc,
            clockEvidence = timing.clockEvidence,
        )
    }
}

enum class ConstraintStatus {
    ACTIVE,
    INACTIVE,
    ACTIVE_CONSERVATIVE,
}

enum class ConstraintFailureReason {
    INVALID_CLOCK_SNAPSHOT,
    ARITHMETIC_OVERFLOW,
}

sealed interface ConstraintResolution {
    val status: ConstraintStatus
    val clockEvidenceToPersist: ClockEvidence?

    data class Checkpointed(
        override val status: ConstraintStatus,
        override val clockEvidenceToPersist: ClockEvidence,
    ) : ConstraintResolution

    data class Reconciled(
        override val status: ConstraintStatus,
        override val clockEvidenceToPersist: ClockEvidence,
    ) : ConstraintResolution

    data class ConservativeFailure(
        val reason: ConstraintFailureReason,
    ) : ConstraintResolution {
        override val status: ConstraintStatus = ConstraintStatus.ACTIVE_CONSERVATIVE
        override val clockEvidenceToPersist: ClockEvidence? = null
    }
}

object DailyConstraintResolver {
    fun resolveForPersistence(
        constraint: SafetyHold,
        current: ClockSnapshot,
    ): ConstraintResolution = resolve(constraint.clockEvidence, constraint.expiresAtUtc, current)

    fun resolveForPersistence(
        constraint: RestDaySuppression,
        current: ClockSnapshot,
    ): ConstraintResolution = resolve(constraint.clockEvidence, constraint.expiresAtUtc, current)

    private fun resolve(
        evidence: ClockEvidence,
        expiresAtUtc: Instant,
        current: ClockSnapshot,
    ): ConstraintResolution {
        if (current.bootMarker < 0 || current.elapsedRealtimeMillis < 0 || current.clockGeneration < 0) {
            return ConstraintResolution.ConservativeFailure(ConstraintFailureReason.INVALID_CLOCK_SNAPSHOT)
        }

        val lastCheckpointElapsedRealtimeMillis =
            evidence.monotonicDeadlineMillis - evidence.remainingElapsedMillisAtLastCheckpoint
        if (current.bootMarker == evidence.originBootMarker &&
            current.elapsedRealtimeMillis >= lastCheckpointElapsedRealtimeMillis
        ) {
            val remaining = if (current.elapsedRealtimeMillis >= evidence.monotonicDeadlineMillis) {
                0L
            } else {
                evidence.monotonicDeadlineMillis - current.elapsedRealtimeMillis
            }
            val checkpoint = evidence.checkpoint(remaining)
            return ConstraintResolution.Checkpointed(
                status = if (remaining > 0) ConstraintStatus.ACTIVE else ConstraintStatus.INACTIVE,
                clockEvidenceToPersist = checkpoint,
            )
        }

        val wallRemaining = try {
            Math.subtractExact(expiresAtUtc.toEpochMilli(), current.instant.toEpochMilli())
                .coerceIn(0L, evidence.originalDurationMillis)
        } catch (_: ArithmeticException) {
            return ConstraintResolution.ConservativeFailure(ConstraintFailureReason.ARITHMETIC_OVERFLOW)
        }
        val conservativeRemaining = maxOf(
            evidence.remainingElapsedMillisAtLastCheckpoint,
            wallRemaining,
        )
        val rebased = try {
            evidence.rebase(
                currentBootMarker = current.bootMarker,
                currentElapsedRealtimeMillis = current.elapsedRealtimeMillis,
                conservativeRemainingMillis = conservativeRemaining,
            )
        } catch (_: ConstraintTimeOverflowException) {
            return ConstraintResolution.ConservativeFailure(ConstraintFailureReason.ARITHMETIC_OVERFLOW)
        }
        return ConstraintResolution.Reconciled(
            status = if (conservativeRemaining > 0) ConstraintStatus.ACTIVE else ConstraintStatus.INACTIVE,
            clockEvidenceToPersist = rebased,
        )
    }
}

private const val RULE_VERSION_V1 = 1

private data class ConstraintTiming(
    val occurred: LocalStamp,
    val expiresAtUtc: Instant,
    val clockEvidence: ClockEvidence,
)

private fun constraintTiming(snapshot: ClockSnapshot): ConstraintTiming {
    require(snapshot.elapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
    require(snapshot.bootMarker >= 0) { "Boot marker must be nonnegative." }
    require(snapshot.clockGeneration >= 0) { "Clock generation must be nonnegative." }

    try {
        val actualOffsetMinutes = snapshot.zoneId.rules.getOffset(snapshot.instant).totalSeconds / 60
        require(actualOffsetMinutes == snapshot.utcOffsetMinutes) {
            "Clock snapshot offset must agree with its zone and instant."
        }
        val localDate = LocalDate.ofInstant(snapshot.instant, snapshot.zoneId)
        val expiresAtUtc = localDate.plusDays(1).atStartOfDay(snapshot.zoneId).toInstant()
        val durationMillis = Duration.between(snapshot.instant, expiresAtUtc).toMillis()
        require(durationMillis >= 0) { "Constraint expiry must not precede its occurrence." }
        val deadline = try {
            Math.addExact(snapshot.elapsedRealtimeMillis, durationMillis)
        } catch (error: ArithmeticException) {
            throw ConstraintTimeOverflowException(error)
        }
        return ConstraintTiming(
            occurred = LocalStamp(snapshot.instant, localDate, snapshot.zoneId, snapshot.utcOffsetMinutes),
            expiresAtUtc = expiresAtUtc,
            clockEvidence = ClockEvidence.restore(
                originBootMarker = snapshot.bootMarker,
                createdElapsedRealtimeMillis = snapshot.elapsedRealtimeMillis,
                monotonicDeadlineMillis = deadline,
                remainingElapsedMillisAtLastCheckpoint = durationMillis,
                originalDurationMillis = durationMillis,
            ),
        )
    } catch (error: ArithmeticException) {
        throw ConstraintTimeOverflowException(error)
    } catch (error: DateTimeException) {
        throw IllegalArgumentException("Clock snapshot cannot form a daily constraint.", error)
    }
}
