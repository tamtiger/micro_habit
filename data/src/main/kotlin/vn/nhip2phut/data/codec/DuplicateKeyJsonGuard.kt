package vn.nhip2phut.data.codec

class DuplicateJsonKeyException(key: String) : IllegalArgumentException("Duplicate JSON object key: $key")

object DuplicateKeyJsonGuard {
    fun requireNoDuplicateObjectKeys(json: String) {
        Parser(json).parse()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse() {
            skipWhitespace()
            parseValue()
            skipWhitespace()
            require(index == source.length) { "Unexpected trailing JSON content." }
        }

        private fun parseValue() {
            skipWhitespace()
            when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> consumeLiteral("true")
                'f' -> consumeLiteral("false")
                'n' -> consumeLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected JSON token at index $index.")
            }
        }

        private fun parseObject() {
            expect('{')
            val keys = mutableSetOf<String>()
            skipWhitespace()
            if (consumeIf('}')) return
            while (true) {
                skipWhitespace()
                val key = parseString()
                if (!keys.add(key)) throw DuplicateJsonKeyException(key)
                skipWhitespace()
                expect(':')
                parseValue()
                skipWhitespace()
                if (consumeIf('}')) return
                expect(',')
            }
        }

        private fun parseArray() {
            expect('[')
            skipWhitespace()
            if (consumeIf(']')) return
            while (true) {
                parseValue()
                skipWhitespace()
                if (consumeIf(']')) return
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                when (char) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(parseEscape())
                    else -> builder.append(char)
                }
            }
            error("Unterminated JSON string.")
        }

        private fun parseEscape(): Char {
            require(index < source.length) { "Unterminated JSON escape." }
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> error("Invalid JSON escape at index ${index - 1}.")
            }
        }

        private fun parseUnicodeEscape(): Char {
            require(index + 4 <= source.length) { "Incomplete unicode escape." }
            val hex = source.substring(index, index + 4)
            require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid unicode escape." }
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun parseNumber() {
            if (consumeIf('-')) Unit
            if (consumeIf('0')) {
                require(peekOrNull()?.isDigit() != true) { "Leading zero JSON number." }
            } else {
                require(peekOrNull()?.isDigit() == true) { "Invalid JSON number." }
                while (peekOrNull()?.isDigit() == true) index++
            }
            if (consumeIf('.')) {
                require(peekOrNull()?.isDigit() == true) { "Invalid JSON fraction." }
                while (peekOrNull()?.isDigit() == true) index++
            }
            if (peekOrNull() == 'e' || peekOrNull() == 'E') {
                index++
                if (peekOrNull() == '+' || peekOrNull() == '-') index++
                require(peekOrNull()?.isDigit() == true) { "Invalid JSON exponent." }
                while (peekOrNull()?.isDigit() == true) index++
            }
        }

        private fun consumeLiteral(literal: String) {
            require(source.startsWith(literal, index)) { "Expected $literal at index $index." }
            index += literal.length
        }

        private fun skipWhitespace() {
            while (peekOrNull() == ' ' || peekOrNull() == '\n' || peekOrNull() == '\r' || peekOrNull() == '\t') index++
        }

        private fun expect(expected: Char) {
            require(peek() == expected) { "Expected $expected at index $index." }
            index++
        }

        private fun consumeIf(expected: Char): Boolean {
            if (peekOrNull() != expected) return false
            index++
            return true
        }

        private fun peek(): Char = peekOrNull() ?: error("Unexpected end of JSON.")
        private fun peekOrNull(): Char? = source.getOrNull(index)
    }
}

