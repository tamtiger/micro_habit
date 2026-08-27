package vn.nhip2phut.domain.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EventContractRegistryV1Test {
    @Test
    fun registryCoversExactlyFortyEightEvents() {
        assertEquals(48, EventNameV1.values().size)
        assertEquals(EventNameV1.values().size, EventContractRegistryV1.masks.size)
    }

    @Test
    fun eventWireTokensAreLowercaseAndRoundTrip() {
        EventNameV1.values().forEach { name ->
            assertEquals(name, EventNameV1.fromWire(name.wire))
            assertEquals(name.wire.lowercase(), name.wire)
            assertNotNull(EventContractRegistryV1.maskFor(name))
        }
    }
}

