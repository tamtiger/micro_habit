package vn.nhip2phut.domain.checkin

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vn.nhip2phut.domain.model.AcuteIssue
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.model.Energy
import vn.nhip2phut.domain.model.Intent
import vn.nhip2phut.domain.model.Stiffness

class CheckInModelsTest {
    @Test
    fun canonicalAnswerUnionExposesOnlyFieldsAllowedByItsDiscriminator() {
        val red = PersistedCheckInAnswersV1.RedFlagStop
        val acute = PersistedCheckInAnswersV1.AcuteStop(AcuteIssue.ACUTE_ILLNESS)
        val full = PersistedCheckInAnswersV1.Full(Energy.GOOD, Stiffness.MILD, Intent.MODERATE)

        assertEquals(CheckInAnswersKindV1.RED_FLAG_STOP, red.kind)
        assertTrue(red.redFlag)
        assertNull(red.acuteIssue)
        assertNull(red.energy)

        assertEquals(CheckInAnswersKindV1.ACUTE_STOP, acute.kind)
        assertEquals(false, acute.redFlag)
        assertEquals(AcuteIssue.ACUTE_ILLNESS, acute.acuteIssue)
        assertNull(acute.stiffness)

        assertEquals(CheckInAnswersKindV1.FULL, full.kind)
        assertEquals(false, full.redFlag)
        assertEquals(AcuteIssue.NONE, full.acuteIssue)
        assertEquals(Energy.GOOD, full.energy)
        assertEquals(Stiffness.MILD, full.stiffness)
        assertEquals(Intent.MODERATE, full.intent)
    }

    @Test
    fun acuteStopRejectsNone() {
        assertFailsWith<IllegalArgumentException> {
            PersistedCheckInAnswersV1.AcuteStop(AcuteIssue.NONE)
        }
    }

    @Test
    fun oneSnapshotCreatesCanonicalStampAndSixHourFreshnessEvidence() {
        val snapshot = snapshot(
            instant = "2026-08-27T03:00:00.000Z",
            elapsed = 12_345L,
            boot = 7L,
            generation = 3L,
        )
        val checkIn = CheckIn.create(
            id = uuid(1),
            parentCheckInId = null,
            scheduleVersionId = uuid(2),
            answers = PersistedCheckInAnswersV1.Full(Energy.OKAY, Stiffness.NONE, Intent.GENTLE),
            snapshot = snapshot,
        )

        assertEquals(snapshot.instant, checkIn.confirmedAt.instant)
        assertEquals(snapshot.zoneId, checkIn.confirmedAt.zoneId)
        assertEquals(snapshot.elapsedRealtimeMillis, checkIn.freshnessEvidence.confirmedElapsedRealtimeMillis)
        assertEquals(snapshot.elapsedRealtimeMillis + DecisionFreshnessEvidence.TTL_MILLIS, checkIn.freshnessEvidence.ttlMonotonicDeadlineMillis)
        assertEquals(
            snapshot.instant.toEpochMilli() - snapshot.elapsedRealtimeMillis,
            checkIn.freshnessEvidence.confirmedWallMinusElapsedMillis,
        )
        assertEquals(CheckIn.RULE_VERSION_V1, checkIn.ruleVersion)
    }

    @Test
    fun freshnessCreationFailsClosedOnDeadlineOrMappingOverflow() {
        assertFailsWith<FreshnessEvidenceOverflowException> {
            DecisionFreshnessEvidence.create(
                snapshot(instant = "2026-08-27T03:00:00.000Z", elapsed = Long.MAX_VALUE, boot = 1, generation = 1),
            )
        }
        assertFailsWith<FreshnessEvidenceOverflowException> {
            DecisionFreshnessEvidence.create(
                ClockSnapshot(
                    instant = Instant.ofEpochMilli(Long.MIN_VALUE),
                    elapsedRealtimeMillis = 1,
                    bootMarker = 1,
                    clockGeneration = 1,
                    zoneId = ZoneId.of("UTC"),
                    utcOffsetMinutes = 0,
                ),
            )
        }
    }

    @Test
    fun checkInRejectsSelfParent() {
        val id = uuid(1)
        assertFailsWith<IllegalArgumentException> {
            CheckIn.create(
                id = id,
                parentCheckInId = id,
                scheduleVersionId = uuid(2),
                answers = PersistedCheckInAnswersV1.RedFlagStop,
                snapshot = snapshot("2026-08-27T03:00:00.000Z", 1, 1, 1),
            )
        }
    }

    private fun snapshot(
        instant: String,
        elapsed: Long,
        boot: Long,
        generation: Long,
        zone: ZoneId = ZoneId.of("Asia/Bangkok"),
    ): ClockSnapshot {
        val parsed = Instant.parse(instant)
        return ClockSnapshot(
            instant = parsed,
            elapsedRealtimeMillis = elapsed,
            bootMarker = boot,
            clockGeneration = generation,
            zoneId = zone,
            utcOffsetMinutes = zone.rules.getOffset(parsed).totalSeconds / 60,
        )
    }

    private fun uuid(last: Int): UUID = UUID.fromString("00000000-0000-4000-8000-${last.toString().padStart(12, '0')}")
}
