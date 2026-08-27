package vn.nhip2phut.data.storage

import vn.nhip2phut.domain.time.DurableClockState
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClockStatePayloadCodecV1Test {
    @Test
    fun `durable clock payload round trips without wall-time coercion`() {
        val state = DurableClockState(
            clockGeneration = 7,
            bootMarker = 23,
            zoneId = ZoneId.of("Asia/Bangkok"),
            elapsedRealtimeMillis = 90_000,
            wallMinusElapsedMillis = -123_456,
        )

        val encoded = ClockStatePayloadCodecV1.encode(state)

        assertEquals(state, ClockStatePayloadCodecV1.decode(encoded))
    }

    @Test
    fun `payload decoder rejects trailing bytes and invalid zone`() {
        val valid = ClockStatePayloadCodecV1.encode(
            DurableClockState(1, 1, ZoneId.of("UTC"), 1, 1),
        )
        assertFailsWith<ClockStatePayloadException> {
            ClockStatePayloadCodecV1.decode(valid + byteArrayOf(0))
        }

        val invalidZone = valid.copyOf().also {
            val zoneOffset = Long.SIZE_BYTES * 4 + Int.SIZE_BYTES
            it[zoneOffset] = 'X'.code.toByte()
        }
        assertFailsWith<ClockStatePayloadException> {
            ClockStatePayloadCodecV1.decode(invalidZone)
        }
    }
}
