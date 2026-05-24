package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.tools.ToolUtils

/**
 * Closing-slice hygiene tests for the text stack — covers the API
 * surface gaps closed in this PR (`SkFont.getPath`, `unicharsToGlyphs`,
 * `getWidth`, `setHinting`) plus rendering tests for Italic / Bold /
 * Mono / Serif Liberation styles that previous slices only resolved
 * but never rasterised.
 *
 * Cf. `archives/MIGRATION_PLAN_TEXT.md` — wraps up the text trajectory before
 * the project pivots back to the main plan.
 */
class SkFontHygieneTest {

    // ---------- API surface: getPath / unicharsToGlyphs / getWidth ----------

    @Test
    fun `getPath on empty typeface returns null`() {
        val f = SkFont(SkTypeface.MakeEmpty(), 20f)
        assertEquals(null, f.getPath(0))
    }

    @Test
    fun `getPath on Liberation Sans for an ASCII glyph returns non-empty path`() {
        val font = ToolUtils.DefaultPortableFont(40f)
        // Resolve 'X' first so we have a real glyph ID.
        val glyphs = ShortArray(1)
        font.unicharsToGlyphs(intArrayOf('X'.code), 1, glyphs)
        assertTrue(glyphs[0] > 0, "'X' must resolve to a non-zero glyph ID, got ${glyphs[0]}")

        val path = font.getPath(glyphs[0].toInt() and 0xFFFF)
        assertNotNull(path)
        assertFalse(path!!.isEmpty(), "'X' glyph path must contain verbs")
    }

    @Test
    fun `unicharsToGlyphs Hello returns five distinct positive glyph IDs`() {
        val font = ToolUtils.DefaultPortableFont(20f)
        val unichars = "Hello".map { it.code }.toIntArray()
        val glyphs = ShortArray(5)
        font.unicharsToGlyphs(unichars, 5, glyphs)

        // All five must resolve to a real glyph (non-zero, non-.notdef).
        for (i in glyphs.indices) {
            assertTrue(glyphs[i] > 0, "char[$i]='${unichars[i].toChar()}' should resolve, got ${glyphs[i]}")
        }
        // 'H', 'e', 'l', 'l', 'o' — positions 2 and 3 are both 'l' so
        // their glyph IDs must match. The other three differ.
        assertEquals(glyphs[2], glyphs[3], "both 'l' must share a glyph ID")
        assertNotEquals(glyphs[0], glyphs[1])
        assertNotEquals(glyphs[1], glyphs[4])
    }

    @Test
    fun `getWidth is consistent with measureText for single ASCII glyph`() {
        val font = ToolUtils.DefaultPortableFont(40f)
        val unichars = intArrayOf('m'.code)
        val glyphs = ShortArray(1)
        font.unicharsToGlyphs(unichars, 1, glyphs)

        val gw = font.getWidth(glyphs[0].toInt() and 0xFFFF)
        val mw = font.measureText("m")
        // Should agree to within sub-pixel rounding.
        assertTrue(
            kotlin.math.abs(gw - mw) < 1f,
            "getWidth ($gw) and measureText ($mw) must agree to <1 px",
        )
    }

    @Test
    fun `getWidth scales with font size`() {
        val small = ToolUtils.DefaultPortableFont(10f)
        val big = ToolUtils.DefaultPortableFont(40f)
        val ch = ShortArray(1).also { small.unicharsToGlyphs(intArrayOf('M'.code), 1, it) }[0].toInt()
        val ws = small.getWidth(ch)
        val wb = big.getWidth(ch)
        val ratio = wb / ws
        assertTrue(ratio in 3.5f..4.5f, "10pt→40pt width ratio should be ~4, got $ratio")
    }

    @Test
    fun `setHinting is stored on SkFont while portable backend still renders`() {
        val font = ToolUtils.DefaultPortableFont(20f)
        // Default = kNormal (matches Skia).
        assertEquals(SkFontHinting.kNormal, font.hinting)
        // Setter mutates.
        font.hinting = SkFontHinting.kFull
        assertEquals(SkFontHinting.kFull, font.hinting)
        // Render with each level — all four should produce non-zero pixels.
        // The portable backend records hinting; this confirms the call
        // doesn't crash and the property round-trips.
        for (h in SkFontHinting.entries) {
            val f = ToolUtils.DefaultPortableFont(24f).apply { hinting = h }
            val bm = SkBitmap(80, 40).apply { eraseColor(0xFFFFFFFF.toInt()) }
            SkCanvas(bm).drawString("Hi", 4f, 28f, f, SkPaint(0xFF000000.toInt()))
            assertTrue(
                bm.pixels.any { it != 0xFFFFFFFF.toInt() },
                "drawString with hinting=$h must paint visible glyphs",
            )
        }
    }

    @Test
    fun `SkFontHinting has four values in upstream order`() {
        assertEquals(
            listOf(SkFontHinting.kNone, SkFontHinting.kSlight, SkFontHinting.kNormal, SkFontHinting.kFull),
            SkFontHinting.entries,
        )
    }

    // ---------- Rendering tests for non-Regular Liberation styles ----------

    private fun renderToPixels(font: SkFont, text: String = "g"): IntArray {
        val bm = SkBitmap(60, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
        SkCanvas(bm).drawString(text, 4f, 38f, font, SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true })
        return bm.pixels.copyOf()
    }

    private fun pixelDifferenceCount(a: IntArray, b: IntArray): Int {
        var diffs = 0
        for (i in a.indices) if (a[i] != b[i]) diffs++
        return diffs
    }

    @Test
    fun `Liberation Sans Italic and Regular at same size produce different pixel patterns`() {
        val regular = SkFont(ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Normal()), 30f)
        val italic = SkFont(ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Italic()), 30f)
        val pxR = renderToPixels(regular)
        val pxI = renderToPixels(italic)
        // Italic and Regular share many BG pixels but the glyph slants
        // differently — expect significant differing-pixel count.
        val diffs = pixelDifferenceCount(pxR, pxI)
        assertTrue(diffs > 30, "Italic vs Regular must differ on >30 pixels (got $diffs)")
    }

    @Test
    fun `Liberation Sans BoldItalic differs from Bold and Italic separately`() {
        val bold = SkFont(ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Bold()), 30f)
        val italic = SkFont(ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Italic()), 30f)
        val boldItalic = SkFont(ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.BoldItalic()), 30f)
        val pxB = renderToPixels(bold)
        val pxI = renderToPixels(italic)
        val pxBI = renderToPixels(boldItalic)
        assertTrue(
            pixelDifferenceCount(pxB, pxBI) > 20 && pixelDifferenceCount(pxI, pxBI) > 20,
            "BoldItalic must differ from Bold and Italic (B↔BI: ${pixelDifferenceCount(pxB, pxBI)}, I↔BI: ${pixelDifferenceCount(pxI, pxBI)})",
        )
    }

    @Test
    fun `Liberation Mono Regular renders visible glyphs`() {
        val mono = SkFont(ToolUtils.CreatePortableTypeface("monospace", SkFontStyle.Normal()), 20f)
        val px = renderToPixels(mono, "code")
        assertTrue(px.any { it != 0xFFFFFFFF.toInt() }, "Mono render must paint visible glyphs")
    }

    @Test
    fun `Liberation Serif Regular renders visible glyphs`() {
        val serif = SkFont(ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Normal()), 20f)
        val px = renderToPixels(serif, "Aa")
        assertTrue(px.any { it != 0xFFFFFFFF.toInt() }, "Serif render must paint visible glyphs")
    }

    @Test
    fun `Liberation Mono Bold and Regular at same size produce different pixel patterns`() {
        val regular = SkFont(ToolUtils.CreatePortableTypeface("monospace", SkFontStyle.Normal()), 24f)
        val bold = SkFont(ToolUtils.CreatePortableTypeface("monospace", SkFontStyle.Bold()), 24f)
        val pxR = renderToPixels(regular, "MM")
        val pxB = renderToPixels(bold, "MM")
        val diffs = pixelDifferenceCount(pxR, pxB)
        assertTrue(diffs > 15, "Mono Bold vs Regular must differ on >15 pixels (got $diffs)")
    }

    @Test
    fun `Three Liberation families with same character render distinctly`() {
        val sans = SkFont(ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Normal()), 30f)
        val mono = SkFont(ToolUtils.CreatePortableTypeface("monospace", SkFontStyle.Normal()), 30f)
        val serif = SkFont(ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Normal()), 30f)
        val pxSans = renderToPixels(sans, "g")
        val pxMono = renderToPixels(mono, "g")
        val pxSerif = renderToPixels(serif, "g")
        // All three pairs must differ on at least 30 pixels — the
        // letter 'g' has very different shapes across the three
        // families.
        assertTrue(pixelDifferenceCount(pxSans, pxMono) > 30, "Sans vs Mono")
        assertTrue(pixelDifferenceCount(pxSans, pxSerif) > 30, "Sans vs Serif")
        assertTrue(pixelDifferenceCount(pxMono, pxSerif) > 30, "Mono vs Serif")
    }
}
