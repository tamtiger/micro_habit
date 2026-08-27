package vn.nhip2phut.domain.time

import vn.nhip2phut.domain.model.ClockPort
import vn.nhip2phut.domain.model.ClockSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FakeClock(
    initialInstant: Instant = Instant.EPOCH,
    initialElapsedRealtimeMillis: Long = 0L,
    initialBootMarker: Long = 0L,
    initialClockGeneration: Long = 0L,
    initialZoneId: ZoneId = TimeZoneFixtures.UTC,
) : ClockPort {
    private var instant: Instant = initialInstant
    private var elapsedRealtimeMillis: Long = initialElapsedRealtimeMillis
    private var bootMarker: Long = initialBootMarker
    private var clockGeneration: Long = initialClockGeneration
    private var zoneId: ZoneId = initialZoneId

    init {
        require(initialElapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
        require(initialBootMarker >= 0) { "Boot marker must be nonnegative." }
        require(initialClockGeneration >= 0) { "Clock generation must be nonnegative." }
    }

    override fun snapshot(): ClockSnapshot {
        val offset = zoneId.rules.getOffset(instant)
        return ClockSnapshot(
            instant = instant,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            bootMarker = bootMarker,
            clockGeneration = clockGeneration,
            zoneId = zoneId,
            utcOffsetMinutes = offset.totalSeconds / 60,
        )
    }

    fun advance(duration: Duration) {
        require(!duration.isNegative) { "Advance duration must be nonnegative." }
        val nextInstant = instant.plus(duration)
        val nextElapsedRealtimeMillis = Math.addExact(elapsedRealtimeMillis, duration.toMillis())
        instant = nextInstant
        elapsedRealtimeMillis = nextElapsedRealtimeMillis
    }

    fun advanceWall(duration: Duration) {
        require(!duration.isNegative) { "Advance duration must be nonnegative." }
        instant = instant.plus(duration)
    }

    fun advanceElapsed(duration: Duration) {
        require(!duration.isNegative) { "Advance duration must be nonnegative." }
        elapsedRealtimeMillis = Math.addExact(elapsedRealtimeMillis, duration.toMillis())
    }

    fun rollbackWall(duration: Duration) {
        require(!duration.isNegative) { "Rollback duration must be nonnegative." }
        instant = instant.minus(duration)
    }

    fun changeZone(newZoneId: ZoneId) {
        zoneId = newZoneId
    }

    fun incrementGeneration() {
        clockGeneration = Math.incrementExact(clockGeneration)
    }

    fun reboot(newBootMarker: Long = Math.incrementExact(bootMarker), elapsedRealtimeMillis: Long = 0L) {
        require(newBootMarker >= 0) { "Boot marker must be nonnegative." }
        require(elapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
        bootMarker = newBootMarker
        this.elapsedRealtimeMillis = elapsedRealtimeMillis
    }

    fun freeze(
        instant: Instant = this.instant,
        elapsedRealtimeMillis: Long = this.elapsedRealtimeMillis,
        bootMarker: Long = this.bootMarker,
        clockGeneration: Long = this.clockGeneration,
        zoneId: ZoneId = this.zoneId,
    ) {
        require(elapsedRealtimeMillis >= 0) { "Elapsed realtime must be nonnegative." }
        require(bootMarker >= 0) { "Boot marker must be nonnegative." }
        require(clockGeneration >= 0) { "Clock generation must be nonnegative." }
        this.instant = instant
        this.elapsedRealtimeMillis = elapsedRealtimeMillis
        this.bootMarker = bootMarker
        this.clockGeneration = clockGeneration
        this.zoneId = zoneId
    }
}
