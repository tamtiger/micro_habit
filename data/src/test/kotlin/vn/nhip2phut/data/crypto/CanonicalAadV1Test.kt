package vn.nhip2phut.data.crypto

import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class CanonicalAadV1Test {
    @Test
    fun `clock singleton aad uses exact ordered length-prefixed tuple`() {
        val metadata = CryptoMetadataV1(
            cryptoVersion = 1,
            keyVersion = 1,
            payloadSchemaVersion = 1,
        )

        val actual = CanonicalAadV1.encode(
            metadata = metadata,
            tableName = "clock_state",
            columnName = "encrypted_payload",
            primaryKey = RecordPrimaryKeyV1.Singleton(1L),
        )

        val expected = components(
            "N2PENC01".encodeToByteArray(),
            int32(1),
            int32(1),
            int32(1),
            "clock_state".encodeToByteArray(),
            "encrypted_payload".encodeToByteArray(),
            byteArrayOf(0x02) + int64(1L),
        )
        assertContentEquals(expected, actual)
    }

    @Test
    fun `uuid aad key is tagged raw RFC 4122 bytes`() {
        val id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

        val actual = RecordPrimaryKeyV1.Uuid(id).canonicalBytes()

        assertContentEquals(
            byteArrayOf(0x01) + byteArrayOf(
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(),
                0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
            ),
            actual,
        )
    }

    @Test
    fun `uuid record aad uses exact table column and tagged key tuple`() {
        val id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

        val actual = CanonicalAadV1.encode(
            metadata = CryptoMetadataV1(cryptoVersion = 1, keyVersion = 7, payloadSchemaVersion = 1),
            tableName = "sessions",
            columnName = "encrypted_payload",
            primaryKey = RecordPrimaryKeyV1.Uuid(id),
        )

        val expected = components(
            "N2PENC01".encodeToByteArray(),
            int32(1),
            int32(7),
            int32(1),
            "sessions".encodeToByteArray(),
            "encrypted_payload".encodeToByteArray(),
            byteArrayOf(0x01) + byteArrayOf(
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(),
                0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
            ),
        )
        assertContentEquals(expected, actual)
    }

    @Test
    fun `uuid and singleton keys have distinct type-tagged canonical bytes`() {
        val uuid = RecordPrimaryKeyV1.Uuid(UUID(0L, 1L)).canonicalBytes()
        val singleton = RecordPrimaryKeyV1.Singleton(1L).canonicalBytes()

        assertContentEquals(byteArrayOf(0x01) + ByteArray(15) + byteArrayOf(0x01), uuid)
        assertContentEquals(byteArrayOf(0x02) + int64(1L), singleton)
    }

    @Test
    fun `metadata and identifiers reject unsupported or ambiguous values`() {
        assertFailsWith<IllegalArgumentException> {
            CryptoMetadataV1(cryptoVersion = 2, keyVersion = 1, payloadSchemaVersion = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            CryptoMetadataV1(cryptoVersion = 1, keyVersion = 0, payloadSchemaVersion = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalAadV1.encode(
                metadata = CryptoMetadataV1(1, 1, 1),
                tableName = "clock/state",
                columnName = "encrypted_payload",
                primaryKey = RecordPrimaryKeyV1.Singleton(1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalAadV1.encode(
                metadata = CryptoMetadataV1(1, 1, 1),
                tableName = "clock_state",
                columnName = "EncryptedPayload",
                primaryKey = RecordPrimaryKeyV1.Singleton(1),
            )
        }
    }

    private fun components(vararg values: ByteArray): ByteArray =
        values.fold(ByteArray(0)) { result, value -> result + int32(value.size) + value }

    private fun int32(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

    private fun int64(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
}
