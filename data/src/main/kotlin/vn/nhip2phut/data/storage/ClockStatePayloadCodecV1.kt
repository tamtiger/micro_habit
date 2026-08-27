package vn.nhip2phut.data.storage

import vn.nhip2phut.domain.time.DurableClockState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.ZoneId

object ClockStatePayloadCodecV1 {
    private const val MAX_ZONE_ID_BYTES = 255

    fun encode(state: DurableClockState): ByteArray {
        val zoneBytes = state.zoneId.id.encodeToByteArray()
        if (zoneBytes.isEmpty() || zoneBytes.size > MAX_ZONE_ID_BYTES) {
            throw ClockStatePayloadException()
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeLong(state.clockGeneration)
                output.writeLong(state.bootMarker)
                output.writeLong(state.elapsedRealtimeMillis)
                output.writeLong(state.wallMinusElapsedMillis)
                output.writeInt(zoneBytes.size)
                output.write(zoneBytes)
            }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): DurableClockState {
        try {
            val byteInput = ByteArrayInputStream(bytes)
            val input = DataInputStream(byteInput)
            val clockGeneration = input.readLong()
            val bootMarker = input.readLong()
            val elapsedRealtimeMillis = input.readLong()
            val wallMinusElapsedMillis = input.readLong()
            val zoneSize = input.readInt()
            if (zoneSize !in 1..MAX_ZONE_ID_BYTES || byteInput.available() != zoneSize) {
                throw ClockStatePayloadException()
            }
            val zoneBytes = ByteArray(zoneSize).also(input::readFully)
            if (byteInput.available() != 0) throw ClockStatePayloadException()
            val zoneText = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(zoneBytes))
                .toString()
            val zoneId = ZoneId.of(zoneText)
            return DurableClockState(
                clockGeneration = clockGeneration,
                bootMarker = bootMarker,
                zoneId = zoneId,
                elapsedRealtimeMillis = elapsedRealtimeMillis,
                wallMinusElapsedMillis = wallMinusElapsedMillis,
            )
        } catch (failure: ClockStatePayloadException) {
            throw failure
        } catch (failure: Exception) {
            throw ClockStatePayloadException(failure)
        }
    }
}

class ClockStatePayloadException(cause: Throwable? = null) :
    Exception("Clock state payload is invalid.", cause)
