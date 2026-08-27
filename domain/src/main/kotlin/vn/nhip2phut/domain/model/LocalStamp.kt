package vn.nhip2phut.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LocalStamp(
    val instant: Instant,
    val localDate: LocalDate,
    val zoneId: ZoneId,
    val utcOffsetMinutes: Int,
)

data class ClockSnapshot(
    val instant: Instant,
    val elapsedRealtimeMillis: Long,
    val bootMarker: Long,
    val clockGeneration: Long,
    val zoneId: ZoneId,
    val utcOffsetMinutes: Int,
)

interface ClockPort {
    fun snapshot(): ClockSnapshot
}

