package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalInt64V1Test {
    @Test
    fun acceptsBothSignedInt64BoundariesAsCanonicalDecimalTokens() {
        mapOf(
            "0" to 0L,
            "1" to 1L,
            "-1" to -1L,
            Long.MAX_VALUE.toString() to Long.MAX_VALUE,
            Long.MIN_VALUE.toString() to Long.MIN_VALUE,
        ).forEach { (token, expected) ->
            assertEquals(expected, StrictJsonV1.parse(token).asInt64("integer"))
        }
    }

    @Test
    fun rejectsNoncanonicalAndOutOfRangeIntegerTokens() {
        listOf(
            "-0",
            "+1",
            "00",
            "01",
            "-01",
            "1.0",
            "1e0",
            "1E0",
            "1e+0",
            "9223372036854775808",
            "-9223372036854775809",
            "\"1\"",
        ).forEach { token ->
            assertFailsWith<WireContractException>(token) {
                StrictJsonV1.parse(token).asInt64("integer")
            }
        }
    }
}
