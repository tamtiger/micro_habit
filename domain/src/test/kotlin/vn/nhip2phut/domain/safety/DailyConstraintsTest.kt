package vn.nhip2phut.domain.safety

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.time.FakeClock

class DailyConstraintsTest {
    @Test
    fun nextDayExpiryUsesOriginZoneRulesAcrossDstInsteadOfAddingTwentyFourHours() {
        val zone = ZoneId.of("America/New_York")
        val origin = snapshot("2026-03-08T05:00:00.000Z", elapsed = 1_000, zone = zone)
        val hold = SafetyHoldFactory.redFlag(uuid(1), origin)

        assertEquals("2026-03-08", hold.occurred.localDate.toString())
        assertEquals(Instant.parse("2026-03-09T04:00:00.000Z"), hold.expiresAtUtc)
        assertEquals(Duration.ofHours(23).toMillis(), hold.clockEvidence.originalDurationMillis)
        assertEquals(1_000 + Duration.ofHours(23).toMillis(), hold.clockEvidence.monotonicDeadlineMillis)
        assertEquals(hold.clockEvidence.originalDurationMillis, hold.clockEvidence.remainingElapsedMillisAtLastCheckpoint)
    }

    @Test
    fun factoriesCreateReasonedCheckInAndSessionHolds() {
        val clock = snapshot("2026-08-27T03:00:00.000Z", 5_000)
        val red = SafetyHoldFactory.redFlag(uuid(1), clock)
        val illness = SafetyHoldFactory.acute(AcuteIssue.ACUTE_ILLNESS, uuid(2), clock)
        val injury = SafetyHoldFactory.acute(AcuteIssue.NEW_OR_WORSENING_PAIN_OR_INJURY, uuid(3), clock)
        val restricted = SafetyHoldFactory.acute(AcuteIssue.MEDICALLY_RESTRICTED, uuid(4), clock)
        val pain = SafetyHoldFactory.postSessionPain(uuid(5), clock)

        assertEquals(SafetyHoldKind.RED_FLAG, red.kind)
        assertEquals(SafetyHoldKind.ACUTE_ILLNESS, illness.kind)
        assertEquals(SafetyHoldKind.NEW_OR_WORSENING_PAIN_OR_INJURY, injury.kind)
        assertEquals(SafetyHoldKind.MEDICALLY_RESTRICTED, restricted.kind)
        assertEquals(SafetyHoldKind.POST_SESSION_NEW_OR_WORSE_PAIN, pain.kind)
        listOf(red, illness, injury, restricted).forEach {
            assertEquals(ConstraintSourceType.CHECK_IN, it.sourceType)
        }
        assertEquals(ConstraintSourceType.SESSION, pain.sourceType)
        assertEquals(uuid(5), pain.sourceId)
    }

    @Test
    fun acuteFactoryRejectsNone() {
        assertFailsWith<IllegalArgumentException> {
            SafetyHoldFactory.acute(AcuteIssue.NONE, uuid(1), snapshot("2026-08-27T03:00:00.000Z", 0))
        }
    }

    @Test
    fun restSuppressionUsesDecisionSourceAndSameClockContract() {
        val source = uuid(9)
        val suppression = RestDaySuppressionFactory.create(source, snapshot("2026-08-27T03:00:00.000Z", 10))

        assertEquals(source, suppression.sourceDecisionId)
        assertEquals(1, suppression.ruleVersion)
        assertEquals(Instant.parse("2026-08-27T17:00:00.000Z"), suppression.expiresAtUtc)
        assertEquals(
            ConstraintStatus.ACTIVE,
            DailyConstraintResolver.resolveForPersistence(
                suppression,
                snapshot("2026-08-27T04:00:00.000Z", 3_600_010),
            ).status,
        )
    }

    @Test
    fun sameBootMonotonicDeadlineIsAuthorityAndEqualityIsInactive() {
        val created = snapshot("2026-08-27T03:00:00.000Z", 1_000, boot = 7, generation = 1)
        val hold = SafetyHoldFactory.redFlag(uuid(1), created)
        val justBefore = snapshot(
            "2026-08-27T02:00:00.000Z",
            hold.clockEvidence.monotonicDeadlineMillis - 1,
            boot = 7,
            generation = 99,
            zone = ZoneId.of("UTC"),
        )
        val equality = justBefore.copy(elapsedRealtimeMillis = hold.clockEvidence.monotonicDeadlineMillis)

        assertEquals(
            ConstraintStatus.ACTIVE,
            DailyConstraintResolver.resolveForPersistence(hold, justBefore).status,
        )
        assertEquals(
            ConstraintStatus.INACTIVE,
            DailyConstraintResolver.resolveForPersistence(hold, equality).status,
        )
    }

    @Test
    fun rebootOrElapsedRollbackReturnsActiveReconciliationPayloadEvenAfterWallExpiry() {
        val created = snapshot("2026-08-27T03:00:00.000Z", 50_000, boot = 7)
        val hold = SafetyHoldFactory.redFlag(uuid(1), created)
        val afterWallExpiryOnNewBoot = snapshot("2026-08-28T03:00:00.000Z", 1, boot = 8)
        val rollback = snapshot("2026-08-27T04:00:00.000Z", 49_999, boot = 7)

        assertIs<ConstraintResolution.Reconciled>(
            DailyConstraintResolver.resolveForPersistence(hold, afterWallExpiryOnNewBoot),
        ).also { assertEquals(ConstraintStatus.ACTIVE, it.status) }
        assertIs<ConstraintResolution.Reconciled>(
            DailyConstraintResolver.resolveForPersistence(hold, rollback),
        ).also { assertEquals(ConstraintStatus.ACTIVE, it.status) }
    }

    @Test
    fun discontinuityReturnsReconciledEvidenceForAtomicPersistenceAndEventuallyExpires() {
        val created = snapshot("2026-08-27T03:00:00.000Z", 1_000, boot = 7)
        val original = SafetyHoldFactory.redFlag(uuid(1), created)
        val checkpointNow = snapshot("2026-08-27T07:00:00.000Z", 14_401_000, boot = 7)
        val checkpoint = assertIs<ConstraintResolution.Checkpointed>(
            DailyConstraintResolver.resolveForPersistence(original, checkpointNow),
        )
        assertEquals(Duration.ofHours(10).toMillis(), checkpoint.clockEvidenceToPersist.remainingElapsedMillisAtLastCheckpoint)

        val checkpointed = original.copy(clockEvidence = checkpoint.clockEvidenceToPersist)
        val newBoot = snapshot("2026-08-27T12:00:00.000Z", 100, boot = 8)
        val reconciliation = assertIs<ConstraintResolution.Reconciled>(
            DailyConstraintResolver.resolveForPersistence(checkpointed, newBoot),
        )

        assertEquals(ConstraintStatus.ACTIVE, reconciliation.status)
        assertEquals(8, reconciliation.clockEvidenceToPersist.originBootMarker)
        assertEquals(100, reconciliation.clockEvidenceToPersist.createdElapsedRealtimeMillis)
        assertEquals(Duration.ofHours(10).toMillis(), reconciliation.clockEvidenceToPersist.remainingElapsedMillisAtLastCheckpoint)
        assertEquals(
            100 + Duration.ofHours(10).toMillis(),
            reconciliation.clockEvidenceToPersist.monotonicDeadlineMillis,
        )
        assertEquals(original.clockEvidence.originalDurationMillis, reconciliation.clockEvidenceToPersist.originalDurationMillis)
        assertEquals(original.occurred, checkpointed.occurred)
        assertEquals(original.expiresAtUtc, checkpointed.expiresAtUtc)

        val rebased = checkpointed.copy(clockEvidence = reconciliation.clockEvidenceToPersist)
        val equality = snapshot(
            "2026-08-28T12:00:00.000Z",
            reconciliation.clockEvidenceToPersist.monotonicDeadlineMillis,
            boot = 8,
        )
        val inactive = assertIs<ConstraintResolution.Checkpointed>(
            DailyConstraintResolver.resolveForPersistence(rebased, equality),
        )
        assertEquals(ConstraintStatus.INACTIVE, inactive.status)
        assertEquals(0, inactive.clockEvidenceToPersist.remainingElapsedMillisAtLastCheckpoint)
    }

    @Test
    fun reconciliationUsesWallRemainingWhenItExceedsLastCheckpointButKeepsOriginImmutable() {
        val original = SafetyHoldFactory.redFlag(
            uuid(1),
            snapshot("2026-08-27T03:00:00.000Z", 1_000, boot = 7),
        )
        val evidenceWithTwoHoursCheckpoint = original.clockEvidence.checkpoint(Duration.ofHours(2).toMillis())
        val checkpointed = original.copy(clockEvidence = evidenceWithTwoHoursCheckpoint)
        val wallRolledBackOnNewBoot = snapshot("2026-08-27T04:00:00.000Z", 50, boot = 8)

        val result = assertIs<ConstraintResolution.Reconciled>(
            DailyConstraintResolver.resolveForPersistence(checkpointed, wallRolledBackOnNewBoot),
        )

        assertEquals(Duration.ofHours(13).toMillis(), result.clockEvidenceToPersist.remainingElapsedMillisAtLastCheckpoint)
        assertEquals(original.occurred, checkpointed.occurred)
        assertEquals(original.expiresAtUtc, checkpointed.expiresAtUtc)
    }

    @Test
    fun reconciliationArithmeticOverflowFailsConservativelyWithoutPersistencePayload() {
        val original = SafetyHoldFactory.redFlag(
            uuid(1),
            snapshot("2026-08-27T03:00:00.000Z", 1_000, boot = 7),
        )
        val overflowEvidence = ClockEvidence.restore(
            originBootMarker = 7,
            createdElapsedRealtimeMillis = 0,
            monotonicDeadlineMillis = Long.MAX_VALUE,
            remainingElapsedMillisAtLastCheckpoint = 1,
            originalDurationMillis = Long.MAX_VALUE,
        )
        val overflowWall = original.copy(
            expiresAtUtc = Instant.ofEpochMilli(Long.MAX_VALUE),
            clockEvidence = overflowEvidence,
        )
        val result = assertIs<ConstraintResolution.ConservativeFailure>(
            DailyConstraintResolver.resolveForPersistence(
                overflowWall,
                ClockSnapshot(
                    instant = Instant.ofEpochMilli(Long.MIN_VALUE),
                    elapsedRealtimeMillis = 0,
                    bootMarker = 8,
                    clockGeneration = 1,
                    zoneId = ZoneId.of("UTC"),
                    utcOffsetMinutes = 0,
                ),
            ),
        )

        assertEquals(ConstraintStatus.ACTIVE_CONSERVATIVE, result.status)
        assertEquals(ConstraintFailureReason.ARITHMETIC_OVERFLOW, result.reason)
        assertNull(result.clockEvidenceToPersist)

        val deadlineOverflow = assertIs<ConstraintResolution.ConservativeFailure>(
            DailyConstraintResolver.resolveForPersistence(
                original.copy(clockEvidence = overflowEvidence),
                snapshot(
                    raw = "2026-08-28T03:00:00.000Z",
                    elapsed = Long.MAX_VALUE,
                    boot = 8,
                ),
            ),
        )
        assertEquals(ConstraintStatus.ACTIVE_CONSERVATIVE, deadlineOverflow.status)
        assertEquals(ConstraintFailureReason.ARITHMETIC_OVERFLOW, deadlineOverflow.reason)
        assertNull(deadlineOverflow.clockEvidenceToPersist)
    }

    @Test
    fun fiveFieldClockEvidenceRejectsIncoherentValues() {
        assertFailsWith<IllegalArgumentException> {
            ClockEvidence.restore(
                originBootMarker = 1,
                createdElapsedRealtimeMillis = 10,
                monotonicDeadlineMillis = 20,
                remainingElapsedMillisAtLastCheckpoint = 11,
                originalDurationMillis = 10,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ClockEvidence.restore(
                originBootMarker = 1,
                createdElapsedRealtimeMillis = 10,
                monotonicDeadlineMillis = 19,
                remainingElapsedMillisAtLastCheckpoint = 10,
                originalDurationMillis = 10,
            )
        }
    }

    @Test
    fun fakeClockCanDriveConstraintBoundaryWithoutWallAuthority() {
        val fake = FakeClock(
            initialInstant = Instant.parse("2026-08-27T03:00:00.000Z"),
            initialElapsedRealtimeMillis = 1_000,
            initialBootMarker = 7,
            initialClockGeneration = 1,
            initialZoneId = ZoneId.of("Asia/Bangkok"),
        )
        val hold = SafetyHoldFactory.redFlag(uuid(1), fake.snapshot())
        fake.rollbackWall(Duration.ofHours(2))
        fake.advanceElapsed(Duration.ofMillis(hold.clockEvidence.originalDurationMillis))

        assertEquals(
            ConstraintStatus.INACTIVE,
            DailyConstraintResolver.resolveForPersistence(hold, fake.snapshot()).status,
        )
    }

    private fun snapshot(
        raw: String,
        elapsed: Long,
        boot: Long = 7,
        generation: Long = 1,
        zone: ZoneId = ZoneId.of("Asia/Bangkok"),
    ): ClockSnapshot {
        val instant = Instant.parse(raw)
        return ClockSnapshot(
            instant,
            elapsed,
            boot,
            generation,
            zone,
            zone.rules.getOffset(instant).totalSeconds / 60,
        )
    }

    private fun uuid(last: Int): UUID = UUID.fromString("00000000-0000-4000-8000-${last.toString().padStart(12, '0')}")
}
