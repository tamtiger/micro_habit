package vn.nhip2phut.data.storage

import vn.nhip2phut.domain.time.ClockUpdateReason
import vn.nhip2phut.domain.time.DurableClockState
import vn.nhip2phut.domain.time.RawClockSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DurableClockStateReducerTest {
    @Test
    fun `first startup persists a real snapshot without inventing a clock change`() {
        val raw = raw(zone = "Asia/Bangkok", elapsed = 25, wall = 1_025)

        val state = DurableClockStateReducer.next(null, raw, ClockUpdateReason.STARTUP)

        assertEquals(0L, state.clockGeneration)
        assertEquals(25L, state.elapsedRealtimeMillis)
        assertEquals(1_000L, state.wallMinusElapsedMillis)
        assertEquals(ZoneId.of("Asia/Bangkok"), state.zoneId)
    }

    @Test
    fun `time and timezone broadcasts increment before checkpoint`() {
        val previous = state(generation = 4, zone = "UTC")

        val afterTimeSet = DurableClockStateReducer.next(
            previous,
            raw(zone = "UTC", elapsed = 2_000, wall = 12_000),
            ClockUpdateReason.TIME_SET,
        )
        val afterTimeZone = DurableClockStateReducer.next(
            afterTimeSet,
            raw(zone = "Asia/Bangkok", elapsed = 2_100, wall = 12_100),
            ClockUpdateReason.TIMEZONE_CHANGED,
        )

        assertEquals(5L, afterTimeSet.clockGeneration)
        assertEquals(6L, afterTimeZone.clockGeneration)
        assertEquals(ZoneId.of("Asia/Bangkok"), afterTimeZone.zoneId)
    }

    @Test
    fun `cold time signal still records one durable generation change`() {
        val state = DurableClockStateReducer.next(
            null,
            raw(zone = "UTC", elapsed = 100, wall = 1_100),
            ClockUpdateReason.TIME_SET,
        )

        assertEquals(1L, state.clockGeneration)
    }

    @Test
    fun `startup or resume only increments when persisted zone differs`() {
        val previous = state(generation = 8, zone = "UTC")

        val changed = DurableClockStateReducer.next(
            previous,
            raw(zone = "Asia/Bangkok", elapsed = 2_000, wall = 12_000),
            ClockUpdateReason.APP_RESUME,
        )
        val unchanged = DurableClockStateReducer.next(
            changed,
            raw(zone = "Asia/Bangkok", elapsed = 2_100, wall = 12_100),
            ClockUpdateReason.STARTUP,
        )

        assertEquals(9L, changed.clockGeneration)
        assertEquals(9L, unchanged.clockGeneration)
        assertEquals(2_100L, unchanged.elapsedRealtimeMillis)
    }

    @Test
    fun `boot checkpoint preserves generation because boot marker is independent evidence`() {
        val previous = state(generation = 2, zone = "UTC")

        val rebooted = DurableClockStateReducer.next(
            previous,
            raw(boot = 11, zone = "UTC", elapsed = 5, wall = 20_005),
            ClockUpdateReason.BOOT_COMPLETED,
        )

        assertEquals(2L, rebooted.clockGeneration)
        assertEquals(11L, rebooted.bootMarker)
        assertEquals(5L, rebooted.elapsedRealtimeMillis)
    }

    @Test
    fun `generation overflow fails closed`() {
        val previous = state(generation = Long.MAX_VALUE, zone = "UTC")

        assertFailsWith<ClockGenerationOverflowException> {
            DurableClockStateReducer.next(
                previous,
                raw(zone = "UTC", elapsed = 2_000, wall = 12_000),
                ClockUpdateReason.TIME_SET,
            )
        }
    }

    @Test
    fun `boot marker rollback fails closed`() {
        val previous = state(generation = 3, zone = "UTC")

        assertFailsWith<ClockStateRegressionException> {
            DurableClockStateReducer.next(
                previous,
                raw(boot = 9, zone = "UTC", elapsed = 2_000, wall = 12_000),
                ClockUpdateReason.STARTUP,
            )
        }
    }

    private fun state(generation: Long, zone: String) = DurableClockState(
        clockGeneration = generation,
        bootMarker = 10,
        zoneId = ZoneId.of(zone),
        elapsedRealtimeMillis = 1_000,
        wallMinusElapsedMillis = 10_000,
    )

    private fun raw(
        boot: Long = 10,
        zone: String,
        elapsed: Long,
        wall: Long,
    ) = RawClockSnapshot(
        instant = Instant.ofEpochMilli(wall),
        elapsedRealtimeMillis = elapsed,
        bootMarker = boot,
        zoneId = ZoneId.of(zone),
        utcOffsetMinutes = ZoneId.of(zone).rules.getOffset(Instant.ofEpochMilli(wall)).totalSeconds / 60,
    )
}
