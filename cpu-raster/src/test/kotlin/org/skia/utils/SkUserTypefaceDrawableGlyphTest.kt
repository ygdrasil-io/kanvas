package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkDrawable
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkRect

/**
 * R-suivi.49 — verifies that drawable-backed glyphs registered via
 * [SkCustomTypefaceBuilder.setGlyph] (the `(unichar, advance, drawable,
 * bounds)` overload) actually render when the canvas runs through the
 * [drawCustomTypefaceText] extension. Pre-R-suivi.49 the path-based
 * [SkUserTypeface.makeTextPath] silently skipped drawable glyphs, so a
 * mixed run would emit nothing for the drawable slots.
 */
class SkUserTypefaceDrawableGlyphTest {

    /**
     * Simple drawable that fills its `(0..1, -1..0)` bounds with the
     * supplied colour. Used to verify the per-glyph save / translate /
     * scale / restore wiring without depending on any heavier
     * rasterisation machinery.
     */
    private class SolidSquareDrawable(private val color: Int) : SkDrawable() {
        override fun onDraw(canvas: SkCanvas) {
            val paint = SkPaint(color).also { it.isAntiAlias = false }
            // Source-unit rect — gets scaled by the per-glyph matrix.
            canvas.drawRect(SkRect.MakeLTRB(0f, -1f, 1f, 0f), paint)
        }

        override fun onGetBounds(): SkRect = SkRect.MakeLTRB(0f, -1f, 1f, 0f)
    }

    private fun unitGlyph(): SkPath =
        SkPathBuilder().addRect(SkRect.MakeLTRB(0f, -1f, 1f, 0f)).detach()

    private fun whiteBitmap(width: Int, height: Int): SkBitmap =
        SkBitmap(width, height).also { it.eraseColor(SK_ColorWHITE) }

    // ─── plain drawable glyph ──────────────────────────────────────────

    @Test
    fun `drawCustomTypefaceText renders a single drawable glyph at the expected pixels`() {
        val drawable = SolidSquareDrawable(SK_ColorBLACK)
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, advance = 1f, drawable = drawable, bounds = SkRect.MakeLTRB(0f, -1f, 1f, 0f))
            .detach()
        val bitmap = whiteBitmap(20, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        surface.canvas.drawCustomTypefaceText("A", x = 0f, y = 12f, font = font, paint = paint)

        // Centre of the 12×12 drawable should be black.
        assertEquals(SK_ColorBLACK, bitmap.getPixel(6, 6), "centre of 'A' drawable")
        // Outside the rect — still white.
        assertEquals(SK_ColorWHITE, bitmap.getPixel(15, 6), "outside drawable horizontally")
        assertEquals(SK_ColorWHITE, bitmap.getPixel(6, 14), "outside drawable vertically")
    }

    @Test
    fun `drawCustomTypefaceText with red drawable produces red pixels`() {
        // Verify the drawable's own paint propagates — i.e. the canvas
        // save / restore is functional and drawable.draw runs to
        // completion.
        val red = SolidSquareDrawable(SK_ColorRED)
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, red, SkRect.MakeLTRB(0f, -1f, 1f, 0f))
            .detach()
        val bitmap = whiteBitmap(16, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        surface.canvas.drawCustomTypefaceText("A", 0f, 12f, font, paint)
        // The drawable's red colour wins over the paint argument — the
        // drawable owns its own colour, paint is only used for path
        // glyphs in a mixed run.
        assertEquals(SK_ColorRED, bitmap.getPixel(6, 6))
    }

    // ─── mixed runs : drawable + path ──────────────────────────────────

    @Test
    fun `drawCustomTypefaceText mixes drawable glyphs with path glyphs`() {
        // 'A' is a drawable ; 'B' is a regular path glyph.
        val tf = SkCustomTypefaceBuilder()
            .setGlyph(
                'A'.code, 1f,
                SolidSquareDrawable(SK_ColorRED),
                SkRect.MakeLTRB(0f, -1f, 1f, 0f),
            )
            .setGlyph('B'.code, 1f, unitGlyph())
            .detach()

        val bitmap = whiteBitmap(36, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        surface.canvas.drawCustomTypefaceText("AB", 0f, 12f, font, paint)

        // 'A' drawable at cols [0, 12) — red.
        assertEquals(SK_ColorRED, bitmap.getPixel(6, 6), "'A' drawable centre")
        // 'B' path at cols [12, 24) — black (paint colour for path glyphs).
        assertEquals(SK_ColorBLACK, bitmap.getPixel(18, 6), "'B' path centre")
        // Past 'B' — white.
        assertEquals(SK_ColorWHITE, bitmap.getPixel(30, 6), "past 'B'")
    }

    @Test
    fun `drawCustomTypefaceText advances cursor by drawable glyph advance`() {
        // Custom advance 2.0 for 'A' (drawable), 1.0 for 'B' (path).
        val tf = SkCustomTypefaceBuilder()
            .setGlyph(
                'A'.code, 2f,
                SolidSquareDrawable(SK_ColorBLACK),
                SkRect.MakeLTRB(0f, -1f, 1f, 0f),
            )
            .setGlyph('B'.code, 1f, unitGlyph())
            .detach()

        val bitmap = whiteBitmap(40, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 10f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        surface.canvas.drawCustomTypefaceText("AB", 0f, 10f, font, paint)

        // 'A' drawable at cols [0, 10) — its 1-unit rect × 10px size.
        assertEquals(SK_ColorBLACK, bitmap.getPixel(5, 5), "'A' drawable")
        // Cursor advanced by 2.0 × 10px = 20 ; gap between [10, 20).
        assertEquals(SK_ColorWHITE, bitmap.getPixel(15, 5), "gap after 'A'")
        // 'B' path at cols [20, 30).
        assertEquals(SK_ColorBLACK, bitmap.getPixel(25, 5), "'B' path")
        // Past 'B' — cursor at 30, no more glyphs.
        assertEquals(SK_ColorWHITE, bitmap.getPixel(35, 5), "past 'B'")
    }

    // ─── fallback path ─────────────────────────────────────────────────

    @Test
    fun `drawCustomTypefaceText falls back to drawString for path-only typefaces`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, unitGlyph())
            .detach()
        // hasDrawableGlyphs should be false → extension delegates to
        // drawString, which uses the legacy makeTextPath route.
        val bitmap = whiteBitmap(16, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        surface.canvas.drawCustomTypefaceText("A", 0f, 12f, font, paint)
        // Same outcome as drawString — black centre.
        assertEquals(SK_ColorBLACK, bitmap.getPixel(6, 6))
    }

    // ─── hasDrawableGlyphs predicate ───────────────────────────────────

    @Test
    fun `hasDrawableGlyphs is false for path-only typefaces`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, unitGlyph())
            .setGlyph('B'.code, 1f, unitGlyph())
            .detach() as SkUserTypeface
        assertFalse(tf.hasDrawableGlyphs(), "path-only typeface has no drawable glyphs")
    }

    @Test
    fun `hasDrawableGlyphs is true when any glyph has a drawable`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, unitGlyph())
            .setGlyph(
                'B'.code, 1f,
                SolidSquareDrawable(SK_ColorBLACK),
                SkRect.MakeLTRB(0f, -1f, 1f, 0f),
            )
            .detach() as SkUserTypeface
        assertTrue(tf.hasDrawableGlyphs(), "at least one drawable glyph → true")
    }

    @Test
    fun `drawDrawableGlyphs renders drawable glyphs even when invoked directly`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph(
                'A'.code, 1f,
                SolidSquareDrawable(SK_ColorBLACK),
                SkRect.MakeLTRB(0f, -1f, 1f, 0f),
            )
            .detach() as SkUserTypeface

        val bitmap = whiteBitmap(16, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        // Direct call (bypassing the extension) — same outcome.
        tf.drawDrawableGlyphs(surface.canvas, "A", 0f, 12f, font, paint)
        assertEquals(SK_ColorBLACK, bitmap.getPixel(6, 6))
    }

    @Test
    fun `drawDrawableGlyphs on empty string is a no-op`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph(
                'A'.code, 1f,
                SolidSquareDrawable(SK_ColorBLACK),
                SkRect.MakeLTRB(0f, -1f, 1f, 0f),
            )
            .detach() as SkUserTypeface

        val bitmap = whiteBitmap(16, 16)
        val surface = SkSurface.MakeRasterDirect(bitmap)
        val font = SkFont(tf, size = 12f)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        tf.drawDrawableGlyphs(surface.canvas, "", 0f, 12f, font, paint)
        // Bitmap untouched.
        for (y in 0 until 16) for (x in 0 until 16) {
            assertEquals(SK_ColorWHITE, bitmap.getPixel(x, y), "($x, $y) untouched")
        }
    }

    @Test
    fun `drawCustomTypefaceText with different drawable colours produces different pixels`() {
        // Sanity check — two surfaces, same text, different drawable
        // colours → different pixels.
        val red = SolidSquareDrawable(SK_ColorRED)
        val blue = SolidSquareDrawable(SkColorSetARGB(0xFF, 0, 0, 255))
        val redTf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, red, SkRect.MakeLTRB(0f, -1f, 1f, 0f))
            .detach()
        val blueTf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, blue, SkRect.MakeLTRB(0f, -1f, 1f, 0f))
            .detach()

        val redBm = whiteBitmap(16, 16)
        val blueBm = whiteBitmap(16, 16)
        SkSurface.MakeRasterDirect(redBm).canvas.drawCustomTypefaceText(
            "A", 0f, 12f, SkFont(redTf, 12f), SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false },
        )
        SkSurface.MakeRasterDirect(blueBm).canvas.drawCustomTypefaceText(
            "A", 0f, 12f, SkFont(blueTf, 12f), SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false },
        )

        assertNotEquals(redBm.getPixel(6, 6), blueBm.getPixel(6, 6))
    }
}
