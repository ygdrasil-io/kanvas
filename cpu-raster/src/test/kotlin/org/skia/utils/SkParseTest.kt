package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.math.SkColorSetRGB

/**
 * Unit tests for [SkParse]. Coverage : each free parser in turn,
 * happy + sad paths.
 */
class SkParseTest {

    @Test
    fun `FindS32 parses signed integers and reports trailing index`() {
        val r = SkParse.FindS32("  -42 px")!!
        assertEquals(-42, r.value)
        // Trailing space + 'px' should be left over.
        assertEquals(5, r.next)
    }

    @Test
    fun `FindS32 returns null on no digits`() {
        assertNull(SkParse.FindS32("   abc"))
    }

    @Test
    fun `FindMSec parses bare integer as ms`() {
        val r = SkParse.FindMSec("500")!!
        assertEquals(500, r.value)
    }

    @Test
    fun `FindMSec parses ms suffix as ms`() {
        val r = SkParse.FindMSec("250ms")!!
        assertEquals(250, r.value)
    }

    @Test
    fun `FindMSec parses s suffix as seconds-times-1000`() {
        val r = SkParse.FindMSec("2s")!!
        assertEquals(2000, r.value)
    }

    @Test
    fun `FindHex parses uppercase and lowercase hex`() {
        assertEquals(0xCAFE, SkParse.FindHex("CAFE")!!.value)
        assertEquals(0xdead, SkParse.FindHex("  dead beef")!!.value)
    }

    @Test
    fun `FindBool true and false case-sensitive`() {
        assertEquals(true, SkParse.FindBool("true")!!.value)
        assertEquals(false, SkParse.FindBool("  false ")!!.value)
        assertNull(SkParse.FindBool("True"))
    }

    @Test
    fun `FindScalar parses decimal and scientific`() {
        assertEquals(3.14f, SkParse.FindScalar("3.14")!!.value)
        assertEquals(-1.5e2f, SkParse.FindScalar("-1.5e2")!!.value)
    }

    @Test
    fun `FindScalars reads count values separated by comma or space`() {
        val out = FloatArray(4)
        val next = SkParse.FindScalars("1 2,3   4", out, 4)
        assertNotNull(next)
        assertEquals(listOf(1f, 2f, 3f, 4f), out.toList())
    }

    @Test
    fun `FindScalars returns null if fewer scalars are available`() {
        val out = FloatArray(4)
        assertNull(SkParse.FindScalars("1 2", out, 4))
    }

    @Test
    fun `FindColor parses hex literals of length 3 6 and 8`() {
        // #RGB → #RRGGBB
        assertEquals(SkColorSetRGB(0xFF, 0xAA, 0x33), SkParse.FindColor("#fa3")!!.value)
        // #RRGGBB
        assertEquals(SkColorSetRGB(0x12, 0x34, 0x56), SkParse.FindColor("#123456")!!.value)
        // #AARRGGBB
        assertEquals(0x80FF0000.toInt(), SkParse.FindColor("#80ff0000")!!.value)
    }

    @Test
    fun `FindColor matches named colors case-insensitively`() {
        assertEquals(SkColorSetRGB(255, 0, 0), SkParse.FindColor("red")!!.value)
        assertEquals(SkColorSetRGB(255, 0, 0), SkParse.FindColor("Red")!!.value)
        assertEquals(SkColorSetRGB(0, 0, 255), SkParse.FindColor("blue")!!.value)
    }

    @Test
    fun `FindColor returns null for unknown name`() {
        assertNull(SkParse.FindColor("not-a-color"))
    }

    @Test
    fun `FindNamedColor reads by length`() {
        assertEquals(SkColorSetRGB(0, 128, 0), SkParse.FindNamedColor("green"))
        assertNull(SkParse.FindNamedColor("zzz"))
    }
}
