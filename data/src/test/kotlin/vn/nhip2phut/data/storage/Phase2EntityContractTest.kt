package vn.nhip2phut.data.storage

import vn.nhip2phut.data.events.ProductEventEntity
import vn.nhip2phut.data.events.ProductEventEntityRef
import vn.nhip2phut.data.events.RequiredCompanionEventRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Phase2EntityContractTest {
    @Test
    fun `operational singleton tables only accept primary key one`() {
        assertFailsWith<IllegalArgumentException> {
            AppProfileEntity(
                singletonId = 2,
                cryptoVersion = 1,
                keyVersion = 1,
                payloadSchemaVersion = 1,
                encryptedPayload = payload(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ActiveWorkScheduleEntity(singletonId = 2, scheduleVersionId = UUID_A)
        }
        assertFailsWith<IllegalArgumentException> {
            FlowTimingStateEntity(
                singletonId = 2,
                cryptoVersion = 1,
                keyVersion = 1,
                payloadSchemaVersion = 1,
                encryptedPayload = payload(),
            )
        }
    }

    @Test
    fun `check in and decision pin rule version one and reject self parent`() {
        assertFailsWith<IllegalArgumentException> {
            checkIn(parentCheckInId = UUID_A)
        }
        assertFailsWith<IllegalArgumentException> {
            checkIn(parentCheckInId = null, ruleVersion = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            decision(ruleVersion = 2)
        }
    }

    @Test
    fun `product event pins key version one and exact 32 byte physical key`() {
        assertFailsWith<IllegalArgumentException> {
            productEvent(idempotencyKeyVersion = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            productEvent(idempotencyKey = ByteArray(31))
        }

        val valid = productEvent(idempotencyKey = ByteArray(32) { it.toByte() })
        assertEquals(1, valid.idempotencyKeyVersion)
        assertEquals(32, valid.idempotencyKey.size)
    }

    @Test
    fun `ordinary refs use closed table allowlist and canonical physical id length`() {
        assertFailsWith<IllegalArgumentException> {
            ProductEventEntityRef(UUID_A, "unknown", ByteArray(16))
        }
        assertFailsWith<IllegalArgumentException> {
            ProductEventEntityRef(UUID_A, "app_profile", ByteArray(16))
        }
        assertFailsWith<IllegalArgumentException> {
            ProductEventEntityRef(UUID_A, "check_in", ByteArray(8))
        }

        ProductEventEntityRef(UUID_A, "app_profile", ByteArray(8))
        ProductEventEntityRef(UUID_A, "check_in", ByteArray(16))
    }

    @Test
    fun `companion refs use closed source allowlist canonical id and expose no role column`() {
        assertFailsWith<IllegalArgumentException> {
            RequiredCompanionEventRef(UUID_A, "work_schedule_version", ByteArray(16))
        }
        assertFailsWith<IllegalArgumentException> {
            RequiredCompanionEventRef(UUID_A, "app_profile", ByteArray(16))
        }
        RequiredCompanionEventRef(UUID_A, "app_profile", ByteArray(8))
        RequiredCompanionEventRef(UUID_A, "decision", ByteArray(16))

        val physicalFields = RequiredCompanionEventRef::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()
        assertEquals(setOf("eventId", "sourceTable", "sourceId"), physicalFields)
    }

    private fun checkIn(
        parentCheckInId: String?,
        ruleVersion: Int = 1,
    ) = CheckInEntity(
        id = UUID_A,
        parentCheckInId = parentCheckInId,
        scheduleVersionId = UUID_B,
        localEpochDay = 20_000,
        deleteAfterEpochDay = 20_090,
        ruleVersion = ruleVersion,
        cryptoVersion = 1,
        keyVersion = 1,
        payloadSchemaVersion = 1,
        encryptedPayload = payload(),
    )

    private fun decision(ruleVersion: Int) = DecisionEntity(
        id = UUID_C,
        checkInId = UUID_A,
        scheduleVersionId = UUID_B,
        localEpochDay = 20_000,
        deleteAfterEpochDay = 20_090,
        ruleVersion = ruleVersion,
        cryptoVersion = 1,
        keyVersion = 1,
        payloadSchemaVersion = 1,
        encryptedPayload = payload(),
    )

    private fun productEvent(
        idempotencyKeyVersion: Int = 1,
        idempotencyKey: ByteArray = ByteArray(32),
    ) = ProductEventEntity(
        id = UUID_C,
        idempotencyKeyVersion = idempotencyKeyVersion,
        idempotencyKey = idempotencyKey,
        decisionId = null,
        sessionId = null,
        reminderOccurrenceId = null,
        scheduleVersionId = null,
        localEpochDay = 20_000,
        deleteAfterEpochDay = 20_090,
        cryptoVersion = 1,
        keyVersion = 1,
        payloadSchemaVersion = 1,
        encryptedPayload = payload(),
    )

    private fun payload(): ByteArray = byteArrayOf(1, 2, 3)

    private companion object {
        const val UUID_A = "00000000-0000-0000-0000-000000000001"
        const val UUID_B = "00000000-0000-0000-0000-000000000002"
        const val UUID_C = "00000000-0000-0000-0000-000000000003"
    }
}
