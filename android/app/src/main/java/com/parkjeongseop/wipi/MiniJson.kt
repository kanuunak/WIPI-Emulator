// 작은 JSON 파서(스킨 팩 skin.json용). 순수 Kotlin — org.json은 JVM 단위 테스트에서 쓸 수 없어(android.jar 스텁) 직접 둔다.
// 결과: Map<String, Any?>(순서 유지) / List<Any?> / String / Double / Boolean / null.

package com.parkjeongseop.wipi

class JsonError(message: String) : Exception(message)

object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (!p.end()) p.fail("unexpected text after JSON value")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun end() = i >= s.length

        fun fail(msg: String): Nothing {
            val line = s.substring(0, minOf(i, s.length)).count { it == '\n' } + 1
            throw JsonError("JSON line $line: $msg")
        }

        fun ws() {
            while (i < s.length) {
                when (s[i]) {
                    ' ', '\t', '\n', '\r', '﻿' -> i++
                    else -> return
                }
            }
        }

        fun value(): Any? {
            if (end()) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("unexpected '$c'")
            }
        }

        fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("expected $w")
            i += w.length
            return v
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (!end() && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (end() || s[i] != '"') fail("expected \"key\"")
                val k = str()
                ws()
                if (end() || s[i] != ':') fail("expected ':' after \"$k\"")
                i++
                ws()
                m[k] = value()
                ws()
                if (end()) fail("unexpected end in object")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (!end() && s[i] == ']') { i++; return l }
            while (true) {
                ws()
                l += value()
                ws()
                if (end()) fail("unexpected end in array")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        fun str(): String {
            val b = StringBuilder()
            i++
            while (true) {
                if (end()) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (end()) fail("bad escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad \\u escape")
                                b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad \\u escape"))
                                i += 4
                            }
                            else -> fail("bad escape \\$e")
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
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number '${s.substring(start, i)}'")
        }
    }
}
