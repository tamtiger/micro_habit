package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class WireContractException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Strict JSON boundary shared by the exporter, importer and event writer.
 *
 * kotlinx.serialization intentionally keeps the last value for a duplicate object member. The
 * closed WireV1 contract must reject that input before binding, therefore [DuplicateMemberGuard]
 * walks the source bytes first. Schema-specific exact-key and type checks happen after parsing.
 */
object StrictJsonV1 {
    private val json = Json {
        isLenient = false
        allowSpecialFloatingPointValues = false
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    fun parse(source: String): JsonElement {
        try {
            DuplicateMemberGuard(source).validate()
            return json.parseToJsonElement(source)
        } catch (error: WireContractException) {
            throw error
        } catch (error: Exception) {
            throw WireContractException("Invalid JSON: ${error.message}", error)
        }
    }

    fun parseObject(source: String): StrictJsonObjectV1 = parse(source).asStrictObject("root")

    fun encode(element: JsonElement): String = element.toString()
}

@JvmInline
value class StrictJsonObjectV1 internal constructor(internal val element: JsonObject) {
    val keys: List<String> get() = element.keys.toList()

    internal operator fun get(key: String): JsonElement? = element[key]
}

internal fun JsonElement.asStrictObject(path: String): StrictJsonObjectV1 =
    StrictJsonObjectV1(this as? JsonObject ?: fail(path, "expected object"))

internal fun JsonElement.asArray(path: String): JsonArray =
    this as? JsonArray ?: fail(path, "expected array")

internal fun JsonElement.asString(path: String): String {
    val primitive = this as? JsonPrimitive ?: fail(path, "expected string")
    if (!primitive.isString) fail(path, "expected string")
    return primitive.content
}

internal fun JsonElement.asBoolean(path: String): Boolean {
    val primitive = this as? JsonPrimitive ?: fail(path, "expected boolean")
    if (primitive.isString) fail(path, "expected boolean")
    return primitive.booleanOrNull ?: fail(path, "expected boolean")
}

internal fun JsonElement.asInt64(path: String): Long {
    val primitive = this as? JsonPrimitive ?: fail(path, "expected int64")
    if (primitive.isString || !INT64_TOKEN.matches(primitive.content)) fail(path, "expected canonical int64")
    return primitive.content.toLongOrNull() ?: fail(path, "int64 is outside signed range")
}

internal fun fail(path: String, message: String): Nothing =
    throw WireContractException("$path: $message")

private val INT64_TOKEN = Regex("0|-?[1-9][0-9]*")
private const val MAX_JSON_NESTING_DEPTH = 64

private class DuplicateMemberGuard(private val source: String) {
    private var cursor: Int = 0

    fun validate() {
        skipWhitespace()
        scanValue("$", depth = 0)
        skipWhitespace()
        if (cursor != source.length) errorAt("trailing content")
    }

    private fun scanValue(path: String, depth: Int) {
        if (depth > MAX_JSON_NESTING_DEPTH) {
            throw WireContractException("$path: JSON nesting exceeds $MAX_JSON_NESTING_DEPTH levels")
        }
        skipWhitespace()
        when (peek()) {
            '{' -> scanObject(path, depth)
            '[' -> scanArray(path, depth)
            '"' -> scanString()
            't' -> scanLiteral("true")
            'f' -> scanLiteral("false")
            'n' -> scanLiteral("null")
            '-', in '0'..'9' -> scanNumber()
            null -> errorAt("unexpected end of input")
            else -> errorAt("unexpected token '${peek()}'")
        }
    }

    private fun scanObject(path: String, depth: Int) {
        consume('{')
        skipWhitespace()
        if (peek() == '}') {
            cursor++
            return
        }
        val names = HashSet<String>()
        while (true) {
            skipWhitespace()
            if (peek() != '"') errorAt("object member name must be a string")
            val name = scanString()
            if (!names.add(name)) {
                throw WireContractException("$path: duplicate object member '$name'")
            }
            skipWhitespace()
            consume(':')
            scanValue("$path.$name", depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> cursor++
                '}' -> {
                    cursor++
                    return
                }
                else -> errorAt("expected ',' or '}'")
            }
        }
    }

    private fun scanArray(path: String, depth: Int) {
        consume('[')
        skipWhitespace()
        if (peek() == ']') {
            cursor++
            return
        }
        var index = 0
        while (true) {
            scanValue("$path[$index]", depth + 1)
            index++
            skipWhitespace()
            when (peek()) {
                ',' -> cursor++
                ']' -> {
                    cursor++
                    return
                }
                else -> errorAt("expected ',' or ']'")
            }
        }
    }

    private fun scanString(): String {
        consume('"')
        val result = StringBuilder()
        while (true) {
            val current = peek() ?: errorAt("unterminated string")
            cursor++
            when (current) {
                '"' -> return result.toString()
                '\\' -> {
                    val escaped = peek() ?: errorAt("unterminated escape")
                    cursor++
                    when (escaped) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(scanUnicodeEscape())
                        else -> errorAt("invalid escape")
                    }
                }
                else -> {
                    if (current.code < 0x20) errorAt("unescaped control character")
                    result.append(current)
                }
            }
        }
    }

    private fun scanUnicodeEscape(): Char {
        if (cursor + 4 > source.length) errorAt("incomplete unicode escape")
        val token = source.substring(cursor, cursor + 4)
        if (!token.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) errorAt("invalid unicode escape")
        cursor += 4
        return token.toInt(16).toChar()
    }

    private fun scanLiteral(literal: String) {
        if (!source.regionMatches(cursor, literal, 0, literal.length)) errorAt("invalid literal")
        cursor += literal.length
    }

    private fun scanNumber() {
        val start = cursor
        if (peek() == '-') cursor++
        when (peek()) {
            '0' -> cursor++
            in '1'..'9' -> while (peek() in '0'..'9') cursor++
            else -> errorAt("invalid number")
        }
        if (peek() == '.') {
            cursor++
            if (peek() !in '0'..'9') errorAt("invalid fraction")
            while (peek() in '0'..'9') cursor++
        }
        if (peek() == 'e' || peek() == 'E') {
            cursor++
            if (peek() == '+' || peek() == '-') cursor++
            if (peek() !in '0'..'9') errorAt("invalid exponent")
            while (peek() in '0'..'9') cursor++
        }
        if (cursor == start) errorAt("invalid number")
    }

    private fun consume(expected: Char) {
        if (peek() != expected) errorAt("expected '$expected'")
        cursor++
    }

    private fun skipWhitespace() {
        while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') cursor++
    }

    private fun peek(): Char? = source.getOrNull(cursor)

    private fun errorAt(message: String): Nothing =
        throw WireContractException("JSON index $cursor: $message")
}
