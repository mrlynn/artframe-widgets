package com.mrlynn.artframe.widget

/**
 * A small JSON reader that gives plain Kotlin values: Map<String, Any?>, List<Any?>, String,
 * Double, Boolean and null. Widgets use it instead of org.json so the spec and its expressions
 * run (and are tested) on a plain JVM.
 */
object Json {
    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i < text.length) p.fail("Unexpected '${text[p.i]}'")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(msg: String): Nothing {
            val line = s.substring(0, i.coerceAtMost(s.length)).count { it == '\n' } + 1
            throw ParseException("$msg at line $line")
        }

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(depth: Int): Any? {
            if (depth > 64) fail("Nested too deeply")
            if (i >= s.length) fail("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("Unexpected '$c'")
            }
        }

        fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("Unexpected '${s[i]}'")
            i += w.length
            return v
        }

        fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("Expected a key")
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') fail("Expected ':'")
                i++
                ws()
                m[k] = value(depth + 1)
                ws()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            val a = ArrayList<Any?>()
            i++
            ws()
            if (i < s.length && s[i] == ']') { i++; return a }
            while (true) {
                ws()
                a += value(depth + 1)
                ws()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    ']' -> { i++; return a }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        fun str(): String {
            val b = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) fail("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) fail("Unterminated string")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Bad \\u escape")
                                b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("Bad \\u escape"))
                                i += 4
                            }
                            else -> fail("Bad escape \\$e")
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        fun num(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("Bad number")
        }
    }
}
