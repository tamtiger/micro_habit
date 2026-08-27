package vn.nhip2phut.domain.wire.v1

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReminderOccurrenceIdCodecV1Test {
    @Test
    fun fixedIdUsesExactAsciiPreimageAndSha256UuidV8Projection() {
        val scheduleId = UuidWireV1.parse("123e4567-e89b-12d3-a456-426614174000")
        val localDate = DateWireV1.parse("2026-12-31")

        assertContentEquals(
            "fixed-v1|123e4567-e89b-12d3-a456-426614174000|1|2026-12-31|fixed|42"
                .toByteArray(StandardCharsets.US_ASCII),
            ReminderOccurrenceIdCodecV1.fixedPreimage(scheduleId, 1, localDate, 42),
        )
        assertEquals(
            "fa3777ba-8503-8951-b6ac-8a290e0c5bdf",
            ReminderOccurrenceIdCodecV1.fixed(scheduleId, 1, localDate, 42).value,
        )
    }

    @Test
    fun snoozeIdUsesExactAsciiPreimageAndSha256UuidV8Projection() {
        val parentId = UuidWireV1.parse("123e4567-e89b-12d3-a456-426614174000")

        assertContentEquals(
            "snooze-v1|123e4567-e89b-12d3-a456-426614174000|0"
                .toByteArray(StandardCharsets.US_ASCII),
            ReminderOccurrenceIdCodecV1.snoozePreimage(parentId, 0),
        )
        assertEquals(
            "ceeb14a2-4d55-8660-ac4f-2f470d137861",
            ReminderOccurrenceIdCodecV1.snooze(parentId, 0).value,
        )
    }

    @Test
    fun negativeDecimalInputsAreRejectedBeforeHashing() {
        val id = UuidWireV1.parse("123e4567-e89b-12d3-a456-426614174000")
        val date = DateWireV1.parse("2026-12-31")

        assertFailsWith<IllegalArgumentException> {
            ReminderOccurrenceIdCodecV1.fixed(id, -1, date, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ReminderOccurrenceIdCodecV1.fixed(id, 0, date, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            ReminderOccurrenceIdCodecV1.snooze(id, -1)
        }
    }
}
