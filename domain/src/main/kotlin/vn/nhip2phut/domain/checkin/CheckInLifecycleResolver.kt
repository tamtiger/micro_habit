package vn.nhip2phut.domain.checkin

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.model.LocalStamp
import vn.nhip2phut.domain.schedule.WorkScheduleVersion

enum class ReconfirmationReasonV1 {
    SCHEDULE_CHANGED,
    TTL,
    LOCAL_DATE_CHANGED,
    TIMEZONE_OR_TIME_CHANGE,
    CLOCK_UNKNOWN,
}

sealed interface CheckInLifecycleResult {
    data object Fresh : CheckInLifecycleResult
    data object Expired : CheckInLifecycleResult
    data class ReconfirmRequired(val reason: ReconfirmationReasonV1) : CheckInLifecycleResult
}

object CheckInLifecycleResolver {
    fun resolve(
        sourceScheduleVersionId: UUID,
        activeSchedule: WorkScheduleVersion,
        confirmedAt: LocalStamp,
        evidence: DecisionFreshnessEvidence,
        current: ClockSnapshot,
    ): CheckInLifecycleResult {
        if (activeSchedule.id != sourceScheduleVersionId) {
            return CheckInLifecycleResult.ReconfirmRequired(ReconfirmationReasonV1.SCHEDULE_CHANGED)
        }

        val currentLocalDate: LocalDate
        val currentLocalTime: LocalTime
        try {
            val currentZoned = current.instant.atZone(current.zoneId)
            currentLocalDate = currentZoned.toLocalDate()
            currentLocalTime = currentZoned.toLocalTime()
        } catch (_: DateTimeException) {
            return CheckInLifecycleResult.ReconfirmRequired(ReconfirmationReasonV1.CLOCK_UNKNOWN)
        }

        if (currentLocalTime < activeSchedule.workStart.toLocalTime() ||
            currentLocalTime >= activeSchedule.workEnd.toLocalTime()
        ) {
            return CheckInLifecycleResult.Expired
        }
        if (currentLocalDate != confirmedAt.localDate) {
            return CheckInLifecycleResult.ReconfirmRequired(ReconfirmationReasonV1.LOCAL_DATE_CHANGED)
        }
        return CheckInFreshnessResolver.resolve(evidence, current)
    }
}

object CheckInFreshnessResolver {
    const val MAX_CLOCK_MAPPING_DRIFT_MILLIS: Long = 2_000L

    fun resolve(
        evidence: DecisionFreshnessEvidence,
        current: ClockSnapshot,
    ): CheckInLifecycleResult {
        if (current.bootMarker < 0 || current.elapsedRealtimeMillis < 0 || current.clockGeneration < 0) {
            return unknownClock()
        }
        if (current.bootMarker != evidence.confirmedBootMarker ||
            current.elapsedRealtimeMillis < evidence.confirmedElapsedRealtimeMillis
        ) {
            return unknownClock()
        }
        if (current.clockGeneration != evidence.confirmedClockGeneration ||
            current.zoneId != evidence.confirmedZoneId
        ) {
            return timeChanged()
        }

        val currentMapping = try {
            Math.subtractExact(current.instant.toEpochMilli(), current.elapsedRealtimeMillis)
        } catch (_: ArithmeticException) {
            return unknownClock()
        }
        val mappingDifference = absoluteDifferenceOrNull(
            currentMapping,
            evidence.confirmedWallMinusElapsedMillis,
        ) ?: return unknownClock()
        if (mappingDifference > MAX_CLOCK_MAPPING_DRIFT_MILLIS) {
            return timeChanged()
        }
        return if (current.elapsedRealtimeMillis < evidence.ttlMonotonicDeadlineMillis) {
            CheckInLifecycleResult.Fresh
        } else {
            CheckInLifecycleResult.ReconfirmRequired(ReconfirmationReasonV1.TTL)
        }
    }

    private fun absoluteDifferenceOrNull(first: Long, second: Long): Long? = try {
        if (first >= second) Math.subtractExact(first, second) else Math.subtractExact(second, first)
    } catch (_: ArithmeticException) {
        null
    }

    private fun unknownClock() =
        CheckInLifecycleResult.ReconfirmRequired(ReconfirmationReasonV1.CLOCK_UNKNOWN)

    private fun timeChanged() =
        CheckInLifecycleResult.ReconfirmRequired(ReconfirmationReasonV1.TIMEZONE_OR_TIME_CHANGE)
}
