package vn.nhip2phut.data.storage

import vn.nhip2phut.domain.time.ClockUpdateReason
import vn.nhip2phut.domain.time.DurableClockState
import vn.nhip2phut.domain.time.RawClockSnapshot

object DurableClockStateReducer {
    fun next(
        previous: DurableClockState?,
        raw: RawClockSnapshot,
        reason: ClockUpdateReason,
    ): DurableClockState {
        if (previous != null && raw.bootMarker < previous.bootMarker) {
            throw ClockStateRegressionException()
        }
        if (
            previous != null &&
            previous.bootMarker == raw.bootMarker &&
            raw.elapsedRealtimeMillis < previous.elapsedRealtimeMillis
        ) {
            throw ClockStateRegressionException()
        }

        val priorGeneration = previous?.clockGeneration ?: 0L
        val mustIncrement = when (reason) {
            ClockUpdateReason.TIME_SET,
            ClockUpdateReason.TIMEZONE_CHANGED -> true

            ClockUpdateReason.STARTUP,
            ClockUpdateReason.APP_RESUME -> previous != null && previous.zoneId != raw.zoneId

            ClockUpdateReason.BOOT_COMPLETED -> false
        }
        val generation = if (mustIncrement) {
            try {
                Math.incrementExact(priorGeneration)
            } catch (_: ArithmeticException) {
                throw ClockGenerationOverflowException()
            }
        } else {
            priorGeneration
        }
        return raw.toDurableState(generation)
    }
}

class DurableClockStateCoordinator(
    private val repository: EncryptedClockStateRepository,
) {
    suspend fun update(reason: ClockUpdateReason, raw: RawClockSnapshot): DurableClockState =
        repository.updateAtomically { previous ->
            DurableClockStateReducer.next(previous, raw, reason)
        }
}

class ClockGenerationOverflowException :
    IllegalStateException("Clock generation cannot be incremented.")

class ClockStateRegressionException :
    IllegalStateException("Clock state monotonic evidence regressed.")
