package vn.nhip2phut.data.crypto

import java.nio.ByteBuffer

data class EncryptedPayloadV1(
    val metadata: CryptoMetadataV1,
    val nonce: ByteArray,
    val ciphertextAndTag: ByteArray,
) {
    init {
        require(nonce.size == NONCE_BYTES) { "AES-GCM nonce must be 12 bytes." }
        require(ciphertextAndTag.size >= TAG_BYTES) { "AES-GCM payload must contain a 128-bit tag." }
    }

    fun encode(): ByteArray = ByteBuffer.allocate(HEADER_BYTES + ciphertextAndTag.size)
        .put(CanonicalAadV1.MAGIC_BYTES)
        .putInt(metadata.cryptoVersion)
        .putInt(metadata.keyVersion)
        .putInt(metadata.payloadSchemaVersion)
        .put(nonce)
        .put(ciphertextAndTag)
        .array()

    companion object {
        const val NONCE_BYTES: Int = 12
        const val TAG_BITS: Int = 128
        const val TAG_BYTES: Int = TAG_BITS / Byte.SIZE_BITS
        private val HEADER_BYTES = CanonicalAadV1.MAGIC_BYTES.size +
            (Int.SIZE_BYTES * 3) + NONCE_BYTES

        fun decode(bytes: ByteArray): EncryptedPayloadV1 {
            if (bytes.size < HEADER_BYTES + TAG_BYTES) throw MalformedEncryptedPayloadException()
            try {
                val input = ByteBuffer.wrap(bytes)
                val magic = ByteArray(CanonicalAadV1.MAGIC_BYTES.size).also(input::get)
                if (!magic.contentEquals(CanonicalAadV1.MAGIC_BYTES)) {
                    throw MalformedEncryptedPayloadException()
                }
                val metadata = CryptoMetadataV1(
                    cryptoVersion = input.int,
                    keyVersion = input.int,
                    payloadSchemaVersion = input.int,
                )
                val nonce = ByteArray(NONCE_BYTES).also(input::get)
                val ciphertextAndTag = ByteArray(input.remaining()).also(input::get)
                return EncryptedPayloadV1(metadata, nonce, ciphertextAndTag)
            } catch (failure: MalformedEncryptedPayloadException) {
                throw failure
            } catch (failure: RuntimeException) {
                throw MalformedEncryptedPayloadException(failure)
            }
        }
    }
}
