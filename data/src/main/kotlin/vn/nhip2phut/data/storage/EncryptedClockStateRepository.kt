package vn.nhip2phut.data.storage

import androidx.room.withTransaction
import vn.nhip2phut.data.crypto.AadBindingV1
import vn.nhip2phut.data.crypto.AesGcmEnvelopeCryptoV1
import vn.nhip2phut.data.crypto.CryptoException
import vn.nhip2phut.data.crypto.CryptoMetadataV1
import vn.nhip2phut.data.crypto.EncryptedPayloadV1
import vn.nhip2phut.data.crypto.RecordPrimaryKeyV1
import vn.nhip2phut.domain.time.DurableClockState

class EncryptedClockStateRepository(
    private val dao: ClockStateDao,
    private val crypto: AesGcmEnvelopeCryptoV1,
    private val metadata: CryptoMetadataV1 = CryptoMetadataV1(
        cryptoVersion = CryptoMetadataV1.CRYPTO_VERSION,
        keyVersion = 1,
        payloadSchemaVersion = CryptoMetadataV1.PAYLOAD_SCHEMA_VERSION,
    ),
    private val database: Nhip2PhutDatabase,
) {
    suspend fun read(): DurableClockState? {
        val entity = readSingletonOrNull() ?: return null
        return entity.decodeState()
    }

    internal suspend fun replace(state: DurableClockState) =
        database.withTransaction { replaceAndVerify(state) }

    internal suspend fun updateAtomically(
        transform: (DurableClockState?) -> DurableClockState,
    ): DurableClockState {
        return database.withTransaction {
            val existing = readSingletonOrNull()
            val previous = existing?.decodeState()
            val next = transform(previous)
            validateMonotonicTransition(previous, next)
            write(next, existing != null)
            val stable = readSingletonOrNull()?.decodeState() ?: throw ClockStateStorageException()
            if (stable != next || stable.clockGeneration != next.clockGeneration) {
                throw ClockStateStorageException()
            }
            stable
        }
    }

    private suspend fun replaceAndVerify(state: DurableClockState) {
        val existing = readSingletonOrNull()
        val previous = existing?.decodeState()
        validateMonotonicTransition(previous, state)
        write(state, existing != null)
        val stable = readSingletonOrNull()?.decodeState() ?: throw ClockStateStorageException()
        if (stable != state || stable.clockGeneration != state.clockGeneration) {
            throw ClockStateStorageException()
        }
    }

    private suspend fun write(state: DurableClockState, hasExistingCiphertext: Boolean) {
        val plaintext = ClockStatePayloadCodecV1.encode(state)
        val encrypted = try {
            crypto.encrypt(
                plaintext = plaintext,
                metadata = metadata,
                binding = AAD_BINDING,
                allowKeyCreation = !hasExistingCiphertext,
            )
        } finally {
            plaintext.fill(0)
        }
        dao.replace(
            ClockStateEntity(
                singletonId = ClockStateEntity.SINGLETON_ID,
                cryptoVersion = encrypted.metadata.cryptoVersion,
                keyVersion = encrypted.metadata.keyVersion,
                payloadSchemaVersion = encrypted.metadata.payloadSchemaVersion,
                encryptedPayload = encrypted.encode(),
            ),
        )
    }

    suspend fun delete() {
        dao.delete()
    }

    private suspend fun readSingletonOrNull(): ClockStateEntity? {
        val rows = dao.readAll()
        if (rows.isEmpty()) return null
        if (rows.size != 1 || rows.single().singletonId != ClockStateEntity.SINGLETON_ID) {
            throw ClockStateStorageException()
        }
        return rows.single()
    }

    private fun ClockStateEntity.decodeState(): DurableClockState {
        val plaintext = crypto.decrypt(authenticatedEnvelope(), AAD_BINDING)
        return try {
            ClockStatePayloadCodecV1.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun validateMonotonicTransition(
        previous: DurableClockState?,
        next: DurableClockState,
    ) {
        if (previous == null) return
        if (next.clockGeneration < previous.clockGeneration) throw ClockStateRegressionException()
        if (next.bootMarker < previous.bootMarker) throw ClockStateRegressionException()
        if (
            next.bootMarker == previous.bootMarker &&
            next.elapsedRealtimeMillis < previous.elapsedRealtimeMillis
        ) {
            throw ClockStateRegressionException()
        }
    }

    private fun ClockStateEntity.authenticatedEnvelope(): EncryptedPayloadV1 {
        if (singletonId != ClockStateEntity.SINGLETON_ID) throw ClockStateStorageException()
        val envelope = EncryptedPayloadV1.decode(encryptedPayload)
        if (
            cryptoVersion != envelope.metadata.cryptoVersion ||
            keyVersion != envelope.metadata.keyVersion ||
            payloadSchemaVersion != envelope.metadata.payloadSchemaVersion ||
            envelope.metadata != metadata
        ) {
            throw ClockStateStorageException()
        }
        return envelope
    }

    companion object {
        private val AAD_BINDING = AadBindingV1(
            tableName = ClockStateEntity.TABLE_NAME,
            columnName = ClockStateEntity.ENCRYPTED_COLUMN,
            primaryKey = RecordPrimaryKeyV1.Singleton(ClockStateEntity.SINGLETON_ID),
        )
    }
}

class ClockStateStorageException(cause: Throwable? = null) :
    CryptoException("Clock state storage metadata is invalid.", cause)
