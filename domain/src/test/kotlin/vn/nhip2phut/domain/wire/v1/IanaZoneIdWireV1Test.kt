package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IanaZoneIdWireV1Test {
    @Test
    fun `accepts the canonical UTC region fixture`() {
        val stamp = LocalStampWireV1(
            occurredAtUtc = InstantWireV1.parse("2026-08-27T10:00:00.000Z"),
            localDate = DateWireV1.parse("2026-08-27"),
            zoneId = "UTC",
            utcOffsetMinutes = 0,
        )

        assertEquals("UTC", stamp.zoneId)
    }

    @Test
    fun `rejects fixed offset and prefixed zone aliases`() {
        listOf(
            Triple("Z", "2026-08-27", 0L),
            Triple("+07:00", "2026-08-27", 420L),
            Triple("GMT+07:00", "2026-08-27", 420L),
        ).forEach { (zoneId, localDate, offsetMinutes) ->
            assertFailsWith<WireContractException>(zoneId) {
                LocalStampWireV1(
                    occurredAtUtc = InstantWireV1.parse("2026-08-27T10:00:00.000Z"),
                    localDate = DateWireV1.parse(localDate),
                    zoneId = zoneId,
                    utcOffsetMinutes = offsetMinutes,
                )
            }
        }
    }
}
