package vn.nhip2phut.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidParameterException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AndroidKeyStoreKeyProviderV1 : AesKeyProviderV1 {
    private val keyStore: KeyStore
        get() = try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        } catch (failure: Exception) {
            throw CryptoKeyUnavailableException(failure)
        }

    @Synchronized
    override fun keyForEncryption(keyVersion: Int, allowCreation: Boolean): SecretKey {
        require(keyVersion > 0) { "Key version must be positive." }
        existingKey(keyVersion)?.let { return it }
        if (!allowCreation) throw CryptoKeyUnavailableException()
        return generateKey(keyVersion)
    }

    @Synchronized
    override fun keyForDecryption(keyVersion: Int): SecretKey {
        require(keyVersion > 0) { "Key version must be positive." }
        return existingKey(keyVersion) ?: throw CryptoKeyUnavailableException()
    }

    @Synchronized
    fun deleteKey(keyVersion: Int) {
        require(keyVersion > 0) { "Key version must be positive." }
        try {
            keyStore.deleteEntry(alias(keyVersion))
        } catch (failure: Exception) {
            throw CryptoKeyUnavailableException(failure)
        }
    }

    @Synchronized
    fun containsKey(keyVersion: Int): Boolean {
        require(keyVersion > 0) { "Key version must be positive." }
        return try {
            keyStore.containsAlias(alias(keyVersion))
        } catch (failure: Exception) {
            throw CryptoKeyUnavailableException(failure)
        }
    }

    private fun existingKey(keyVersion: Int): SecretKey? {
        return try {
            val keyStore = keyStore
            if (!keyStore.containsAlias(alias(keyVersion))) {
                null
            } else {
                keyStore.getKey(alias(keyVersion), null) as? SecretKey
                    ?: throw CryptoKeyUnavailableException()
            }
        } catch (failure: CryptoKeyUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw CryptoKeyUnavailableException(failure)
        }
    }

    private fun generateKey(keyVersion: Int): SecretKey {
        val alias = alias(keyVersion)
        return PreferredAesKeyGeneratorV1(
            generate = { keySize -> generate(alias, keySize) },
            recoverExisting = { existingKey(keyVersion) },
        ).generate()
    }

    private fun generate(alias: String, keySize: Int): SecretKey {
        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(keySize)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(specification)
            generateKey()
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALIAS_PREFIX = "nhip2phut_data_v"

        fun alias(keyVersion: Int): String {
            require(keyVersion > 0) { "Key version must be positive." }
            return "$ALIAS_PREFIX$keyVersion"
        }
    }
}

internal class PreferredAesKeyGeneratorV1(
    private val generate: (keySize: Int) -> SecretKey,
    private val recoverExisting: () -> SecretKey?,
) {
    fun generate(): SecretKey {
        return try {
            generate(AES_256_BITS)
        } catch (failure: Exception) {
            recoverExisting()?.let { return it }
            if (!failure.isExplicitUnsupportedKeyParameter()) {
                throw CryptoKeyUnavailableException(failure)
            }
            try {
                generate(AES_128_BITS)
            } catch (fallbackFailure: Exception) {
                recoverExisting()?.let { return it }
                throw CryptoKeyUnavailableException(fallbackFailure)
            }
        }
    }

    private fun Throwable.isExplicitUnsupportedKeyParameter(): Boolean {
        var failure: Throwable? = this
        repeat(MAX_CAUSE_DEPTH) {
            when (failure) {
                is InvalidAlgorithmParameterException, is InvalidParameterException -> return true
            }
            val next = failure?.cause
            if (next == null || next === failure) return false
            failure = next
        }
        return false
    }

    private companion object {
        const val AES_256_BITS = 256
        const val AES_128_BITS = 128
        const val MAX_CAUSE_DEPTH = 8
    }
}
