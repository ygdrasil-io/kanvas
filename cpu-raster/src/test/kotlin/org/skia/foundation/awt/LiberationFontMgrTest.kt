@file:Suppress("DEPRECATION")

package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTypeface
import org.skia.core.SkCanvas

/**
 * T4 — verifies that [LiberationFontMgr] loads Liberation TTFs from
 * the classpath for the optional JVM/AWT backend.
 */
class LiberationFontMgrTest {

    @Test
    fun `getDefault returns a typeface with Normal SkFontStyle`() {
        val tf = LiberationFontMgr.getDefault()
        assertNotNull(tf)
        assertEquals(SkFontStyle.Normal(), tf.fontStyle)
    }

    @Test
    fun `getDefault is a singleton`() {
        val a = LiberationFontMgr.getDefault()
        val b = LiberationFontMgr.getDefault()
        assertSame(a, b, "default typeface must be cached")
    }

    @Test
    fun `AWT Liberation manager default routes to Liberation Sans Regular`() {
        val via = LiberationFontMgr.matchFamilyStyle(null, SkFontStyle.Normal())
        assertSame(LiberationFontMgr.getDefault(), via)
    }

    @Test
    fun `matchFamilyStyle null family resolves to default Liberation Sans`() {
        val tf = LiberationFontMgr.matchFamilyStyle(null, SkFontStyle.Normal())
        assertSame(LiberationFontMgr.getDefault(), tf)
    }

    @Test
    fun `matchFamilyStyle resolves family aliases via substring matching`() {
        // Upstream's onMatchFamily uses strstr(name, "ono") / "ans" / "erif".
        val mono = LiberationFontMgr.matchFamilyStyle("monospace", SkFontStyle.Normal())
        val sans = LiberationFontMgr.matchFamilyStyle("sans-serif", SkFontStyle.Normal())
        val serif = LiberationFontMgr.matchFamilyStyle("serif", SkFontStyle.Normal())

        // All three must be distinct objects (different fonts).
        assertNotEquals(mono, sans)
        assertNotEquals(sans, serif)
        assertNotEquals(mono, serif)
    }

    @Test
    fun `matchFamilyStyle returns same instance for repeated identical lookups`() {
        val a = LiberationFontMgr.matchFamilyStyle("sans-serif", SkFontStyle.Bold())
        val b = LiberationFontMgr.matchFamilyStyle("sans-serif", SkFontStyle.Bold())
        assertSame(a, b)
    }

    @Test
    fun `matchFamilyStyle resolves the four Liberation styles per family`() {
        // Mono is the easiest to verify because the metrics differ visibly.
        val r = LiberationFontMgr.matchFamilyStyle("monospace", SkFontStyle.Normal())
        val b = LiberationFontMgr.matchFamilyStyle("monospace", SkFontStyle.Bold())
        val i = LiberationFontMgr.matchFamilyStyle("monospace", SkFontStyle.Italic())
        val bi = LiberationFontMgr.matchFamilyStyle("monospace", SkFontStyle.BoldItalic())

        assertNotEquals(r, b)
        assertNotEquals(r, i)
        assertNotEquals(b, bi)
        assertEquals(SkFontStyle.Normal(), r.fontStyle)
        assertEquals(SkFontStyle.Bold(), b.fontStyle)
        assertEquals(SkFontStyle.Italic(), i.fontStyle)
        assertEquals(SkFontStyle.BoldItalic(), bi.fontStyle)
    }

    @Test
    fun `Liberation Sans Bold has wider advance than Regular at same size`() {
        // Sanity check — bold weights produce wider glyph advances.
        val regular = SkFont(LiberationFontMgr.matchFamilyStyle("sans-serif", SkFontStyle.Normal()), 24f)
        val bold = SkFont(LiberationFontMgr.matchFamilyStyle("sans-serif", SkFontStyle.Bold()), 24f)
        val wReg = regular.measureText("Hello, world")
        val wBold = bold.measureText("Hello, world")
        assertTrue(wBold > wReg, "Bold ($wBold) should be wider than Regular ($wReg)")
    }

    @Test
    fun `Liberation Mono is monospaced — equal advance for any 5-char string`() {
        // Mono fonts have constant glyph advance — sum of 5 different chars
        // should equal 5× single-char width to within rounding.
        val mono = SkFont(LiberationFontMgr.matchFamilyStyle("monospace", SkFontStyle.Normal()), 20f)
        val w1 = mono.measureText("X")
        val w5 = mono.measureText("AbCdE")
        // Allow ~1% slack for sub-pixel rounding.
        val expected = 5f * w1
        val ratio = w5 / expected
        assertTrue(ratio in 0.99f..1.01f, "Mono 5-char advance should equal 5×1-char (got w1=$w1, w5=$w5, ratio=$ratio)")
    }

    @Test
    fun `drawString with Liberation Sans paints non-zero pixels`() {
        val bm = SkBitmap(120, 50)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val canvas = SkCanvas(bm)
        val font = SkFont(LiberationFontMgr.getDefault(), 28f)
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true }

        canvas.drawString("Hello", 6f, 36f, font, paint)

        val anyNonWhite = bm.pixels.any { it != 0xFFFFFFFF.toInt() }
        assertTrue(anyNonWhite, "drawString with Liberation Sans must paint visible glyphs")
    }

    @Test
    fun `drawString with Liberation Serif and Sans produce different pixel patterns`() {
        // Different font families = different glyph outlines = different
        // pixel patterns, even if advance widths happen to coincide.
        fun render(family: String): IntArray {
            val bm = SkBitmap(80, 40)
            bm.eraseColor(0xFFFFFFFF.toInt())
            val canvas = SkCanvas(bm)
            val tf = LiberationFontMgr.matchFamilyStyle(family, SkFontStyle.Normal())
            val font = SkFont(tf, 24f)
            val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true }
            canvas.drawString("g", 4f, 28f, font, paint)
            return bm.pixels.copyOf()
        }
        val sansPixels = render("sans-serif")
        val serifPixels = render("serif")
        // Cheap "are these images different?" check — count differing pixels.
        var diffs = 0
        for (i in sansPixels.indices) if (sansPixels[i] != serifPixels[i]) diffs++
        assertTrue(diffs > 5, "Sans-Serif and Serif renderings should differ on >5 pixels (got $diffs)")
    }

    @Test
    fun `getMetrics on Liberation Sans returns plausible values`() {
        val font = SkFont(LiberationFontMgr.getDefault(), 20f)
        val m = org.skia.foundation.SkFontMetrics()
        val spacing = font.getMetrics(m)
        // Liberation Sans 20pt: ascent ~14-18 (negative), descent ~3-5 (positive).
        assertTrue(m.fAscent < -10f, "fAscent should be < -10 for 20pt Liberation Sans, got ${m.fAscent}")
        assertTrue(m.fDescent in 2f..8f, "fDescent should be in [2,8] for 20pt, got ${m.fDescent}")
        assertTrue(spacing in 18f..30f, "spacing should be in [18,30] for 20pt, got $spacing")
    }
}
