package vn.nhip2phut.platform.time

import vn.nhip2phut.domain.time.RawClockSnapshot
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidClockTest {
    @Test
    fun `snapshot uses verified boot count instead of a monotonic instant`() {
        val clock = AndroidClock(
            wallTimeMillis = { 1_000_000L },
            elapsedRealtimeMillis = { 25_000L },
            bootMarker = { 17L },
            clockGeneration = { 4L },
            zoneId = { ZoneId.of("Asia/Bangkok") },
        )

        val snapshot = clock.snapshot()

        assertEquals(17L, snapshot.bootMarker)
        assertEquals(25_000L, snapshot.elapsedRealtimeMillis)
        assertEquals(4L, snapshot.clockGeneration)
        assertEquals(420, snapshot.utcOffsetMinutes)
    }

    @Test
    fun `unverified boot count fails closed`() {
        val clock = AndroidClock(
            wallTimeMillis = { 1L },
            elapsedRealtimeMillis = { 1L },
            bootMarker = { null },
            clockGeneration = { 0L },
            zoneId = { ZoneId.of("UTC") },
        )

        assertFailsWith<ClockSnapshotUnavailableException> { clock.snapshot() }
    }

    @Test
    fun `unloaded durable generation fails closed instead of defaulting to zero`() {
        val raw = RawClockSource {
            RawClockSnapshot(
                instant = java.time.Instant.ofEpochMilli(1),
                elapsedRealtimeMillis = 1,
                bootMarker = 1,
                zoneId = ZoneId.of("UTC"),
                utcOffsetMinutes = 0,
            )
        }
        val clock = AndroidClock(
            rawClockSource = raw,
            generationSource = ClockGenerationSource { throw ClockGenerationUnavailableException() },
        )

        assertFailsWith<ClockGenerationUnavailableException> { clock.snapshot() }
    }

    @Test
    fun `generation changing across raw sample fails closed`() {
        var generation = 0L
        val clock = AndroidClock(
            rawClockSource = RawClockSource {
                RawClockSnapshot(
                    instant = java.time.Instant.ofEpochMilli(1),
                    elapsedRealtimeMillis = 1,
                    bootMarker = 1,
                    zoneId = ZoneId.of("UTC"),
                    utcOffsetMinutes = 0,
                )
            },
            generationSource = ClockGenerationSource { ++generation },
        )

        assertFailsWith<ClockGenerationUnavailableException> { clock.snapshot() }
    }
}
