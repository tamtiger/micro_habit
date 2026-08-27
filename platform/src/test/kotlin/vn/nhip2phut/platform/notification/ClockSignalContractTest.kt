package vn.nhip2phut.platform.notification

import vn.nhip2phut.domain.time.ClockUpdateReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClockSignalContractTest {
    @Test
    fun `only exact clock actions map to durable update reasons`() {
        assertEquals(
            ClockUpdateReason.BOOT_COMPLETED,
            ClockSignalActions.fromAction("android.intent.action.BOOT_COMPLETED"),
        )
        assertEquals(
            ClockUpdateReason.TIME_SET,
            ClockSignalActions.fromAction("android.intent.action.TIME_SET"),
        )
        assertEquals(
            ClockUpdateReason.TIMEZONE_CHANGED,
            ClockSignalActions.fromAction("android.intent.action.TIMEZONE_CHANGED"),
        )
        assertNull(ClockSignalActions.fromAction(null))
        assertNull(ClockSignalActions.fromAction("android.intent.action.DATE_CHANGED"))
    }

    @Test
    fun `pending result completion runs at most once`() {
        var finishes = 0
        val once = CompleteOnce { finishes++ }

        assertTrue(once.complete())
        assertFalse(once.complete())
        assertEquals(1, finishes)
    }
}
