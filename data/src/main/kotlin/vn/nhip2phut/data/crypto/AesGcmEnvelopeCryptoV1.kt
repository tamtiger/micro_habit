package vn.nhip2phut.data.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class AesGcmEnvelopeCryptoV1(
    private val keyProvider: AesKeyProviderV1,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(
        plaintext: ByteArray,
        metadata: CryptoMetadataV1,
        binding: AadBindingV1,
        allowKeyCreation: Boolean,
    ): EncryptedPayloadV1 {
        val key = keyProvider.keyForEncryption(metadata.keyVersion, allowKeyCreation)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // AndroidKeyStore rejects caller-provided IVs when randomized encryption is required.
            // Let the selected provider generate the nonce, then persist the exact IV with the envelope.
            cipher.init(Cipher.ENCRYPT_MODE, key, secureRandom)
            val nonce = cipher.iv?.copyOf()
                ?.takeIf { it.size == EncryptedPayloadV1.NONCE_BYTES }
                ?: throw GeneralSecurityException("AES-GCM provider returned an invalid nonce.")
            cipher.updateAAD(CanonicalAadV1.encode(metadata, binding))
            EncryptedPayloadV1(
                metadata = metadata,
                nonce = nonce,
                ciphertextAndTag = cipher.doFinal(plaintext),
            )
        } catch (failure: GeneralSecurityException) {
            throw CryptoOperationException(failure)
        }
    }

    fun decrypt(encrypted: EncryptedPayloadV1, binding: AadBindingV1): ByteArray {
        val key = keyProvider.keyForDecryption(encrypted.metadata.keyVersion)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(EncryptedPayloadV1.TAG_BITS, encrypted.nonce),
            )
            cipher.updateAAD(CanonicalAadV1.encode(encrypted.metadata, binding))
            cipher.doFinal(encrypted.ciphertextAndTag)
        } catch (failure: AEADBadTagException) {
            throw CryptoAuthenticationException(failure)
        } catch (failure: GeneralSecurityException) {
            throw CryptoAuthenticationException(failure)
        }
    }

    companion object {
        const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    }
}
