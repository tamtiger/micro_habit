package vn.nhip2phut.domain.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeClockTest {
    @Test
    fun `advance leaves both clocks unchanged when elapsed time overflows`() {
        val clock = FakeClock(
            initialInstant = Instant.parse("2026-08-27T00:00:00Z"),
            initialElapsedRealtimeMillis = Long.MAX_VALUE,
        )
        val before = clock.snapshot()

        assertFailsWith<ArithmeticException> {
            clock.advance(Duration.ofMillis(1))
        }

        assertEquals(before, clock.snapshot())
    }

    @Test
    fun `fake clock independently models elapsed wall zone generation and reboot`() {
        val clock = FakeClock(
            initialInstant = Instant.parse("2026-08-27T00:00:00Z"),
            initialElapsedRealtimeMillis = 100,
            initialBootMarker = 3,
            initialClockGeneration = 4,
            initialZoneId = ZoneId.of("UTC"),
        )

        clock.advance(Duration.ofSeconds(5))
        clock.rollbackWall(Duration.ofSeconds(2))
        clock.changeZone(ZoneId.of("Asia/Bangkok"))
        clock.incrementGeneration()
        clock.reboot(newBootMarker = 4, elapsedRealtimeMillis = 9)

        val snapshot = clock.snapshot()
        assertEquals(Instant.parse("2026-08-27T00:00:03Z"), snapshot.instant)
        assertEquals(9, snapshot.elapsedRealtimeMillis)
        assertEquals(4, snapshot.bootMarker)
        assertEquals(5, snapshot.clockGeneration)
        assertEquals(ZoneId.of("Asia/Bangkok"), snapshot.zoneId)
        assertEquals(420, snapshot.utcOffsetMinutes)
    }

    @Test
    fun `continuity detector distinguishes reboot zone and mapping drift`() {
        val previous = DurableClockState(
            clockGeneration = 2,
            bootMarker = 10,
            zoneId = ZoneId.of("UTC"),
            elapsedRealtimeMillis = 1_000,
            wallMinusElapsedMillis = 10_000,
        )

        assertEquals(
            ClockDiscontinuity.REBOOT,
            ClockContinuity.detect(previous, snapshot(boot = 11, generation = 2, zone = "UTC", elapsed = 2_000, wall = 12_000)),
        )
        assertEquals(
            ClockDiscontinuity.TIMEZONE_OR_GENERATION_CHANGE,
            ClockContinuity.detect(previous, snapshot(boot = 10, generation = 3, zone = "Asia/Bangkok", elapsed = 2_000, wall = 12_000)),
        )
        assertEquals(
            ClockDiscontinuity.WALL_MAPPING_DRIFT,
            ClockContinuity.detect(previous, snapshot(boot = 10, generation = 2, zone = "UTC", elapsed = 2_000, wall = 14_001)),
        )
    }

    private fun snapshot(
        boot: Long,
        generation: Long,
        zone: String,
        elapsed: Long,
        wall: Long,
    ) = vn.nhip2phut.domain.model.ClockSnapshot(
        instant = Instant.ofEpochMilli(wall),
        elapsedRealtimeMillis = elapsed,
        bootMarker = boot,
        clockGeneration = generation,
        zoneId = ZoneId.of(zone),
        utcOffsetMinutes = 0,
    )
}
