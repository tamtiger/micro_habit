package vn.nhip2phut.platform.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import vn.nhip2phut.domain.model.ClockPort
import vn.nhip2phut.domain.model.ClockSnapshot
import vn.nhip2phut.domain.time.RawClockSnapshot
import java.time.Instant
import java.time.ZoneId

fun interface ClockGenerationSource {
    fun currentGeneration(): Long
}

fun interface RawClockSource {
    fun snapshot(): RawClockSnapshot
}

class AndroidRawClockSource internal constructor(
    private val wallTimeMillis: () -> Long,
    private val elapsedRealtimeMillis: () -> Long,
    private val bootMarker: () -> Long?,
    private val zoneId: () -> ZoneId,
) : RawClockSource {
    constructor(
        context: Context,
    ) : this(
        wallTimeMillis = System::currentTimeMillis,
        elapsedRealtimeMillis = SystemClock::elapsedRealtime,
        bootMarker = {
            try {
                Settings.Global.getInt(
                    context.applicationContext.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    BOOT_COUNT_UNAVAILABLE,
                ).takeIf { it >= 0 }?.toLong()
            } catch (_: RuntimeException) {
                null
            }
        },
        zoneId = ZoneId::systemDefault,
    )

    override fun snapshot(): RawClockSnapshot {
        val wallMillis = wallTimeMillis()
        val elapsedMillis = elapsedRealtimeMillis()
        val verifiedBootMarker = bootMarker() ?: throw ClockSnapshotUnavailableException()
        val currentZone = zoneId()
        if (elapsedMillis < 0 || verifiedBootMarker < 0) {
            throw ClockSnapshotUnavailableException()
        }
        val instant = try {
            Instant.ofEpochMilli(wallMillis)
        } catch (failure: RuntimeException) {
            throw ClockSnapshotUnavailableException(failure)
        }
        val offset = currentZone.rules.getOffset(instant)
        return RawClockSnapshot(
            instant = instant,
            elapsedRealtimeMillis = elapsedMillis,
            bootMarker = verifiedBootMarker,
            zoneId = currentZone,
            utcOffsetMinutes = offset.totalSeconds / 60,
        )
    }

    companion object {
        private const val BOOT_COUNT_UNAVAILABLE = -1
    }
}

class AndroidClock(
    private val rawClockSource: RawClockSource,
    private val generationSource: ClockGenerationSource,
) : ClockPort {
    constructor(
        context: Context,
        generationSource: ClockGenerationSource,
    ) : this(
        rawClockSource = AndroidRawClockSource(context),
        generationSource = generationSource,
    )

    internal constructor(
        wallTimeMillis: () -> Long,
        elapsedRealtimeMillis: () -> Long,
        bootMarker: () -> Long?,
        clockGeneration: () -> Long,
        zoneId: () -> ZoneId,
    ) : this(
        rawClockSource = AndroidRawClockSource(
            wallTimeMillis = wallTimeMillis,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            bootMarker = bootMarker,
            zoneId = zoneId,
        ),
        generationSource = ClockGenerationSource(clockGeneration),
    )

    override fun snapshot(): ClockSnapshot {
        val generationBefore = generationSource.currentGeneration()
        if (generationBefore < 0) throw ClockGenerationUnavailableException()
        val raw = rawClockSource.snapshot()
        val generationAfter = generationSource.currentGeneration()
        if (generationAfter != generationBefore) throw ClockGenerationUnavailableException()
        return raw.withGeneration(generationBefore)
    }
}

class ClockGenerationUnavailableException(cause: Throwable? = null) :
    IllegalStateException("Durable clock generation is unavailable.", cause)

class ClockSnapshotUnavailableException(cause: Throwable? = null) :
    IllegalStateException("A verified clock snapshot is unavailable.", cause)

