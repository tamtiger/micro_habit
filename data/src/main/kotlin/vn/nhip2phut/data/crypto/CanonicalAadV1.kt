package vn.nhip2phut.data.crypto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.UUID

sealed interface RecordPrimaryKeyV1 {
    fun canonicalBytes(): ByteArray

    data class Uuid(val value: UUID) : RecordPrimaryKeyV1 {
        override fun canonicalBytes(): ByteArray =
            ByteBuffer.allocate(1 + UUID_BYTES)
                .put(UUID_TAG)
                .putLong(value.mostSignificantBits)
                .putLong(value.leastSignificantBits)
                .array()
    }

    data class Singleton(val value: Long) : RecordPrimaryKeyV1 {
        override fun canonicalBytes(): ByteArray =
            ByteBuffer.allocate(1 + Long.SIZE_BYTES)
                .put(SINGLETON_TAG)
                .putLong(value)
                .array()
    }

    companion object {
        private const val UUID_BYTES = 16
        private const val UUID_TAG: Byte = 0x01
        private const val SINGLETON_TAG: Byte = 0x02
    }
}

object CanonicalAadV1 {
    const val MAGIC_TEXT: String = "N2PENC01"
    val MAGIC_BYTES: ByteArray
        get() = MAGIC_TEXT.encodeToByteArray()

    private val IDENTIFIER = Regex("^[a-z][a-z0-9_]{0,62}$")

    fun encode(
        metadata: CryptoMetadataV1,
        tableName: String,
        columnName: String,
        primaryKey: RecordPrimaryKeyV1,
    ): ByteArray {
        require(IDENTIFIER.matches(tableName)) { "Table name is not canonical." }
        require(IDENTIFIER.matches(columnName)) { "Column name is not canonical." }

        val components = arrayOf(
            MAGIC_BYTES,
            int32(metadata.cryptoVersion),
            int32(metadata.keyVersion),
            int32(metadata.payloadSchemaVersion),
            tableName.encodeToByteArray(),
            columnName.encodeToByteArray(),
            primaryKey.canonicalBytes(),
        )
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                components.forEach { component ->
                    output.writeInt(component.size)
                    output.write(component)
                }
            }
            bytes.toByteArray()
        }
    }

    fun encode(metadata: CryptoMetadataV1, binding: AadBindingV1): ByteArray = encode(
        metadata = metadata,
        tableName = binding.tableName,
        columnName = binding.columnName,
        primaryKey = binding.primaryKey,
    )

    private fun int32(value: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
}
