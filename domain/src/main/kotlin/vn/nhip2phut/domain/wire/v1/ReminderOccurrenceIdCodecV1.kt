package vn.nhip2phut.domain.wire.v1

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Canonical deterministic IDs for fixed and snoozed reminder occurrences. */
object ReminderOccurrenceIdCodecV1 {
    fun fixed(
        scheduleVersionId: UuidWireV1,
        slotIndex: Long,
        localDate: DateWireV1,
        generation: Long,
    ): UuidWireV1 = projectSha256ToUuidV8(fixedPreimage(scheduleVersionId, slotIndex, localDate, generation))

    fun snooze(parentOccurrenceId: UuidWireV1, ordinal: Long): UuidWireV1 =
        projectSha256ToUuidV8(snoozePreimage(parentOccurrenceId, ordinal))

    internal fun fixedPreimage(
        scheduleVersionId: UuidWireV1,
        slotIndex: Long,
        localDate: DateWireV1,
        generation: Long,
    ): ByteArray {
        require(slotIndex >= 0) { "slotIndex must be non-negative" }
        require(generation >= 0) { "generation must be non-negative" }
        return "fixed-v1|${scheduleVersionId.value}|$slotIndex|${localDate.value}|fixed|$generation"
            .toByteArray(StandardCharsets.US_ASCII)
    }

    internal fun snoozePreimage(parentOccurrenceId: UuidWireV1, ordinal: Long): ByteArray {
        require(ordinal >= 0) { "ordinal must be non-negative" }
        return "snooze-v1|${parentOccurrenceId.value}|$ordinal"
            .toByteArray(StandardCharsets.US_ASCII)
    }

    private fun projectSha256ToUuidV8(preimage: ByteArray): UuidWireV1 {
        val bytes = MessageDigest.getInstance("SHA-256").digest(preimage).copyOfRange(0, UUID_BYTE_COUNT)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x80).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = buildString(UUID_BYTE_COUNT * 2) {
            bytes.forEach { byte -> append(HEX[(byte.toInt() ushr 4) and 0x0f]).append(HEX[byte.toInt() and 0x0f]) }
        }
        return UuidWireV1.parse(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}",
        )
    }

    private const val UUID_BYTE_COUNT = 16
    private const val HEX = "0123456789abcdef"
}
