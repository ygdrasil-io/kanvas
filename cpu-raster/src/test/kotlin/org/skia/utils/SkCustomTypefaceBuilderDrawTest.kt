package org.skia.utils


import org.skia.math.between
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTypeface
import org.skia.math.SkRect

/**
 * R-suivi.46 — verifies that [SkCustomTypefaceBuilder]'s produced
 * typeface routes through `SkCanvas.drawString` → `SkFont.makeTextPath`
 * → `SkUserTypeface.makeTextPath` and emits actual pixels.
 *
 * Strategy : build a typeface whose 'A', 'B', 'C' glyphs are simple
 * solid rectangles (full source-unit square `(0, -1, 1, 0)` — the
 * `-1..0` y-range mimics a real glyph whose origin is at the baseline,
 * so when we drawString at `(x, y=size)` the rectangles end up at
 * device rows `[0, size)`). Each glyph carries advance `1f`, so at
 * `size = 12` consecutive glyphs land at columns `[0, 12)`, `[12, 24)`,
 * `[24, 36)`.
 *
 * Each test rasterises into a fresh white bitmap wrapped by
 * [SkSurface.MakeRasterDirect] (so we keep a handle on the pixels)
 * and checks one pixel inside the expected glyph footprint plus one
 * pixel outside.
 */
class SkCustomTypefaceBuilderDrawTest {

    /**
     * Build a unit square path covering `(0, -1)..(1, 0)` — fits the
     * "origin on baseline, ascends to y = -1, descends to y = 0"
     * convention so positive baseline y produces visible pixels.
     */
    private fun unitGlyph(): SkPath =
        SkPathBuilder().addRect(SkRect.MakeLTRB(0f, -1f, 1f, 0f)).detach()

    private fun customABC(): SkTypeface {
        return SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, unitGlyph())
            .setGlyph('B'.code, 1f, unitGlyph())
            .setGlyph('C'.code, 1f, unitGlyph())
            .detach()
    }

    private fun whiteBitmap(width: Int, height: Int): SkBitmap =
        SkBitmap(width, height).also { it.eraseColor(SK_ColorWHITE) }

    @Test
    fun `drawString routes through SkUserTypeface and rasterises the stored glyph paths`() {
        // Surface big enough for "ABC" at size 12 — three 12x12 boxes
        // sitting on baseline y = 12, so rows [0, 12), cols [0, 36).
        val bitmap = whiteBitmap(48, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)

        val font = SkFont(customABC(), size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        surface.canvas.drawString("ABC", x = 0f, y = 12f, font = font, paint = paint)

        // Centre of each glyph box should now be black.
        assertEquals(SK_ColorBLACK, bitmap.getPixel(6, 6), "centre of 'A'")
        assertEquals(SK_ColorBLACK, bitmap.getPixel(18, 6), "centre of 'B'")
        assertEquals(SK_ColorBLACK, bitmap.getPixel(30, 6), "centre of 'C'")
        // Right of the rendered run should still be white.
        assertEquals(SK_ColorWHITE, bitmap.getPixel(42, 6), "past 'C' stays white")
        // Below the baseline (y >= 12) should stay white — our glyphs
        // span y = [-1, 0] in source units, mapped to device [0, 12).
        assertEquals(SK_ColorWHITE, bitmap.getPixel(6, 13), "below baseline stays white")
    }

    @Test
    fun `drawString with different paint colour produces different pixels`() {
        val font = SkFont(customABC(), size = 12f)

        // Surface #1 : black paint.
        val b1 = whiteBitmap(24, 16)
        val s1 = SkSurface.MakeRasterDirect(b1)
        s1.canvas.drawString(
            "A", 0f, 12f, font,
            SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false },
        )
        val blackPixel = b1.getPixel(6, 6)
        assertEquals(SK_ColorBLACK, blackPixel)

        // Surface #2 : red paint, same draw.
        val b2 = whiteBitmap(24, 16)
        val s2 = SkSurface.MakeRasterDirect(b2)
        s2.canvas.drawString(
            "A", 0f, 12f, font,
            SkPaint(SK_ColorRED).also { it.isAntiAlias = false },
        )
        val redPixel = b2.getPixel(6, 6)
        assertEquals(SK_ColorRED, redPixel)

        // Different paint → different rasterised colour, same geometry.
        assertNotEquals(blackPixel, redPixel)
    }

    @Test
    fun `drawString translates each glyph by its accumulated advance`() {
        // Use distinct advances to check the cursor-translation logic :
        // 'A' advance 2, 'B' advance 1. Glyph rect is still 1×1 in src
        // units. At size 10 :
        //  - 'A' rect lands at cols [0, 10), advance moves cursor to 20 ;
        //  - 'B' rect lands at cols [20, 30).
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 2f, unitGlyph())
            .setGlyph('B'.code, 1f, unitGlyph())
            .detach()
        val bitmap = whiteBitmap(40, 12)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 10f)
        surface.canvas.drawString(
            "AB", 0f, 10f, font,
            SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false },
        )

        // 'A' centre at col 5 — black.
        assertEquals(SK_ColorBLACK, bitmap.getPixel(5, 5), "centre of 'A'")
        // Gap between cursor 10 (end of 'A' rect) and 20 (start of 'B')
        // — white, because 'A''s advance pushed the cursor past the rect.
        assertEquals(SK_ColorWHITE, bitmap.getPixel(15, 5), "gap between glyphs")
        // 'B' centre at col 25 — black.
        assertEquals(SK_ColorBLACK, bitmap.getPixel(25, 5), "centre of 'B'")
    }

    @Test
    fun `empty string is a no-op`() {
        val bitmap = whiteBitmap(8, 8)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(customABC(), size = 4f)
        surface.canvas.drawString(
            "", 0f, 4f, font,
            SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false },
        )
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(SK_ColorWHITE, bitmap.getPixel(x, y), "pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `unknown unichar falls back to notdef (glyph 0) advance`() {
        // '?' is not registered. The unicharsToGlyphsInternal contract
        // says unknown → 0, so '?' renders glyph 0 ('A''s rect). This
        // is the .notdef substitution behaviour — we don't actually care
        // *which* visual lands, just that something is drawn (i.e. the
        // pipeline didn't silently skip the glyph).
        val bitmap = whiteBitmap(16, 12)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(customABC(), size = 8f)
        surface.canvas.drawString(
            "?", 0f, 8f, font,
            SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false },
        )
        // Centre of the rendered .notdef rect — non-white.
        assertTrue(
            bitmap.getPixel(4, 4) != SK_ColorWHITE,
            "unknown glyph still renders the .notdef substitute",
        )
    }
}
