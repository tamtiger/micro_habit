package vn.nhip2phut.domain.time

import vn.nhip2phut.domain.model.ClockSnapshot
import java.lang.Math.subtractExact
import java.time.Instant
import java.time.DateTimeException
import java.time.ZoneId

enum class ClockUpdateReason {
    STARTUP,
    APP_RESUME,
    BOOT_COMPLETED,
    TIME_SET,
    TIMEZONE_CHANGED,
}

data class RawClockSnapshot(
    val instant: Instant,
    val elapsedRealtimeMillis: Long,
    val bootMarker: Long,
    val zoneId: ZoneId,
    val utcOffsetMinutes: Int,
) {
    init {
        require(elapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
        require(bootMarker >= 0) { "Boot marker must be nonnegative." }
        val expectedOffset = zoneId.rules.getOffset(instant).totalSeconds / 60
        require(utcOffsetMinutes == expectedOffset) { "UTC offset must match the snapshot zone." }
    }

    fun withGeneration(clockGeneration: Long): ClockSnapshot {
        require(clockGeneration >= 0) { "Clock generation must be nonnegative." }
        return ClockSnapshot(
            instant = instant,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            bootMarker = bootMarker,
            clockGeneration = clockGeneration,
            zoneId = zoneId,
            utcOffsetMinutes = utcOffsetMinutes,
        )
    }

    fun toDurableState(clockGeneration: Long): DurableClockState = DurableClockState(
        clockGeneration = clockGeneration,
        bootMarker = bootMarker,
        zoneId = zoneId,
        elapsedRealtimeMillis = elapsedRealtimeMillis,
        wallMinusElapsedMillis = try {
            subtractExact(instant.toEpochMilli(), elapsedRealtimeMillis)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Clock mapping overflow.")
        },
    )
}

data class DurableClockState(
    val clockGeneration: Long,
    val bootMarker: Long,
    val zoneId: ZoneId,
    val elapsedRealtimeMillis: Long,
    val wallMinusElapsedMillis: Long,
) {
    init {
        require(clockGeneration >= 0) { "Clock generation must be nonnegative." }
        require(bootMarker >= 0) { "Boot marker must be nonnegative." }
        require(elapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
    }

    companion object {
        fun from(snapshot: ClockSnapshot): DurableClockState {
            val mapping = try {
                subtractExact(snapshot.instant.toEpochMilli(), snapshot.elapsedRealtimeMillis)
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("Clock mapping overflow.")
            }
            return DurableClockState(
                clockGeneration = snapshot.clockGeneration,
                bootMarker = snapshot.bootMarker,
                zoneId = snapshot.zoneId,
                elapsedRealtimeMillis = snapshot.elapsedRealtimeMillis,
                wallMinusElapsedMillis = mapping,
            )
        }
    }
}

enum class ClockDiscontinuity {
    NONE,
    REBOOT,
    ELAPSED_REALTIME_ROLLBACK,
    TIMEZONE_OR_GENERATION_CHANGE,
    WALL_MAPPING_DRIFT,
    CLOCK_UNKNOWN,
}

object ClockContinuity {
    const val MAX_WALL_MAPPING_DRIFT_MILLIS: Long = 2_000L

    fun detect(previous: DurableClockState, current: ClockSnapshot): ClockDiscontinuity {
        if (current.bootMarker != previous.bootMarker) return ClockDiscontinuity.REBOOT
        if (current.elapsedRealtimeMillis < previous.elapsedRealtimeMillis) {
            return ClockDiscontinuity.ELAPSED_REALTIME_ROLLBACK
        }
        if (current.clockGeneration != previous.clockGeneration || current.zoneId != previous.zoneId) {
            return ClockDiscontinuity.TIMEZONE_OR_GENERATION_CHANGE
        }

        val currentMapping = try {
            subtractExact(current.instant.toEpochMilli(), current.elapsedRealtimeMillis)
        } catch (_: ArithmeticException) {
            return ClockDiscontinuity.CLOCK_UNKNOWN
        }
        val drift = absoluteDifferenceOrNull(currentMapping, previous.wallMinusElapsedMillis)
            ?: return ClockDiscontinuity.CLOCK_UNKNOWN
        return if (drift > MAX_WALL_MAPPING_DRIFT_MILLIS) {
            ClockDiscontinuity.WALL_MAPPING_DRIFT
        } else {
            ClockDiscontinuity.NONE
        }
    }

    private fun absoluteDifferenceOrNull(left: Long, right: Long): Long? {
        if (left >= right) {
            return try {
                subtractExact(left, right)
            } catch (_: ArithmeticException) {
                null
            }
        }
        return try {
            subtractExact(right, left)
        } catch (_: ArithmeticException) {
            null
        }
    }
}

object TimeZoneFixtures {
    val UTC: ZoneId = ZoneId.of("UTC")
    val BANGKOK: ZoneId = ZoneId.of("Asia/Bangkok")
    val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    fun requireZone(zoneId: String): ZoneId = try {
        ZoneId.of(zoneId)
    } catch (_: DateTimeException) {
        throw IllegalArgumentException("Invalid zone ID.")
    }
}
