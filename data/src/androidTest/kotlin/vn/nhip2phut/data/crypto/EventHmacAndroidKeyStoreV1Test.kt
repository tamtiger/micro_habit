package vn.nhip2phut.data.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventHmacAndroidKeyStoreV1Test {
    private lateinit var provider: EventHmacAndroidKeyStoreProviderV1

    @Before
    fun setUp() {
        provider = EventHmacAndroidKeyStoreProviderV1()
        if (provider.containsKey()) provider.deleteKey()
    }

    @After
    fun tearDown() {
        if (provider.containsKey()) provider.deleteKey()
    }

    @Test
    fun exactAliasCreatesNonExportableHmacSha256KeyAndVerifiesFullOutput() {
        assertEquals("n2p_event_idem_hmac_v1", EventHmacContractV1.KEY_ALIAS)
        assertFalse(provider.containsKey())

        val hmac = EventHmacSha256V1(provider)
        val preimage =
            "{\"domain\":\"onboarding_completed\",\"parts\":[],\"schema\":\"event-idem-v1\"}"
                .encodeToByteArray()
        val physicalKey = hmac.compute(preimage, allowKeyCreation = true)

        assertTrue(provider.containsKey())
        assertNull(provider.keyForMac(allowCreation = false).encoded)
        assertEquals(EventHmacContractV1.KEY_BYTES, physicalKey.size)
        assertTrue(hmac.verify(preimage, physicalKey))
        assertFalse(hmac.verify(preimage + 0, physicalKey))
    }

    @Test
    fun existingDatasetReadCannotRegenerateMissingAlias() {
        assertThrows<EventHmacKeyUnavailableException> {
            provider.keyForMac(allowCreation = false)
        }
        assertFalse(provider.containsKey())
    }

    @Test
    fun verificationPathCannotCreateAMissingAlias() {
        val hmac = EventHmacSha256V1(provider)

        assertThrows<EventHmacKeyUnavailableException> {
            hmac.verify(
                logicalPreimage = "existing-dataset-event".encodeToByteArray(),
                expectedPhysicalKey = ByteArray(EventHmacContractV1.KEY_BYTES),
            )
        }
        assertFalse(provider.containsKey())
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}.")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
        }
    }
}
