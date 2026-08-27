package vn.nhip2phut.data.crypto

import javax.crypto.SecretKey

data class CryptoMetadataV1(
    val cryptoVersion: Int,
    val keyVersion: Int,
    val payloadSchemaVersion: Int,
) {
    init {
        require(cryptoVersion == CRYPTO_VERSION) { "Unsupported crypto version." }
        require(keyVersion > 0) { "Key version must be positive." }
        require(payloadSchemaVersion == PAYLOAD_SCHEMA_VERSION) { "Unsupported payload schema version." }
    }

    companion object {
        const val CRYPTO_VERSION: Int = 1
        const val PAYLOAD_SCHEMA_VERSION: Int = 1
    }
}

data class AadBindingV1(
    val tableName: String,
    val columnName: String,
    val primaryKey: RecordPrimaryKeyV1,
)

interface AesKeyProviderV1 {
    fun keyForEncryption(keyVersion: Int, allowCreation: Boolean): SecretKey
    fun keyForDecryption(keyVersion: Int): SecretKey
}

open class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CryptoKeyUnavailableException(cause: Throwable? = null) :
    CryptoException("Encryption key is unavailable.", cause)

class CryptoAuthenticationException(cause: Throwable? = null) :
    CryptoException("Encrypted payload authentication failed.", cause)

class CryptoOperationException(cause: Throwable? = null) :
    CryptoException("Cryptographic operation failed.", cause)

class MalformedEncryptedPayloadException(cause: Throwable? = null) :
    CryptoException("Encrypted payload is malformed.", cause)
