package vn.nhip2phut.data.crypto

import java.nio.ByteBuffer
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class AesGcmEnvelopeV1Test {
    private val aad = AadBindingV1(
        tableName = "clock_state",
        columnName = "encrypted_payload",
        primaryKey = RecordPrimaryKeyV1.Singleton(1),
    )

    @Test
    fun `round trip uses a fresh twelve-byte nonce`() {
        val keys = InMemoryKeyProvider()
        val crypto = AesGcmEnvelopeCryptoV1(keys)
        val plaintext = "clock-state".encodeToByteArray()

        val first = crypto.encrypt(plaintext, CryptoMetadataV1(1, 1, 1), aad, allowKeyCreation = true)
        val second = crypto.encrypt(plaintext, CryptoMetadataV1(1, 1, 1), aad, allowKeyCreation = false)

        assertContentEquals(plaintext, crypto.decrypt(first, aad))
        assertContentEquals(plaintext, crypto.decrypt(second, aad))
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertContentEquals(first.encode(), EncryptedPayloadV1.decode(first.encode()).encode())
    }

    @Test
    fun `envelope header has exact magic versions nonce ciphertext and tag order`() {
        val nonce = ByteArray(EncryptedPayloadV1.NONCE_BYTES) { it.toByte() }
        val ciphertextAndTag = ByteArray(EncryptedPayloadV1.TAG_BYTES + 3) { (it + 20).toByte() }
        val payload = EncryptedPayloadV1(
            metadata = CryptoMetadataV1(cryptoVersion = 1, keyVersion = 9, payloadSchemaVersion = 1),
            nonce = nonce,
            ciphertextAndTag = ciphertextAndTag,
        )

        val encoded = payload.encode()

        assertContentEquals(
            "N2PENC01".encodeToByteArray() +
                int32(1) + int32(9) + int32(1) + nonce + ciphertextAndTag,
            encoded,
        )
        val decoded = EncryptedPayloadV1.decode(encoded)
        assertEquals(payload.metadata, decoded.metadata)
        assertContentEquals(nonce, decoded.nonce)
        assertContentEquals(ciphertextAndTag, decoded.ciphertextAndTag)
    }

    @Test
    fun `malformed magic versions nonce and tag are rejected before decryption`() {
        val valid = EncryptedPayloadV1(
            metadata = CryptoMetadataV1(1, 1, 1),
            nonce = ByteArray(EncryptedPayloadV1.NONCE_BYTES),
            ciphertextAndTag = ByteArray(EncryptedPayloadV1.TAG_BYTES),
        ).encode()

        assertFailsWith<MalformedEncryptedPayloadException> {
            EncryptedPayloadV1.decode(valid.copyOf().also { it[0] = (it[0] xor 1) })
        }
        assertFailsWith<MalformedEncryptedPayloadException> {
            EncryptedPayloadV1.decode(valid.copyOf().also { putInt(it, MAGIC_SIZE, 2) })
        }
        assertFailsWith<MalformedEncryptedPayloadException> {
            EncryptedPayloadV1.decode(valid.copyOf().also { putInt(it, MAGIC_SIZE + Int.SIZE_BYTES, 0) })
        }
        assertFailsWith<MalformedEncryptedPayloadException> {
            EncryptedPayloadV1.decode(
                valid.copyOf().also { putInt(it, MAGIC_SIZE + (Int.SIZE_BYTES * 2), 2) },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EncryptedPayloadV1(
                metadata = CryptoMetadataV1(1, 1, 1),
                nonce = ByteArray(EncryptedPayloadV1.NONCE_BYTES - 1),
                ciphertextAndTag = ByteArray(EncryptedPayloadV1.TAG_BYTES),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EncryptedPayloadV1(
                metadata = CryptoMetadataV1(1, 1, 1),
                nonce = ByteArray(EncryptedPayloadV1.NONCE_BYTES),
                ciphertextAndTag = ByteArray(EncryptedPayloadV1.TAG_BYTES - 1),
            )
        }
        assertFailsWith<MalformedEncryptedPayloadException> {
            EncryptedPayloadV1.decode(valid.copyOf(valid.size - 1))
        }
    }

    @Test
    fun `ciphertext nonce and tag tampering each fail authentication`() {
        val crypto = AesGcmEnvelopeCryptoV1(InMemoryKeyProvider())
        val encrypted = crypto.encrypt(
            plaintext = "durable-state".encodeToByteArray(),
            metadata = CryptoMetadataV1(1, 1, 1),
            binding = aad,
            allowKeyCreation = true,
        )

        val tamperedCiphertext = encrypted.ciphertextAndTag.copyOf().also { it[0] = (it[0] xor 1) }
        assertFailsWith<CryptoAuthenticationException> {
            crypto.decrypt(encrypted.copy(ciphertextAndTag = tamperedCiphertext), aad)
        }
        val tamperedNonce = encrypted.nonce.copyOf().also { it[0] = (it[0] xor 1) }
        assertFailsWith<CryptoAuthenticationException> {
            crypto.decrypt(encrypted.copy(nonce = tamperedNonce), aad)
        }
        val tamperedTag = encrypted.ciphertextAndTag.copyOf().also {
            it[it.lastIndex] = (it[it.lastIndex] xor 1)
        }
        assertFailsWith<CryptoAuthenticationException> {
            crypto.decrypt(encrypted.copy(ciphertextAndTag = tamperedTag), aad)
        }
    }

    @Test
    fun `aad prevents relocation across table column record and primary key type`() {
        val crypto = AesGcmEnvelopeCryptoV1(InMemoryKeyProvider())
        val encrypted = crypto.encrypt(
            plaintext = "bound-record".encodeToByteArray(),
            metadata = CryptoMetadataV1(1, 1, 1),
            binding = aad,
            allowKeyCreation = true,
        )

        listOf(
            aad.copy(tableName = "clock_state_archive"),
            aad.copy(columnName = "other_payload"),
            aad.copy(primaryKey = RecordPrimaryKeyV1.Singleton(2)),
            aad.copy(primaryKey = RecordPrimaryKeyV1.Uuid(UUID(0L, 1L))),
        ).forEach { relocatedBinding ->
            assertFailsWith<CryptoAuthenticationException> {
                crypto.decrypt(encrypted, relocatedBinding)
            }
        }

        val uuidBinding = aad.copy(primaryKey = RecordPrimaryKeyV1.Uuid(UUID(0L, 1L)))
        val uuidEncrypted = crypto.encrypt(
            plaintext = "uuid-record".encodeToByteArray(),
            metadata = CryptoMetadataV1(1, 1, 1),
            binding = uuidBinding,
            allowKeyCreation = false,
        )
        assertFailsWith<CryptoAuthenticationException> {
            crypto.decrypt(uuidEncrypted, aad)
        }
    }

    @Test
    fun `changing authenticated key version header fails closed`() {
        val keys = InMemoryKeyProvider()
        val crypto = AesGcmEnvelopeCryptoV1(keys)
        val encrypted = crypto.encrypt(
            plaintext = "version-bound".encodeToByteArray(),
            metadata = CryptoMetadataV1(1, 1, 1),
            binding = aad,
            allowKeyCreation = true,
        )
        keys.create(2)
        val changedHeader = encrypted.encode().also {
            putInt(it, MAGIC_SIZE + Int.SIZE_BYTES, 2)
        }

        assertFailsWith<CryptoAuthenticationException> {
            crypto.decrypt(EncryptedPayloadV1.decode(changedHeader), aad)
        }
    }

    @Test
    fun `existing ciphertext never causes a missing key to be regenerated`() {
        val keys = InMemoryKeyProvider()
        val crypto = AesGcmEnvelopeCryptoV1(keys)
        val encrypted = crypto.encrypt(
            plaintext = byteArrayOf(1, 2, 3),
            metadata = CryptoMetadataV1(1, 1, 1),
            binding = aad,
            allowKeyCreation = true,
        )
        keys.remove(1)

        assertFailsWith<CryptoKeyUnavailableException> { crypto.decrypt(encrypted, aad) }
        assertFailsWith<CryptoKeyUnavailableException> {
            crypto.encrypt(
                plaintext = byteArrayOf(4),
                metadata = CryptoMetadataV1(1, 1, 1),
                binding = aad,
                allowKeyCreation = false,
            )
        }
        assertFalse(keys.contains(1))
    }

    private class InMemoryKeyProvider : AesKeyProviderV1 {
        private val keys = mutableMapOf<Int, SecretKey>()

        override fun keyForEncryption(keyVersion: Int, allowCreation: Boolean): SecretKey {
            keys[keyVersion]?.let { return it }
            if (!allowCreation) throw CryptoKeyUnavailableException()
            return create(keyVersion)
        }

        override fun keyForDecryption(keyVersion: Int): SecretKey =
            keys[keyVersion] ?: throw CryptoKeyUnavailableException()

        fun remove(keyVersion: Int) {
            keys.remove(keyVersion)
        }

        fun contains(keyVersion: Int): Boolean = keys.containsKey(keyVersion)

        fun create(keyVersion: Int): SecretKey =
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
                keys[keyVersion] = it
            }
    }

    private infix fun Byte.xor(other: Int): Byte = (toInt() xor other).toByte()

    private fun int32(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes).putInt(offset, value)
    }

    private companion object {
        val MAGIC_SIZE: Int = CanonicalAadV1.MAGIC_BYTES.size
    }
}
