package vn.nhip2phut.data.crypto

import vn.nhip2phut.domain.events.EventIdempotencyCodecV1
import vn.nhip2phut.domain.wire.v1.ClosedCodecV1
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventHmacSha256V1Test {
    @Test
    fun `rfc4231 golden uses exact HmacSHA256 and full 32 byte output`() {
        val key = SecretKeySpec(ByteArray(20) { 0x0b }, "HmacSHA256")
        val hmac = EventHmacSha256V1(FixedEventHmacKeyProvider(key))

        val actual = hmac.compute(
            logicalPreimage = "Hi There".encodeToByteArray(),
            allowKeyCreation = false,
        )

        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b" +
                "881dc200c9833da726e9376c2e32cff7",
            actual.toHex(),
        )
        assertEquals(EventHmacContractV1.KEY_BYTES, actual.size)
    }

    @Test
    fun `contract pins key version algorithm and exact keystore alias`() {
        assertEquals(1, EventHmacContractV1.KEY_VERSION)
        assertEquals("HmacSHA256", EventHmacContractV1.ALGORITHM)
        assertEquals("n2p_event_idem_hmac_v1", EventHmacContractV1.KEY_ALIAS)
    }

    @Test
    fun `verification is fail closed for tampered preimage key and wrong length`() {
        val first = EventHmacSha256V1(
            FixedEventHmacKeyProvider(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")),
        )
        val second = EventHmacSha256V1(
            FixedEventHmacKeyProvider(SecretKeySpec(ByteArray(32) { 9 }, "HmacSHA256")),
        )
        val preimage = "{\"schema\":\"event-idem-v1\"}".encodeToByteArray()
        val key = first.compute(preimage, allowKeyCreation = false)

        assertTrue(first.verify(preimage, key))
        assertFalse(first.verify(preimage + 0, key))
        assertFalse(second.verify(preimage, key))
        assertFalse(first.verify(preimage, key.copyOf(key.size - 1)))
    }

    @Test
    fun `typed event canonical preimage produces exact full physical key`() {
        val event = ClosedCodecV1.decodeEvent(FIRST_OPEN_EVENT)
        val hmac = EventHmacSha256V1(
            FixedEventHmacKeyProvider(
                SecretKeySpec(ByteArray(32) { 7 }, EventHmacContractV1.ALGORITHM),
            ),
        )

        val physicalKey = hmac.compute(
            logicalPreimage = EventIdempotencyCodecV1.logicalPreimage(event),
            allowKeyCreation = false,
        )

        assertEquals(
            "6fc5f0addb06ba556cdfe6296582e26ded1a744f7306be240bc990186ae46019",
            physicalKey.toHex(),
        )
        assertEquals(EventHmacContractV1.KEY_BYTES, physicalKey.size)
    }

    @Test
    fun `write forwards creation policy while verification never creates a missing dataset key`() {
        val provider = RecordingEventHmacKeyProvider(
            SecretKeySpec(ByteArray(32) { 11 }, EventHmacContractV1.ALGORITHM),
        )
        val hmac = EventHmacSha256V1(provider)
        val preimage = "dataset-scoped-event".encodeToByteArray()

        val physicalKey = hmac.compute(preimage, allowKeyCreation = true)
        hmac.compute(preimage, allowKeyCreation = false)
        assertTrue(hmac.verify(preimage, physicalKey))

        assertEquals(listOf(true, false, false), provider.creationPolicies)
    }

    private class FixedEventHmacKeyProvider(
        private val key: SecretKey,
    ) : EventHmacKeyProviderV1 {
        override fun keyForMac(allowCreation: Boolean): SecretKey = key
    }

    private class RecordingEventHmacKeyProvider(
        private val key: SecretKey,
    ) : EventHmacKeyProviderV1 {
        val creationPolicies = mutableListOf<Boolean>()

        override fun keyForMac(allowCreation: Boolean): SecretKey {
            creationPolicies += allowCreation
            return key
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        val FIRST_OPEN_EVENT = """
            {
              "event_id": "00000000-0000-4000-8000-000000000001",
              "event_schema_version": 1,
              "name": "app_first_opened",
              "occurred_at_utc": "2026-08-27T08:00:00.000Z",
              "local_date": "2026-08-27",
              "zone_id": "UTC",
              "utc_offset_minutes": 0,
              "installation_id": "00000000-0000-4000-8000-000000000002",
              "decision_id": null,
              "session_id": null,
              "reminder_occurrence_id": null,
              "schedule_version_id": null,
              "source": null,
              "properties": {
                "first_open_id": "00000000-0000-4000-8000-000000000003"
              }
            }
        """.trimIndent()
    }
}
