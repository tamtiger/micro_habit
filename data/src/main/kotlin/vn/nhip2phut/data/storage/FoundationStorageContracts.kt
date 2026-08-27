package vn.nhip2phut.data.storage

import java.util.UUID

data class EncryptedEnvelopeV1(
    val entityId: UUID,
    val entityType: String,
    val schemaVersion: Int,
    val aadVersion: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
) {
    init {
        require(schemaVersion == 1) { "Only schema version 1 is valid for MVP foundation." }
        require(aadVersion == 1) { "Only AAD version 1 is valid for MVP foundation." }
        require(entityType.matches(Regex("^[A-Za-z][A-Za-z0-9_]*$"))) { "Entity type must be canonical." }
        require(nonce.isNotEmpty()) { "Nonce is required." }
        require(ciphertext.isNotEmpty()) { "Ciphertext is required." }
        require(tag.isNotEmpty()) { "Authentication tag is required." }
    }
}

interface EncryptedEntityStore {
    suspend fun put(envelope: EncryptedEnvelopeV1)
    suspend fun get(entityId: UUID): EncryptedEnvelopeV1?
    suspend fun deleteAll()
}

