package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

data class UuidWireV1 private constructor(val value: String) : Comparable<UuidWireV1> {
    private val rawBytes: ByteArray
        get() {
            val uuid = UUID.fromString(value)
            return ByteArray(16).also { bytes ->
                for (index in 0 until 8) bytes[index] = (uuid.mostSignificantBits ushr (56 - index * 8)).toByte()
                for (index in 0 until 8) bytes[index + 8] = (uuid.leastSignificantBits ushr (56 - index * 8)).toByte()
            }
        }

    override fun compareTo(other: UuidWireV1): Int {
        val left = rawBytes
        val right = other.rawBytes
        for (index in left.indices) {
            val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return 0
    }

    override fun toString(): String = value

    companion object {
        private val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        fun parse(value: String): UuidWireV1 {
            if (!pattern.matches(value)) fail("uuid", "expected canonical lowercase RFC-variant UUID")
            try {
                if (UUID.fromString(value).toString() != value) fail("uuid", "UUID is not canonical")
            } catch (_: IllegalArgumentException) {
                fail("uuid", "invalid UUID")
            }
            return UuidWireV1(value)
        }
    }
}

data class InstantWireV1 private constructor(val value: String, val instant: Instant) : Comparable<InstantWireV1> {
    override fun compareTo(other: InstantWireV1): Int = instant.compareTo(other.instant)
    override fun toString(): String = value

    companion object {
        private val pattern = Regex(
            "(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}T" +
                "(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]\\.[0-9]{3}Z",
        )

        fun parse(value: String): InstantWireV1 {
            if (!pattern.matches(value)) fail("instant", "expected exact UTC millisecond InstantWireV1")
            val parsed = try {
                Instant.parse(value)
            } catch (_: DateTimeException) {
                fail("instant", "invalid UTC instant")
            }
            return InstantWireV1(value, parsed)
        }
    }
}

data class DateWireV1 private constructor(val value: String, val date: LocalDate) : Comparable<DateWireV1> {
    override fun compareTo(other: DateWireV1): Int = date.compareTo(other.date)
    override fun toString(): String = value

    companion object {
        private val pattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

        fun parse(value: String): DateWireV1 {
            if (!pattern.matches(value) || value.startsWith("0000-")) fail("date", "expected Gregorian YYYY-MM-DD in year 0001..9999")
            val parsed = try {
                LocalDate.parse(value, formatter)
            } catch (_: DateTimeException) {
                fail("date", "invalid Gregorian date")
            }
            return DateWireV1(value, parsed)
        }
    }
}

data class TimeMinuteWireV1 private constructor(val value: String, val time: LocalTime) : Comparable<TimeMinuteWireV1> {
    override fun compareTo(other: TimeMinuteWireV1): Int = time.compareTo(other.time)
    override fun toString(): String = value

    companion object {
        private val pattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

        fun parse(value: String): TimeMinuteWireV1 {
            if (!pattern.matches(value)) fail("time", "expected exact ASCII HH:mm")
            val parsed = LocalTime.parse(value)
            if (parsed.second != 0 || parsed.nano != 0 || parsed.toString() != value) fail("time", "time is not byte-canonical")
            return TimeMinuteWireV1(value, parsed)
        }
    }
}

data class SemVerWireV1 private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private val pattern = Regex(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)" +
                "(?:-(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)" +
                "(?:\\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*)?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?",
        )

        fun parse(value: String): SemVerWireV1 {
            if (!pattern.matches(value)) fail("semver", "expected strict SemVer")
            return SemVerWireV1(value)
        }
    }
}

data class Sha256DigestWireV1 private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private val pattern = Regex("[0-9a-f]{64}")
        fun parse(value: String): Sha256DigestWireV1 {
            if (!pattern.matches(value)) fail("sha256", "expected 64 lowercase hexadecimal characters")
            return Sha256DigestWireV1(value)
        }
    }
}

data class LocalStampWireV1(
    val occurredAtUtc: InstantWireV1,
    val localDate: DateWireV1,
    val zoneId: String,
    val utcOffsetMinutes: Long,
) {
    init {
        validateLocalStamp(occurredAtUtc, localDate, zoneId, utcOffsetMinutes, "LocalStampWireV1")
    }

    internal fun toJson(): JsonObject = JsonObject(
        linkedMapOf(
            "occurred_at_utc" to JsonPrimitive(occurredAtUtc.value),
            "local_date" to JsonPrimitive(localDate.value),
            "zone_id" to JsonPrimitive(zoneId),
            "utc_offset_minutes" to JsonPrimitive(utcOffsetMinutes),
        ),
    )

    companion object {
        internal fun fromObject(value: StrictJsonObjectV1, path: String): LocalStampWireV1 {
            LocalStampSchemaV1.validateAndOrder(value, path)
            return LocalStampWireV1(
                InstantWireV1.parse(value.requiredString("occurred_at_utc", path)),
                DateWireV1.parse(value.requiredString("local_date", path)),
                value.requiredString("zone_id", path),
                value.requiredInt64("utc_offset_minutes", path),
            )
        }
    }
}

internal enum class FieldPresenceV1 { REQUIRED, OPTIONAL }

internal data class WireFieldV1(
    val key: String,
    val shape: WireShapeV1,
    val presence: FieldPresenceV1 = FieldPresenceV1.REQUIRED,
)

internal fun required(key: String, shape: WireShapeV1): WireFieldV1 = WireFieldV1(key, shape)
internal fun optional(key: String, shape: WireShapeV1): WireFieldV1 = WireFieldV1(key, shape, FieldPresenceV1.OPTIONAL)

internal sealed interface WireShapeV1 {
    fun validate(value: JsonElement, path: String)
}

internal data object StringShapeV1 : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) { value.asString(path) }
}

internal class CheckedStringShapeV1(
    private val label: String,
    private val check: (String) -> Unit,
) : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) {
        val string = value.asString(path)
        try {
            check(string)
        } catch (error: WireContractException) {
            fail(path, "$label is invalid: ${error.message}")
        }
    }
}

internal data class EnumShapeV1(val tokens: List<String>) : WireShapeV1 {
    private val values = tokens.toSet()
    override fun validate(value: JsonElement, path: String) {
        val token = value.asString(path)
        if (token !in values) fail(path, "unknown or wrong-case enum '$token'")
    }
}

internal data class Int64ShapeV1(
    val minimum: Long = Long.MIN_VALUE,
    val maximum: Long = Long.MAX_VALUE,
    val literal: Long? = null,
) : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) {
        val integer = value.asInt64(path)
        if (integer !in minimum..maximum) fail(path, "int64 outside $minimum..$maximum")
        if (literal != null && integer != literal) fail(path, "expected integer literal $literal")
    }
}

internal data class BooleanShapeV1(val literal: Boolean? = null) : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) {
        val boolean = value.asBoolean(path)
        if (literal != null && boolean != literal) fail(path, "expected boolean literal $literal")
    }
}

internal data class NullableShapeV1(val inner: WireShapeV1) : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) {
        if (value !== JsonNull) inner.validate(value, path)
    }
}

internal data class ObjectShapeV1(val schema: ClosedObjectSchemaV1) : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) {
        schema.validateAndOrder(value.asStrictObject(path), path)
    }
}

internal data class ArrayShapeV1(
    val item: WireShapeV1,
    val minimumSize: Int = 0,
    val maximumSize: Int = Int.MAX_VALUE,
    val crossValidator: (JsonArray, String) -> Unit = { _, _ -> },
) : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) {
        val array = value.asArray(path)
        if (array.size !in minimumSize..maximumSize) fail(path, "array size outside $minimumSize..$maximumSize")
        array.forEachIndexed { index, element -> item.validate(element, "$path[$index]") }
        crossValidator(array, path)
    }
}

internal data object AnyObjectShapeV1 : WireShapeV1 {
    override fun validate(value: JsonElement, path: String) { value.asStrictObject(path) }
}

internal class ClosedObjectSchemaV1(
    val name: String,
    internal val fields: List<WireFieldV1>,
    private val crossValidator: (StrictJsonObjectV1, String) -> Unit = { _, _ -> },
) {
    val keys: List<String> = fields.map { it.key }
    private val allowedKeys = keys.toSet()
    private val requiredKeys = fields.filter { it.presence == FieldPresenceV1.REQUIRED }.mapTo(linkedSetOf()) { it.key }

    init {
        require(keys.size == allowedKeys.size) { "$name contains duplicate schema keys" }
    }

    fun validateAndOrder(value: StrictJsonObjectV1, path: String = name): JsonObject {
        val actualKeys = value.element.keys
        val unknown = actualKeys - allowedKeys
        if (unknown.isNotEmpty()) fail(path, "unknown/extra key(s): ${unknown.joinToString()}")
        val missing = requiredKeys - actualKeys
        if (missing.isNotEmpty()) fail(path, "missing required key(s): ${missing.joinToString()}")
        fields.forEach { field ->
            value[field.key]?.let { field.shape.validate(it, "$path.${field.key}") }
        }
        crossValidator(value, path)
        return JsonObject(linkedMapOf<String, JsonElement>().also { ordered ->
            fields.forEach { field -> value[field.key]?.let { ordered[field.key] = normalizeNested(field.shape, it, "$path.${field.key}") } }
        })
    }
}

private fun normalizeNested(shape: WireShapeV1, value: JsonElement, path: String): JsonElement = when {
    value === JsonNull -> JsonNull
    shape is NullableShapeV1 -> normalizeNested(shape.inner, value, path)
    shape is ObjectShapeV1 -> shape.schema.validateAndOrder(value.asStrictObject(path), path)
    shape is ArrayShapeV1 -> JsonArray(value.asArray(path).mapIndexed { index, child -> normalizeNested(shape.item, child, "$path[$index]") })
    else -> value
}

internal val UuidShapeV1 = CheckedStringShapeV1("UUID") { UuidWireV1.parse(it) }
internal val InstantShapeV1 = CheckedStringShapeV1("InstantWireV1") { InstantWireV1.parse(it) }
internal val DateShapeV1 = CheckedStringShapeV1("DateWireV1") { DateWireV1.parse(it) }
internal val TimeMinuteShapeV1 = CheckedStringShapeV1("TimeMinuteWireV1") { TimeMinuteWireV1.parse(it) }
internal val SemVerShapeV1 = CheckedStringShapeV1("SemVer") { SemVerWireV1.parse(it) }
internal val DigestShapeV1 = CheckedStringShapeV1("SHA-256 digest") { Sha256DigestWireV1.parse(it) }
internal val ZoneIdShapeV1 = CheckedStringShapeV1("IANA ZoneId") { value ->
    parseIanaZoneId(value, "zone_id")
}
internal val NonNegativeInt64ShapeV1 = Int64ShapeV1(minimum = 0)

internal val LocalStampSchemaV1 = ClosedObjectSchemaV1(
    "LocalStampWireV1",
    listOf(
        required("occurred_at_utc", InstantShapeV1),
        required("local_date", DateShapeV1),
        required("zone_id", ZoneIdShapeV1),
        required("utc_offset_minutes", Int64ShapeV1(-1080, 1080)),
    ),
) { value, path ->
    validateLocalStamp(
        InstantWireV1.parse(value.requiredString("occurred_at_utc", path)),
        DateWireV1.parse(value.requiredString("local_date", path)),
        value.requiredString("zone_id", path),
        value.requiredInt64("utc_offset_minutes", path),
        path,
    )
}

internal fun StrictJsonObjectV1.requiredElement(key: String, path: String): JsonElement =
    this[key] ?: fail(path, "missing required key '$key'")

internal fun StrictJsonObjectV1.requiredString(key: String, path: String): String =
    requiredElement(key, path).asString("$path.$key")

internal fun StrictJsonObjectV1.requiredInt64(key: String, path: String): Long =
    requiredElement(key, path).asInt64("$path.$key")

internal fun StrictJsonObjectV1.requiredBoolean(key: String, path: String): Boolean =
    requiredElement(key, path).asBoolean("$path.$key")

internal fun StrictJsonObjectV1.nullableString(key: String, path: String): String? =
    requiredElement(key, path).let { if (it === JsonNull) null else it.asString("$path.$key") }

internal fun StrictJsonObjectV1.nullableInt64(key: String, path: String): Long? =
    requiredElement(key, path).let { if (it === JsonNull) null else it.asInt64("$path.$key") }

internal fun StrictJsonObjectV1.hasNonNull(key: String): Boolean = this[key]?.let { it !== JsonNull } == true

internal fun StrictJsonObjectV1.isNull(key: String): Boolean = this[key] === JsonNull

internal fun StrictJsonObjectV1.hasKey(key: String): Boolean = key in element

internal fun JsonArray.requireStrictlySortedUnique(path: String, comparator: (JsonElement, JsonElement) -> Int) {
    zipWithNext().forEachIndexed { index, (left, right) ->
        if (comparator(left, right) >= 0) fail(path, "array must be strictly sorted and unique at index ${index + 1}")
    }
}

internal fun JsonArray.requireUniqueStringsInCanonicalOrder(path: String, canonical: List<String>, nonEmpty: Boolean = false) {
    if (nonEmpty && isEmpty()) fail(path, "array must be nonempty")
    val values = mapIndexed { index, element -> element.asString("$path[$index]") }
    if (values.distinct().size != values.size) fail(path, "array contains duplicate value")
    val indexes = values.map { value -> canonical.indexOf(value).also { if (it < 0) fail(path, "unknown enum '$value'") } }
    if (indexes.zipWithNext().any { (left, right) -> left >= right }) fail(path, "array is not in canonical order")
}

private fun validateLocalStamp(
    instant: InstantWireV1,
    localDate: DateWireV1,
    zoneId: String,
    utcOffsetMinutes: Long,
    path: String,
) {
    val zone = parseIanaZoneId(zoneId, path)
    val zoned = instant.instant.atZone(zone)
    if (zoned.toLocalDate() != localDate.date) fail(path, "local_date is not coherent with instant and zone")
    val actualOffset = zoned.offset.totalSeconds / 60L
    if (actualOffset != utcOffsetMinutes) fail(path, "utc_offset_minutes does not match ZoneRules")
    if (zoned.offset.totalSeconds % 60 != 0) fail(path, "offset is not minute aligned")
    if (utcOffsetMinutes !in -1080L..1080L) fail(path, "offset outside supported range")
}

private val canonicalIanaZoneIdsV1: Set<String> = ZoneId.getAvailableZoneIds() + "UTC"

private fun parseIanaZoneId(value: String, path: String): ZoneId {
    if (value !in canonicalIanaZoneIdsV1) fail(path, "unknown or non-region IANA ZoneId '$value'")
    return try {
        ZoneId.of(value)
    } catch (_: DateTimeException) {
        fail(path, "unknown IANA ZoneId '$value'")
    }
}

internal fun DateWireV1.requireMonday(path: String) {
    if (date.dayOfWeek != DayOfWeek.MONDAY) fail(path, "week_start_local_date must be Monday")
}
