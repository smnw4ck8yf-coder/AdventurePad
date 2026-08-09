package com.jamesmoran.adventurepad

/** Small dependency-free JSON reader used for untrusted skin manifests. */
internal object SimpleJson {
    fun parse(source: String): Any? = Reader(source).parse()

    private class Reader(private val source: String) {
        private var offset = 0

        fun parse(): Any? {
            val value = value()
            whitespace()
            require(offset == source.length) { "Unexpected JSON content at offset $offset" }
            return value
        }

        private fun value(): Any? {
            whitespace()
            require(offset < source.length) { "Unexpected end of JSON" }
            return when (source[offset]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> numberValue()
                else -> error("Unexpected JSON token at offset $offset")
            }
        }

        private fun objectValue(): Map<String, Any?> {
            offset++
            whitespace()
            val result = linkedMapOf<String, Any?>()
            if (consume('}')) return result
            while (true) {
                whitespace()
                require(offset < source.length && source[offset] == '"') { "Expected object key at offset $offset" }
                val key = stringValue()
                require(key !in result) { "Duplicate JSON key '$key'" }
                whitespace()
                require(consume(':')) { "Expected ':' after '$key'" }
                result[key] = value()
                whitespace()
                if (consume('}')) return result
                require(consume(',')) { "Expected ',' in object at offset $offset" }
            }
        }

        private fun arrayValue(): List<Any?> {
            offset++
            whitespace()
            val result = mutableListOf<Any?>()
            if (consume(']')) return result
            while (true) {
                result += value()
                whitespace()
                if (consume(']')) return result
                require(consume(',')) { "Expected ',' in array at offset $offset" }
            }
        }

        private fun stringValue(): String {
            require(consume('"'))
            val result = StringBuilder()
            while (offset < source.length) {
                val char = source[offset++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(offset < source.length) { "Incomplete JSON escape" }
                        when (val escaped = source[offset++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(offset + 4 <= source.length) { "Incomplete Unicode escape" }
                                result.append(source.substring(offset, offset + 4).toInt(16).toChar())
                                offset += 4
                            }
                            else -> error("Invalid JSON escape \\$escaped")
                        }
                    }
                    else -> {
                        require(char.code >= 0x20) { "Control character in JSON string" }
                        result.append(char)
                    }
                }
            }
            error("Unterminated JSON string")
        }

        private fun numberValue(): Number {
            val start = offset
            if (source[offset] == '-') offset++
            require(offset < source.length)
            if (source[offset] == '0') offset++ else while (offset < source.length && source[offset].isDigit()) offset++
            var decimal = false
            if (offset < source.length && source[offset] == '.') {
                decimal = true
                offset++
                require(offset < source.length && source[offset].isDigit())
                while (offset < source.length && source[offset].isDigit()) offset++
            }
            if (offset < source.length && source[offset] in "eE") {
                decimal = true
                offset++
                if (offset < source.length && source[offset] in "+-") offset++
                require(offset < source.length && source[offset].isDigit())
                while (offset < source.length && source[offset].isDigit()) offset++
            }
            val token = source.substring(start, offset)
            return if (decimal) token.toDouble() else token.toLong()
        }

        private fun <T> literal(token: String, value: T): T {
            require(source.startsWith(token, offset)) { "Invalid token at offset $offset" }
            offset += token.length
            return value
        }

        private fun whitespace() {
            while (offset < source.length && source[offset].isWhitespace()) offset++
        }

        private fun consume(char: Char): Boolean = if (offset < source.length && source[offset] == char) {
            offset++
            true
        } else false
    }
}
