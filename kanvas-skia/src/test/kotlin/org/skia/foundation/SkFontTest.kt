package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.foundation.opentype.OpenTypeTypeface
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Slice T1 + T2 — covers the no-op rendering surface (`drawString`) and
 * the portable OpenType measurement surface (`measureText`, `getMetrics`).
 */
class SkFontTest {

    // ---------- T1: API construction ---------------------------------------

    @Test
    fun `default ctor produces empty typeface size 12 upright`() {
        val f = SkFont()
        assertEquals(12f, f.size)
        assertEquals(1f, f.scaleX)
        assertEquals(0f, f.skewX)
        assertEquals(SkFont.Edging.kAntiAlias, f.edging)
        assertFalse(f.isSubpixel)
        // typeface defaults to MakeEmpty()
        assertEquals(SkTypeface.MakeEmpty(), f.typeface)
    }

    @Test
    fun `ctor with typeface and size`() {
        val tf = SkTypeface.MakeEmpty()
        val f = SkFont(tf, 24f)
        assertEquals(tf, f.typeface)
        assertEquals(24f, f.size)
    }

    @Test
    fun `ctor with typeface size scaleX skewX`() {
        val tf = SkTypeface.MakeEmpty()
        val f = SkFont(tf, 32f, 0.5f, 0.25f)
        assertEquals(0.5f, f.scaleX)
        assertEquals(0.25f, f.skewX)
    }

    @Test
    fun `copy ctor mirrors all properties`() {
        val f = SkFont(SkTypeface.MakeEmpty(), 20f, 1.5f, 0.2f).also {
            it.edging = SkFont.Edging.kAlias
            it.isSubpixel = true
            it.isLinearMetrics = true
            it.isEmbolden = true
            it.isBaselineSnap = true
        }
        val g = SkFont(f)
        assertNotSame(f, g)
        assertEquals(f.size, g.size)
        assertEquals(f.scaleX, g.scaleX)
        assertEquals(f.skewX, g.skewX)
        assertEquals(f.edging, g.edging)
        assertEquals(f.isSubpixel, g.isSubpixel)
        assertEquals(f.isLinearMetrics, g.isLinearMetrics)
        assertEquals(f.isEmbolden, g.isEmbolden)
        assertEquals(f.isBaselineSnap, g.isBaselineSnap)
    }

    @Test
    fun `setters mutate in place mirroring SkFont's mutable upstream surface`() {
        val f = SkFont()
        f.size = 50f
        f.scaleX = 2f
        f.skewX = -0.3f
        f.edging = SkFont.Edging.kSubpixelAntiAlias
        f.isSubpixel = true

        assertEquals(50f, f.size)
        assertEquals(2f, f.scaleX)
        assertEquals(-0.3f, f.skewX)
        assertEquals(SkFont.Edging.kSubpixelAntiAlias, f.edging)
        assertTrue(f.isSubpixel)
    }

    // ---------- T3: SkCanvas drawString rendering --------------------------

    @Test
    fun `drawString with empty typeface is a no-op T3 base SkTypeface returns null path`() {
        // The base SkTypeface (returned by MakeEmpty) has no glyph engine,
        // so makeTextPath() returns null and drawString must do nothing.
        val bm = SkBitmap(8, 8)
        bm.eraseColor(0xFFFF0000.toInt())
        val before = bm.pixels.copyOf()

        val canvas = SkCanvas(bm)
        val font = SkFont(SkTypeface.MakeEmpty(), 16f)
        val paint = SkPaint(0xFF000000.toInt())

        canvas.drawString("hello", 0f, 0f, font, paint)
        canvas.drawSimpleText("hello", 5, SkTextEncoding.kUTF8, 0f, 0f, font, paint)

        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `drawString with empty string is no-op even on portable OpenType typeface`() {
        val bm = SkBitmap(16, 16)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val before = bm.pixels.copyOf()
        val canvas = SkCanvas(bm)
        val font = ToolUtils.DefaultPortableFont(20f)
        val paint = SkPaint(0xFF000000.toInt())
        canvas.drawString("", 0f, 0f, font, paint)
        canvas.drawSimpleText("", 0, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `drawString with portable OpenType typeface paints non-zero pixels onto white canvas`() {
        // Sanity: drawing black text on a white BG must leave at least one
        // pixel that isn't pure white.
        val bm = SkBitmap(80, 40)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val canvas = SkCanvas(bm)
        val font = ToolUtils.DefaultPortableFont(24f)
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true }

        canvas.drawString("Hello", 4f, 28f, font, paint)

        val anyNonWhite = bm.pixels.any { it != 0xFFFFFFFF.toInt() }
        assertTrue(anyNonWhite, "drawString must paint at least one non-white pixel")
    }

    @Test
    fun `drawString respects baseline y position`() {
        // Text drawn at baseline y=10 should leave pixels in row band [3..15]
        // (typical 24pt cap-height ~16-18px above baseline). Text drawn at
        // baseline y=70 should leave pixels far below — verifying the y
        // parameter actually positions the path.
        fun rowsTouchedAtBaseline(yBase: Float): IntRange {
            val bm = SkBitmap(80, 80)
            bm.eraseColor(0xFFFFFFFF.toInt())
            val canvas = SkCanvas(bm)
            val font = ToolUtils.DefaultPortableFont(24f)
            val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true }
            canvas.drawString("X", 5f, yBase, font, paint)
            var min = bm.height; var max = -1
            for (row in 0 until bm.height) {
                for (col in 0 until bm.width) {
                    if (bm.pixels[row * bm.width + col] != 0xFFFFFFFF.toInt()) {
                        if (row < min) min = row
                        if (row > max) max = row
                    }
                }
            }
            return min..max
        }
        val low = rowsTouchedAtBaseline(20f)
        val high = rowsTouchedAtBaseline(70f)
        assertTrue(low.last < high.first, "low band $low must be entirely above high band $high")
    }

    @Test
    fun `drawString honours paint color`() {
        val bm = SkBitmap(60, 40)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val canvas = SkCanvas(bm)
        val font = ToolUtils.DefaultPortableFont(28f)
        // Solid red, AA off → glyph interior pixels should be exactly red.
        val paint = SkPaint(0xFFFF0000.toInt()).also { it.isAntiAlias = false }

        canvas.drawString("X", 4f, 30f, font, paint)

        val red = 0xFFFF0000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val anyRed = bm.pixels.any { it == red }
        val anyOther = bm.pixels.any { it != red && it != white }
        assertTrue(anyRed, "AA-off rendering must produce solid-red interior pixels")
        assertEquals(false, anyOther, "AA-off must produce only paint or BG colour")
    }

    @Test
    fun `drawString respects the canvas CTM`() {
        // Translate before drawString — the painted region should shift.
        fun touchedColMin(translateX: Float): Int {
            val bm = SkBitmap(120, 40)
            bm.eraseColor(0xFFFFFFFF.toInt())
            val canvas = SkCanvas(bm)
            canvas.translate(translateX, 0f)
            val font = ToolUtils.DefaultPortableFont(20f)
            val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true }
            canvas.drawString("A", 0f, 25f, font, paint)
            for (col in 0 until bm.width) {
                for (row in 0 until bm.height) {
                    if (bm.pixels[row * bm.width + col] != 0xFFFFFFFF.toInt()) return col
                }
            }
            return -1
        }
        val a = touchedColMin(5f)
        val b = touchedColMin(50f)
        assertTrue(a in 0..20, "small translate → glyph near left, got col=$a")
        assertTrue(b in 40..70, "large translate → glyph shifted right, got col=$b")
        assertTrue(b > a + 30, "translate must shift glyph by ~45 pixels (got $a → $b)")
    }

    @Test
    fun `drawString forwards raw text to typeface path builder without implicit shaping`() {
        val typeface = RecordingTypeface()
        val bm = SkBitmap(32, 16)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val canvas = SkCanvas(bm)

        canvas.drawString("fi", 2f, 10f, SkFont(typeface, 12f), SkPaint(0xFF000000.toInt()))

        assertEquals(listOf("fi"), typeface.requestedTexts)
    }

    // ---------- T1: ToolUtils helpers --------------------------------------

    @Test
    fun `DefaultPortableTypeface is non-null and stable across calls`() {
        val a = ToolUtils.DefaultPortableTypeface()
        val b = ToolUtils.DefaultPortableTypeface()
        // Singleton today (T4 may load from TTF resource — still a singleton).
        assertEquals(a, b)
    }

    @Test
    fun `DefaultPortableFont returns SkFont with portable typeface and given size`() {
        val f = ToolUtils.DefaultPortableFont(18f)
        assertEquals(18f, f.size)
        assertEquals(ToolUtils.DefaultPortableTypeface(), f.typeface)
    }

    @Test
    fun `DefaultPortableFont default size is 12 matching upstream`() {
        assertEquals(12f, ToolUtils.DefaultPortableFont().size)
    }

    // ---------- T2: measureText with portable OpenType typeface ------------

    @Test
    fun `measureText returns 0 for empty string regardless of typeface`() {
        val f = ToolUtils.DefaultPortableFont(16f)
        assertEquals(0f, f.measureText(""))
    }

    @Test
    fun `measureText returns 0 on empty typeface T1 fallback`() {
        val f = SkFont(SkTypeface.MakeEmpty(), 16f)
        assertEquals(0f, f.measureText("X"))
    }

    @Test
    fun `measureText is monotonic with string length portable OpenType backend`() {
        val f = ToolUtils.DefaultPortableFont(20f)
        val w1 = f.measureText("X")
        val w2 = f.measureText("XX")
        val w3 = f.measureText("XXXXXXXX")
        assertTrue(w1 > 0f, "single char must have positive advance, got $w1")
        assertTrue(w2 > w1, "two X must be wider than one (got $w1, $w2)")
        assertTrue(w3 > w2, "eight X must be wider than two (got $w2, $w3)")
    }

    @Test
    fun `measureText scales roughly linearly with font size`() {
        val text = "ABCDEFGH"
        val w12 = ToolUtils.DefaultPortableFont(12f).measureText(text)
        val w24 = ToolUtils.DefaultPortableFont(24f).measureText(text)
        // 24pt roughly doubles 12pt, allowing raster/outline rounding drift.
        val ratio = w24 / w12
        assertTrue(ratio in 1.8f..2.2f, "expected ratio ~2 for size 12→24, got $ratio")
    }

    @Test
    fun `measureText fills tight visual bounds when bounds out-param is provided`() {
        val f = ToolUtils.DefaultPortableFont(40f)
        val bounds = SkRect.MakeEmpty()
        val advance = f.measureText("Hello", bounds = bounds)
        assertTrue(advance > 0f)
        // Visual bbox of "Hello" with a 40pt font: expect height in roughly [10, 50] pixels
        // (cap-height area, no descenders), width comparable to advance.
        assertTrue(bounds.width() > 0f, "width must be positive, got ${bounds.width()}")
        assertTrue(bounds.height() > 0f, "height must be positive, got ${bounds.height()}")
        assertTrue(bounds.height() < 60f, "visual height should be < 60 for a 40pt font, got ${bounds.height()}")
    }

    // ---------- T2: getMetrics ---------------------------------------------

    @Test
    fun `getMetrics on empty typeface zeros everything`() {
        val f = SkFont(SkTypeface.MakeEmpty(), 16f)
        val m = SkFontMetrics()
        // Pre-fill to detect zeroing.
        m.fAscent = 999f; m.fDescent = 999f; m.fFlags = 999
        val spacing = f.getMetrics(m)
        assertEquals(0f, spacing)
        assertEquals(0f, m.fAscent)
        assertEquals(0f, m.fDescent)
        assertEquals(0, m.fFlags)
    }

    @Test
    fun `getMetrics ascent is negative descent positive portable OpenType backend`() {
        val f = ToolUtils.DefaultPortableFont(20f)
        val m = SkFontMetrics()
        val spacing = f.getMetrics(m)
        // Skia y-down: ascent is above baseline → negative, descent below → positive.
        assertTrue(m.fAscent < 0f, "fAscent must be negative (Skia y-down), got ${m.fAscent}")
        assertTrue(m.fDescent > 0f, "fDescent must be positive, got ${m.fDescent}")
        assertTrue(m.fLeading >= 0f, "fLeading must be non-negative, got ${m.fLeading}")
        assertTrue(spacing > 0f, "recommended line spacing must be positive, got $spacing")
    }

    @Test
    fun `getMetrics scales with font size`() {
        val small = SkFontMetrics().also { ToolUtils.DefaultPortableFont(10f).getMetrics(it) }
        val big = SkFontMetrics().also { ToolUtils.DefaultPortableFont(40f).getMetrics(it) }
        // 4× the size → ~4× the descent magnitude (within hinting noise).
        val ratio = big.fDescent / small.fDescent
        assertTrue(ratio in 3f..5f, "expected fDescent ratio ~4 for 10→40pt, got $ratio")
    }

    @Test
    fun `getMetrics sets underline and strikeout valid flags portable OpenType backend`() {
        val f = ToolUtils.DefaultPortableFont(20f)
        val m = SkFontMetrics()
        f.getMetrics(m)
        assertTrue((m.fFlags and SkFontMetrics.kUnderlineThicknessIsValid_Flag) != 0)
        assertTrue((m.fFlags and SkFontMetrics.kUnderlinePositionIsValid_Flag) != 0)
        assertTrue((m.fFlags and SkFontMetrics.kStrikeoutThicknessIsValid_Flag) != 0)
        assertTrue((m.fFlags and SkFontMetrics.kStrikeoutPositionIsValid_Flag) != 0)
    }

    @Test
    fun `getSpacing returns same value as getMetrics ignoring out`() {
        val f = ToolUtils.DefaultPortableFont(22f)
        val m = SkFontMetrics()
        val byOut = f.getMetrics(m)
        val convenience = f.getSpacing()
        assertEquals(byOut, convenience)
    }

    @Test
    fun `DefaultPortableTypeface is OpenType backed`() {
        assertTrue(ToolUtils.DefaultPortableTypeface() is OpenTypeTypeface)
    }

    private class RecordingTypeface : SkTypeface() {
        val requestedTexts = mutableListOf<String>()

        override fun makeTextPath(
            text: String,
            x: Float,
            y: Float,
            size: Float,
            scaleX: Float,
            skewX: Float,
            isSubpixel: Boolean,
        ): SkPath? {
            requestedTexts += text
            return SkPathBuilder()
                .addRect(SkRect.MakeLTRB(x, y - size, x + text.length * scaleX, y))
                .detach()
        }
    }
}
