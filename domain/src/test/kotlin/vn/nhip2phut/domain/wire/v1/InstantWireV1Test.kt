package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstantWireV1Test {
    @Test
    fun `accepts an exact canonical UTC millisecond instant`() {
        val value = "2026-08-27T23:59:59.123Z"

        assertEquals(value, InstantWireV1.parse(value).value)
    }

    @Test
    fun `rejects normalized timestamp aliases before parsing`() {
        for (
            value in listOf(
                "0000-01-01T00:00:00.000Z",
                "2026-08-27T24:00:00.000Z",
                "2026-08-27T23:59:60.000Z",
            )
        ) {
            assertFailsWith<WireContractException>(value) {
                InstantWireV1.parse(value)
            }
        }
    }
}
