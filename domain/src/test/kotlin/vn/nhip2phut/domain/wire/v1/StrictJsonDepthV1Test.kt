package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertFailsWith

class StrictJsonDepthV1Test {
    @Test
    fun acceptsTheMaximumSupportedNestingDepth() {
        val source = "[".repeat(64) + "0" + "]".repeat(64)

        StrictJsonV1.parse(source)
    }

    @Test
    fun rejectsExcessiveNestingAsAWireContractFailure() {
        val source = "[".repeat(10_000) + "0" + "]".repeat(10_000)

        assertFailsWith<WireContractException> { StrictJsonV1.parse(source) }
    }
}
