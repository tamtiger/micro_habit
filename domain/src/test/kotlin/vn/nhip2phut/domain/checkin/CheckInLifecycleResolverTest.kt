package vn.nhip2phut.domain.checkin

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.schedule.ScheduleTime
import vn.nhip2phut.domain.schedule.WorkScheduleVersion
import vn.nhip2phut.domain.time.FakeClock

class CheckInLifecycleResolverTest {
    @Test
    fun lifecycleUsesRequiredPrecedence() {
        val source = source()
        val otherSchedule = schedule(id = uuid(99))
        val nextDayOutside = snapshot(
            "2026-08-28T18:00:00.000Z",
            source.freshnessEvidence.confirmedElapsedRealtimeMillis + 30_000_000,
        )

        assertReconfirm(
            ReconfirmationReasonV1.SCHEDULE_CHANGED,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, otherSchedule, source.confirmedAt, source.freshnessEvidence, nextDayOutside),
        )

        val outside = snapshot(
            "2026-08-27T10:00:00.000Z",
            source.freshnessEvidence.confirmedElapsedRealtimeMillis + 7_000_000,
        )
        assertEquals(
            CheckInLifecycleResult.Expired,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, outside),
        )

        val nextDayInside = snapshot(
            "2026-08-28T03:00:00.000Z",
            source.freshnessEvidence.confirmedElapsedRealtimeMillis + 86_400_000,
        )
        assertReconfirm(
            ReconfirmationReasonV1.LOCAL_DATE_CHANGED,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, nextDayInside),
        )
    }

    @Test
    fun ttlIsHalfOpenAndEqualityRequiresReconfirmation() {
        val source = source()
        val before = snapshot(
            instant = "2026-08-27T08:59:59.999Z",
            elapsed = source.freshnessEvidence.ttlMonotonicDeadlineMillis - 1,
        )
        val equality = snapshot(
            instant = "2026-08-27T09:00:00.000Z",
            elapsed = source.freshnessEvidence.ttlMonotonicDeadlineMillis,
        )

        assertEquals(
            CheckInLifecycleResult.Fresh,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, before),
        )
        assertReconfirm(
            ReconfirmationReasonV1.TTL,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, equality),
        )
    }

    @Test
    fun mappingDriftAtTwoSecondsIsFreshAndOneMillisecondMoreRequiresReconfirmation() {
        val source = source()
        val elapsedAdvance = Duration.ofHours(1).toMillis()
        val exactBoundary = snapshot(
            instant = "2026-08-27T04:00:02.000Z",
            elapsed = source.freshnessEvidence.confirmedElapsedRealtimeMillis + elapsedAdvance,
        )
        val overBoundary = snapshot(
            instant = "2026-08-27T04:00:02.001Z",
            elapsed = source.freshnessEvidence.confirmedElapsedRealtimeMillis + elapsedAdvance,
        )

        assertEquals(
            CheckInLifecycleResult.Fresh,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, exactBoundary),
        )
        assertReconfirm(
            ReconfirmationReasonV1.TIMEZONE_OR_TIME_CHANGE,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, overBoundary),
        )
    }

    @Test
    fun generationOrZoneChangeWinsOverTtlWithinSameDateAndWindow() {
        val source = source()
        val changedGeneration = snapshot(
            "2026-08-27T09:00:00.000Z",
            source.freshnessEvidence.ttlMonotonicDeadlineMillis,
            generation = source.freshnessEvidence.confirmedClockGeneration + 1,
        )
        val changedZone = snapshot(
            "2026-08-27T09:00:00.000Z",
            source.freshnessEvidence.ttlMonotonicDeadlineMillis,
            zone = ZoneId.of("Asia/Ho_Chi_Minh"),
        )

        assertReconfirm(
            ReconfirmationReasonV1.TIMEZONE_OR_TIME_CHANGE,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, changedGeneration),
        )
        assertReconfirm(
            ReconfirmationReasonV1.TIMEZONE_OR_TIME_CHANGE,
            CheckInLifecycleResolver.resolve(source.scheduleVersionId, schedule(), source.confirmedAt, source.freshnessEvidence, changedZone),
        )
    }

    @Test
    fun rebootElapsedRollbackAndMappingOverflowAreClockUnknown() {
        val source = source()
        val rebooted = snapshot("2026-08-27T04:00:00.000Z", 1, boot = source.freshnessEvidence.confirmedBootMarker + 1)
        val rolledBack = snapshot(
            "2026-08-27T04:00:00.000Z",
            source.freshnessEvidence.confirmedElapsedRealtimeMillis - 1,
        )

        assertReconfirm(
            ReconfirmationReasonV1.CLOCK_UNKNOWN,
            CheckInFreshnessResolver.resolve(source.freshnessEvidence, rebooted),
        )
        assertReconfirm(
            ReconfirmationReasonV1.CLOCK_UNKNOWN,
            CheckInFreshnessResolver.resolve(source.freshnessEvidence, rolledBack),
        )

        val minimumClock = FakeClock(
            initialInstant = Instant.ofEpochMilli(Long.MIN_VALUE),
            initialElapsedRealtimeMillis = 0,
            initialBootMarker = 4,
            initialClockGeneration = 5,
            initialZoneId = ZoneId.of("UTC"),
        )
        val minimumEvidence = DecisionFreshnessEvidence.create(minimumClock.snapshot())
        minimumClock.advanceElapsed(Duration.ofMillis(1))
        assertReconfirm(
            ReconfirmationReasonV1.CLOCK_UNKNOWN,
            CheckInFreshnessResolver.resolve(minimumEvidence, minimumClock.snapshot()),
        )
    }

    private fun source(): CheckIn {
        val snapshot = snapshot("2026-08-27T03:00:00.000Z", 100_000, boot = 4, generation = 5)
        return CheckIn.create(
            id = uuid(1),
            parentCheckInId = null,
            scheduleVersionId = uuid(2),
            answers = PersistedCheckInAnswersV1.RedFlagStop,
            snapshot = snapshot,
        )
    }

    private fun schedule(id: UUID = uuid(2)): WorkScheduleVersion = WorkScheduleVersion(
        id = id,
        enabled = true,
        selectedWeekdays = setOf(1, 2, 3, 4, 5),
        workStart = time("09:00"),
        workEnd = time("17:00"),
        reminderTimes = listOf(time("10:00")),
        effectiveFrom = sourceStamp("2026-08-27T02:00:00.000Z"),
        replacedAt = null,
    )

    private fun sourceStamp(raw: String): vn.nhip2phut.domain.model.LocalStamp {
        val instant = Instant.parse(raw)
        val zone = ZoneId.of("Asia/Bangkok")
        return vn.nhip2phut.domain.model.LocalStamp(
            instant,
            java.time.LocalDate.ofInstant(instant, zone),
            zone,
            zone.rules.getOffset(instant).totalSeconds / 60,
        )
    }

    private fun snapshot(
        instant: String,
        elapsed: Long,
        boot: Long = 4,
        generation: Long = 5,
        zone: ZoneId = ZoneId.of("Asia/Bangkok"),
    ): ClockSnapshot {
        val parsed = Instant.parse(instant)
        return ClockSnapshot(
            parsed,
            elapsed,
            boot,
            generation,
            zone,
            zone.rules.getOffset(parsed).totalSeconds / 60,
        )
    }

    private fun assertReconfirm(reason: ReconfirmationReasonV1, result: CheckInLifecycleResult) {
        assertEquals(reason, assertIs<CheckInLifecycleResult.ReconfirmRequired>(result).reason)
    }

    private fun time(raw: String): ScheduleTime = requireNotNull(ScheduleTime.parse(raw))
    private fun uuid(last: Int): UUID = UUID.fromString("00000000-0000-4000-8000-${last.toString().padStart(12, '0')}")
}
