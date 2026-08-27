package vn.nhip2phut.platform.time

import android.os.SystemClock
import vn.nhip2phut.domain.model.ClockPort
import vn.nhip2phut.domain.model.ClockSnapshot
import java.time.Instant
import java.time.ZoneId

class AndroidClock : ClockPort {
    override fun snapshot(): ClockSnapshot {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val offset = zone.rules.getOffset(now)
        return ClockSnapshot(
            instant = now,
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            bootMarker = SystemClock.elapsedRealtimeNanos(),
            clockGeneration = 0L,
            zoneId = zone,
            utcOffsetMinutes = offset.totalSeconds / 60,
        )
    }
}

