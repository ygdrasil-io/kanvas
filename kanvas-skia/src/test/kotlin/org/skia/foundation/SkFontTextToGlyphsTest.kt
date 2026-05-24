package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * S7-B sprint — covers the four [SkFont] helpers promoted from inline
 * GM duplicates: [SkFont.textToGlyphs], [SkFont.getPos],
 * [SkFont.getXPos], and [SkFont.getWidths]. The tests pair the
 * portable OpenType typeface (real glyph metrics) with [SkTypeface.MakeEmpty]
 * (empty-typeface fallback) to guard both the happy path and the
 * "missing glyph → 0" semantics.
 */
class SkFontTextToGlyphsTest {

    @Test
    fun `textToGlyphs on empty string returns empty array`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        assertArrayEquals(IntArray(0), font.textToGlyphs(""))
    }

    @Test
    fun `textToGlyphs returns one glyph per code point for ASCII`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        val glyphs = font.textToGlyphs("ABC")
        assertEquals(3, glyphs.size)
        // OpenType-backed Liberation Sans must resolve A/B/C to non-zero glyph IDs.
        for (i in glyphs.indices) {
            assertNotEquals(0, glyphs[i], "glyph $i should be non-zero, got ${glyphs[i]}")
        }
        // Different code points map to different glyphs.
        assertNotEquals(glyphs[0], glyphs[1])
        assertNotEquals(glyphs[1], glyphs[2])
    }

    @Test
    fun `textToGlyphs on empty typeface returns zero IDs the missing-glyph sentinel`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 16f)
        val glyphs = font.textToGlyphs("ABC")
        assertEquals(3, glyphs.size)
        for (g in glyphs) assertEquals(0, g, "MakeEmpty must yield .notdef (0) glyphs")
    }

    @Test
    fun `textToGlyphs with kGlyphID encoding short-circuits to char codes`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        // Pretend the input string carries raw glyph IDs ; output should
        // be the per-char code values, untouched by the typeface map.
        val raw = "ABC" // A, B, C → 65, 66, 67
        val glyphs = font.textToGlyphs(raw, SkTextEncoding.kGlyphID)
        assertArrayEquals(intArrayOf(65, 66, 67), glyphs)
    }

    @Test
    fun `getXPos on empty glyph array returns empty array`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        assertArrayEquals(FloatArray(0), font.getXPos(IntArray(0)))
    }

    @Test
    fun `getXPos accumulates per-glyph advance widths from origin`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        val glyphs = font.textToGlyphs("ABC")
        val widths = font.getWidths(glyphs)
        val xs = font.getXPos(glyphs, origin = 10f)

        // First entry is the origin, subsequent entries cumulate widths.
        assertEquals(3, xs.size)
        assertEquals(10f, xs[0])
        assertEquals(10f + widths[0], xs[1], 0.001f)
        assertEquals(10f + widths[0] + widths[1], xs[2], 0.001f)
    }

    @Test
    fun `getXPos default origin is zero`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        val glyphs = font.textToGlyphs("X")
        val xs = font.getXPos(glyphs)
        assertEquals(1, xs.size)
        assertEquals(0f, xs[0])
    }

    @Test
    fun `getPos on empty glyph array returns empty array`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        assertEquals(0, font.getPos(IntArray(0)).size)
    }

    @Test
    fun `getPos shares X with getXPos and clamps Y to origin`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        val glyphs = font.textToGlyphs("ABC")
        val origin = SkPoint(5f, 12f)
        val pos = font.getPos(glyphs, origin)
        val xs = font.getXPos(glyphs, origin = 5f)

        assertEquals(3, pos.size)
        for (i in pos.indices) {
            assertEquals(xs[i], pos[i].fX, 0.001f, "pos[$i].x must match getXPos[$i]")
            assertEquals(12f, pos[i].fY, "pos[$i].y must equal origin.y")
        }
    }

    @Test
    fun `getWidths returns per-glyph advance widths matching getWidth`() {
        val font = ToolUtils.DefaultPortableFont(16f)
        val glyphs = font.textToGlyphs("ABC")
        val widths = font.getWidths(glyphs)
        assertEquals(3, widths.size)
        for (i in glyphs.indices) {
            assertEquals(font.getWidth(glyphs[i]), widths[i], 0.001f)
            assertTrue(widths[i] > 0f, "ASCII glyph $i must have positive advance, got ${widths[i]}")
        }
    }

    @Test
    fun `getWidths on empty typeface returns zeros`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 16f)
        val glyphs = intArrayOf(1, 2, 3)
        val widths = font.getWidths(glyphs)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), widths, 0f)
    }
}
