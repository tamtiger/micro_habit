package vn.nhip2phut.domain.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTimeTest {
    @Test
    fun acceptsOnlyCanonicalZeroPaddedHourMinute() {
        assertEquals("09:00", ScheduleTime.parse("09:00")?.wire)
        assertEquals("23:59", ScheduleTime.parse("23:59")?.wire)
    }

    @Test
    fun rejectsAliasesInsteadOfNormalizing() {
        assertNull(ScheduleTime.parse("9:00"))
        assertNull(ScheduleTime.parse("09:00:00"))
        assertNull(ScheduleTime.parse(" 09:00"))
        assertNull(ScheduleTime.parse("24:00"))
    }
}

