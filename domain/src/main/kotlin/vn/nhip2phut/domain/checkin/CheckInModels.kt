package vn.nhip2phut.domain.checkin

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.LocalStamp
import vn.nhip2phut.domain.model.Stiffness

enum class CheckInAnswersKindV1 {
    RED_FLAG_STOP,
    ACUTE_STOP,
    FULL,
}

sealed interface PersistedCheckInAnswersV1 {
    val kind: CheckInAnswersKindV1
    val redFlag: Boolean
    val acuteIssue: AcuteIssue?
    val energy: Energy?
    val stiffness: Stiffness?
    val intent: Intent?

    data object RedFlagStop : PersistedCheckInAnswersV1 {
        override val kind = CheckInAnswersKindV1.RED_FLAG_STOP
        override val redFlag = true
        override val acuteIssue: AcuteIssue? = null
        override val energy: Energy? = null
        override val stiffness: Stiffness? = null
        override val intent: Intent? = null
    }

    data class AcuteStop(
        override val acuteIssue: AcuteIssue,
    ) : PersistedCheckInAnswersV1 {
        init {
            require(acuteIssue != AcuteIssue.NONE) { "Acute stop requires a non-NONE acute issue." }
        }

        override val kind = CheckInAnswersKindV1.ACUTE_STOP
        override val redFlag = false
        override val energy: Energy? = null
        override val stiffness: Stiffness? = null
        override val intent: Intent? = null
    }

    data class Full(
        override val energy: Energy,
        override val stiffness: Stiffness,
        override val intent: Intent,
    ) : PersistedCheckInAnswersV1 {
        override val kind = CheckInAnswersKindV1.FULL
        override val redFlag = false
        override val acuteIssue: AcuteIssue = AcuteIssue.NONE
    }
}

class FreshnessEvidenceOverflowException(cause: ArithmeticException) :
    IllegalArgumentException("Freshness clock evidence overflowed.", cause)

data class DecisionFreshnessEvidence private constructor(
    val confirmedBootMarker: Long,
    val confirmedElapsedRealtimeMillis: Long,
    val ttlMonotonicDeadlineMillis: Long,
    val confirmedClockGeneration: Long,
    val confirmedZoneId: ZoneId,
    val confirmedWallMinusElapsedMillis: Long,
) {
    companion object {
        val TTL_MILLIS: Long = Duration.ofHours(6).toMillis()

        fun create(snapshot: ClockSnapshot): DecisionFreshnessEvidence {
            validateSnapshotCounters(snapshot)
            return try {
                restore(
                    confirmedBootMarker = snapshot.bootMarker,
                    confirmedElapsedRealtimeMillis = snapshot.elapsedRealtimeMillis,
                    ttlMonotonicDeadlineMillis = Math.addExact(snapshot.elapsedRealtimeMillis, TTL_MILLIS),
                    confirmedClockGeneration = snapshot.clockGeneration,
                    confirmedZoneId = snapshot.zoneId,
                    confirmedWallMinusElapsedMillis = Math.subtractExact(
                        snapshot.instant.toEpochMilli(),
                        snapshot.elapsedRealtimeMillis,
                    ),
                )
            } catch (error: ArithmeticException) {
                throw FreshnessEvidenceOverflowException(error)
            }
        }

        fun restore(
            confirmedBootMarker: Long,
            confirmedElapsedRealtimeMillis: Long,
            ttlMonotonicDeadlineMillis: Long,
            confirmedClockGeneration: Long,
            confirmedZoneId: ZoneId,
            confirmedWallMinusElapsedMillis: Long,
        ): DecisionFreshnessEvidence {
            require(confirmedBootMarker >= 0) { "Boot marker must be nonnegative." }
            require(confirmedElapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
            require(confirmedClockGeneration >= 0) { "Clock generation must be nonnegative." }
            val expectedDeadline = try {
                Math.addExact(confirmedElapsedRealtimeMillis, TTL_MILLIS)
            } catch (error: ArithmeticException) {
                throw FreshnessEvidenceOverflowException(error)
            }
            require(ttlMonotonicDeadlineMillis == expectedDeadline) {
                "TTL deadline must be exactly six hours after confirmation."
            }
            return DecisionFreshnessEvidence(
                confirmedBootMarker,
                confirmedElapsedRealtimeMillis,
                ttlMonotonicDeadlineMillis,
                confirmedClockGeneration,
                confirmedZoneId,
                confirmedWallMinusElapsedMillis,
            )
        }
    }
}

data class CheckIn private constructor(
    val id: UUID,
    val parentCheckInId: UUID?,
    val scheduleVersionId: UUID,
    val ruleVersion: Int,
    val answers: PersistedCheckInAnswersV1,
    val confirmedAt: LocalStamp,
    val freshnessEvidence: DecisionFreshnessEvidence,
) {
    companion object {
        const val RULE_VERSION_V1: Int = 1

        fun create(
            id: UUID,
            parentCheckInId: UUID?,
            scheduleVersionId: UUID,
            answers: PersistedCheckInAnswersV1,
            snapshot: ClockSnapshot,
        ): CheckIn {
            require(id != parentCheckInId) { "A check-in cannot be its own parent." }
            validateSnapshotCounters(snapshot)
            val confirmedAt = try {
                val actualOffsetMinutes = snapshot.zoneId.rules.getOffset(snapshot.instant).totalSeconds / 60
                require(actualOffsetMinutes == snapshot.utcOffsetMinutes) {
                    "Clock snapshot offset must agree with its zone and instant."
                }
                LocalStamp(
                    instant = snapshot.instant,
                    localDate = LocalDate.ofInstant(snapshot.instant, snapshot.zoneId),
                    zoneId = snapshot.zoneId,
                    utcOffsetMinutes = snapshot.utcOffsetMinutes,
                )
            } catch (error: DateTimeException) {
                throw IllegalArgumentException("Clock snapshot cannot form a local confirmation stamp.", error)
            }
            return CheckIn(
                id = id,
                parentCheckInId = parentCheckInId,
                scheduleVersionId = scheduleVersionId,
                ruleVersion = RULE_VERSION_V1,
                answers = answers,
                confirmedAt = confirmedAt,
                freshnessEvidence = DecisionFreshnessEvidence.create(snapshot),
            )
        }
    }
}

private fun validateSnapshotCounters(snapshot: ClockSnapshot) {
    require(snapshot.elapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
    require(snapshot.bootMarker >= 0) { "Boot marker must be nonnegative." }
    require(snapshot.clockGeneration >= 0) { "Clock generation must be nonnegative." }
}
