package vn.nhip2phut.domain.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnboardingContractsV1Test {
    @Test
    fun `debug safety content is explicitly non production`() {
        val identity = SafetyContentIdentityV1.debugFixture(
            manifestVersion = "1.0.0",
            globalSafetyDigestSha256 = "a".repeat(64),
        )

        assertEquals("NON_PRODUCTION_NOT_CLINICALLY_APPROVED", identity.approvalMarker)
        assertTrue(identity.isDebugOnly)
        assertEquals(SafetyContentAvailabilityV1.DebugFixture(identity), identity.forDebugBuild())
        assertEquals(SafetyContentAvailabilityV1.Unavailable, identity.forReleaseBuild())
    }

    @Test
    fun `content identity rejects aliases and malformed digests`() {
        assertFailsWith<IllegalArgumentException> {
            SafetyContentIdentityV1.debugFixture("v1", "a".repeat(64))
        }
        assertFailsWith<IllegalArgumentException> {
            SafetyContentIdentityV1.debugFixture("1.0.0", "A".repeat(64))
        }
    }

    @Test
    fun `validated initial schedule keeps exact canonical ordering`() {
        val schedule = ValidatedInitialScheduleV1.create(
            selectedWeekdays = linkedSetOf(5, 1, 3),
            workStart = "09:00",
            workEnd = "17:00",
            reminderTimes = listOf("10:30", "15:30"),
        ).getOrThrow()

        assertEquals(listOf(1, 3, 5), schedule.selectedWeekdays)
        assertEquals("09:00", schedule.workStart.wire)
        assertEquals(listOf("10:30", "15:30"), schedule.reminderTimes.map { it.wire })
    }

    @Test
    fun `initial schedule rejects normalization candidates`() {
        assertTrue(
            ValidatedInitialScheduleV1.create(
                selectedWeekdays = setOf(1),
                workStart = "9:00",
                workEnd = "17:00",
                reminderTimes = listOf("10:00"),
            ).isFailure,
        )
        assertTrue(
            ValidatedInitialScheduleV1.create(
                selectedWeekdays = setOf(1),
                workStart = "09:00",
                workEnd = "17:00",
                reminderTimes = listOf("15:00", "10:00"),
            ).isFailure,
        )
    }
}
