package vn.nhip2phut.data.crypto

import java.security.InvalidAlgorithmParameterException
import java.security.InvalidParameterException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.ProviderException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PreferredAesKeyGeneratorV1Test {
    @Test
    fun `data key alias is exact and versioned`() {
        assertEquals("nhip2phut_data_v1", AndroidKeyStoreKeyProviderV1.alias(1))
        assertEquals("nhip2phut_data_v27", AndroidKeyStoreKeyProviderV1.alias(27))
        assertFailsWith<IllegalArgumentException> { AndroidKeyStoreKeyProviderV1.alias(0) }
    }

    @Test
    fun `arbitrary provider failure fails closed without trying 128 bit`() {
        listOf(
            ProviderException("keystore unavailable"),
            ProviderException("keystore rejected the operation", KeyStoreException("backend failure")),
            NoSuchAlgorithmException("AES provider missing"),
            SecurityException("keystore access denied"),
        ).forEach { providerFailure ->
            val attempts = mutableListOf<Int>()
            val generator = PreferredAesKeyGeneratorV1(
                generate = { keySize ->
                    attempts += keySize
                    throw providerFailure
                },
                recoverExisting = { null },
            )

            val failure = assertFailsWith<CryptoKeyUnavailableException> { generator.generate() }

            assertSame(providerFailure, failure.cause)
            assertEquals(listOf(256), attempts)
        }
    }

    @Test
    fun `explicit key parameter rejection permits one 128 bit fallback`() {
        listOf(
            InvalidAlgorithmParameterException("unsupported AES-256 parameters"),
            InvalidParameterException("unsupported AES-256 key size"),
            ProviderException(
                "provider wrapped an explicit parameter rejection",
                InvalidAlgorithmParameterException("unsupported AES-256 parameters"),
            ),
        ).forEach { unsupportedFailure ->
            val attempts = mutableListOf<Int>()
            val fallbackKey = aesKey(16)
            val generator = PreferredAesKeyGeneratorV1(
                generate = { keySize ->
                    attempts += keySize
                    if (keySize == 256) throw unsupportedFailure
                    fallbackKey
                },
                recoverExisting = { null },
            )

            assertSame(fallbackKey, generator.generate())
            assertEquals(listOf(256, 128), attempts)
        }
    }

    @Test
    fun `key created before provider failure is recovered without downgrade`() {
        val attempts = mutableListOf<Int>()
        val createdKey = aesKey(32)
        val generator = PreferredAesKeyGeneratorV1(
            generate = { keySize ->
                attempts += keySize
                throw ProviderException("late provider failure")
            },
            recoverExisting = { createdKey },
        )

        assertSame(createdKey, generator.generate())
        assertEquals(listOf(256), attempts)
    }

    @Test
    fun `failed 128 bit fallback is not retried or hidden`() {
        val attempts = mutableListOf<Int>()
        val fallbackFailure = ProviderException("keystore write failed")
        val generator = PreferredAesKeyGeneratorV1(
            generate = { keySize ->
                attempts += keySize
                if (keySize == 256) {
                    throw InvalidAlgorithmParameterException("unsupported AES-256 parameters")
                }
                throw fallbackFailure
            },
            recoverExisting = { null },
        )

        val failure = assertFailsWith<CryptoKeyUnavailableException> { generator.generate() }

        assertSame(fallbackFailure, failure.cause)
        assertEquals(listOf(256, 128), attempts)
    }

    private fun aesKey(bytes: Int): SecretKey = SecretKeySpec(ByteArray(bytes), "AES")
}
