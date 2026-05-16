package org.skia.utils

import org.skia.math.SkColor
import org.skia.math.SkColorSetARGB
import org.skia.math.SkColorSetRGB
import org.skia.math.SkScalar

/**
 * Iso-aligned port of Skia's `SkParse`
 * ([include/utils/SkParse.h](https://github.com/google/skia/blob/main/include/utils/SkParse.h)).
 *
 * A bundle of small string→primitive parsers used by SVG and the
 * legacy XML/animator code path. The C++ side returns a `const char*`
 * pointing to the character past the match; in Kotlin we model the
 * same result with a small [ParseResult] holder that carries the
 * parsed value and the trailing index (so callers can chain
 * `FindScalars` etc.).
 *
 * Every helper accepts a CharSequence-like input (`String`) and
 * skips leading whitespace before parsing. Mirrors the upstream
 * tokenizer at `src/utils/SkParse.cpp`.
 */
public object SkParse {

    /** Result of a successful parse — [value] is the parsed token, [next] is the index in [String] past the match. */
    public data class ParseResult<T>(val value: T, val next: Int)

    private fun skipWS(s: String, start: Int): Int {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun skipSeparator(s: String, start: Int): Int {
        var i = skipWS(s, start)
        if (i < s.length && s[i] == ',') {
            i++
            i = skipWS(s, i)
        }
        return i
    }

    /**
     * Parse a signed 32-bit decimal integer at the start of [str] (after whitespace).
     * Returns `null` if no digit was found.
     */
    public fun FindS32(str: String): ParseResult<Int>? {
        var i = skipWS(str, 0)
        val start = i
        var sign = 1
        if (i < str.length && (str[i] == '+' || str[i] == '-')) {
            if (str[i] == '-') sign = -1
            i++
        }
        val digitsStart = i
        var value = 0L
        while (i < str.length && str[i].isDigit()) {
            value = value * 10 + (str[i] - '0')
            i++
        }
        if (i == digitsStart) return null
        return ParseResult((sign * value).toInt(), i)
    }

    /**
     * Parse a duration in milliseconds (integer with optional `ms`/`s` suffix).
     * Defaults to milliseconds. Returns `null` if no number was found.
     */
    public fun FindMSec(str: String): ParseResult<Int>? {
        val n = FindS32(str) ?: return null
        var i = n.next
        // Optional unit suffix (ms or s)
        if (i + 1 < str.length && str[i] == 'm' && str[i + 1] == 's') {
            return ParseResult(n.value, i + 2)
        }
        if (i < str.length && str[i] == 's') {
            return ParseResult(n.value * 1000, i + 1)
        }
        return n
    }

    /**
     * Parse an unsigned 32-bit hexadecimal number (no `0x` prefix expected).
     * Returns `null` if no hex digit was found.
     */
    public fun FindHex(str: String): ParseResult<Int>? {
        var i = skipWS(str, 0)
        val start = i
        var value = 0L
        while (i < str.length) {
            val c = str[i]
            val d = when {
                c in '0'..'9' -> c - '0'
                c in 'a'..'f' -> 10 + (c - 'a')
                c in 'A'..'F' -> 10 + (c - 'A')
                else -> break
            }
            value = (value shl 4) or d.toLong()
            i++
        }
        if (i == start) return null
        return ParseResult(value.toInt(), i)
    }

    /**
     * Parse `"true"` / `"false"` (case-sensitive, mirrors upstream).
     * Returns `null` on no match.
     */
    public fun FindBool(str: String): ParseResult<Boolean>? {
        val i = skipWS(str, 0)
        if (str.startsWith("true", i)) return ParseResult(true, i + 4)
        if (str.startsWith("false", i)) return ParseResult(false, i + 5)
        return null
    }

    /**
     * Parse a single decimal scalar (optionally signed, optional fractional part,
     * optional exponent). Returns `null` if no number was found.
     */
    public fun FindScalar(str: String): ParseResult<SkScalar>? {
        val i = skipWS(str, 0)
        // Use Kotlin's built-in parser for correctness — scan the longest valid
        // numeric prefix.
        var end = i
        if (end < str.length && (str[end] == '+' || str[end] == '-')) end++
        while (end < str.length && str[end].isDigit()) end++
        if (end < str.length && str[end] == '.') {
            end++
            while (end < str.length && str[end].isDigit()) end++
        }
        if (end < str.length && (str[end] == 'e' || str[end] == 'E')) {
            end++
            if (end < str.length && (str[end] == '+' || str[end] == '-')) end++
            while (end < str.length && str[end].isDigit()) end++
        }
        if (end == i) return null
        val token = str.substring(i, end)
        val v = token.toFloatOrNull() ?: return null
        return ParseResult(v, end)
    }

    /**
     * Parse [count] scalars separated by whitespace and/or commas into [value].
     * Returns the index past the last parsed scalar, or `null` if fewer than
     * [count] scalars were found.
     */
    public fun FindScalars(str: String, value: FloatArray, count: Int): Int? {
        require(count >= 0) { "count must be non-negative" }
        require(value.size >= count) { "value array too small for count=$count" }
        var i = 0
        for (k in 0 until count) {
            if (k > 0) i = skipSeparator(str, i)
            val pr = FindScalar(str.substring(i)) ?: return null
            value[k] = pr.value
            i += pr.next
        }
        return i
    }

    /** Result of parsing a named color — same packed RGB as upstream. */
    private val NAMED_COLORS: Map<String, SkColor> = mapOf(
        "black" to SkColorSetRGB(0, 0, 0),
        "white" to SkColorSetRGB(255, 255, 255),
        "red" to SkColorSetRGB(255, 0, 0),
        "green" to SkColorSetRGB(0, 128, 0),
        "lime" to SkColorSetRGB(0, 255, 0),
        "blue" to SkColorSetRGB(0, 0, 255),
        "yellow" to SkColorSetRGB(255, 255, 0),
        "cyan" to SkColorSetRGB(0, 255, 255),
        "aqua" to SkColorSetRGB(0, 255, 255),
        "magenta" to SkColorSetRGB(255, 0, 255),
        "fuchsia" to SkColorSetRGB(255, 0, 255),
        "gray" to SkColorSetRGB(128, 128, 128),
        "grey" to SkColorSetRGB(128, 128, 128),
        "silver" to SkColorSetRGB(192, 192, 192),
        "maroon" to SkColorSetRGB(128, 0, 0),
        "olive" to SkColorSetRGB(128, 128, 0),
        "navy" to SkColorSetRGB(0, 0, 128),
        "purple" to SkColorSetRGB(128, 0, 128),
        "teal" to SkColorSetRGB(0, 128, 128),
        "orange" to SkColorSetRGB(255, 165, 0),
        "transparent" to SkColorSetARGB(0, 0, 0, 0),
    )

    /**
     * Parse an `#RGB` / `#RRGGBB` / `#AARRGGBB` color literal **or** a
     * named CSS color. Returns `null` on no match. The result is an
     * unpremultiplied ARGB [SkColor].
     */
    public fun FindColor(str: String): ParseResult<SkColor>? {
        val start = skipWS(str, 0)
        if (start >= str.length) return null
        if (str[start] == '#') {
            // Scan up to 8 hex digits after the #
            var end = start + 1
            while (end < str.length && end - start - 1 < 8 && str[end].isHexDigit()) end++
            val hex = str.substring(start + 1, end)
            if (hex.isEmpty()) return null
            val v = hex.toLong(16)
            val color: SkColor = when (hex.length) {
                3 -> {
                    // #RGB → #RRGGBB
                    val r = ((v shr 8) and 0xF).toInt() * 0x11
                    val g = ((v shr 4) and 0xF).toInt() * 0x11
                    val b = (v and 0xF).toInt() * 0x11
                    SkColorSetRGB(r, g, b)
                }
                6 -> SkColorSetRGB(
                    ((v shr 16) and 0xFF).toInt(),
                    ((v shr 8) and 0xFF).toInt(),
                    (v and 0xFF).toInt(),
                )
                8 -> v.toInt()
                else -> return null
            }
            return ParseResult(color, end)
        }
        // Try named color — scan an identifier
        var end = start
        while (end < str.length && (str[end].isLetter())) end++
        if (end == start) return null
        val token = str.substring(start, end).lowercase()
        val color = NAMED_COLORS[token] ?: return null
        return ParseResult(color, end)
    }

    /**
     * Look up a CSS-style named color from a substring of [str]
     * (length [len] from start). Returns `null` if the name does
     * not match a known color.
     */
    public fun FindNamedColor(str: String, len: Int = str.length): SkColor? {
        val token = str.substring(0, len.coerceAtMost(str.length)).lowercase()
        return NAMED_COLORS[token]
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
