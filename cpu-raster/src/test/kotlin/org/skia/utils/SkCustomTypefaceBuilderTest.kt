package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkDrawable
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkRect

/**
 * Unit tests for [SkCustomTypefaceBuilder]. Coverage :
 *  - Build a typeface with three path-glyphs ('A', 'B', 'C') and
 *    verify [SkCustomTypefaceBuilder.detach] returns a usable typeface.
 *  - Verify that the produced typeface's unichar-to-glyph map honours
 *    insertion order (glyphId == insertion index).
 *  - Verify drawable-glyph storage (bounds + advance round-trip).
 *  - Verify the single-use contract : every mutator + `detach` throws
 *    after a successful `detach()`.
 *  - Verify the empty-builder contract : `detach()` on an empty
 *    builder throws (we picked exception over `null`, see class doc).
 *  - Verify `setMetrics(metrics, scale)` pre-scales every field.
 */
class SkCustomTypefaceBuilderTest {

    private fun rectPath(l: Float, t: Float, r: Float, b: Float): SkPath =
        SkPathBuilder().addRect(SkRect.MakeLTRB(l, t, r, b)).detach()

    private class DummyDrawable : SkDrawable() {
        override fun onDraw(canvas: SkCanvas) {
            // No-op — the test only needs the storage round-trip.
        }
    }

    @Test
    fun `builds a typeface with three path glyphs and resolves their glyph ids`() {
        val a = rectPath(0f, 0f, 1f, 1f)
        val b = rectPath(0f, 0f, 1f, 2f)
        val c = rectPath(0f, 0f, 1f, 3f)

        val builder = SkCustomTypefaceBuilder()
        builder.setGlyph('A'.code, 1f, a)
            .setGlyph('B'.code, 2f, b)
            .setGlyph('C'.code, 3f, c)

        val tf = builder.detach()
        assertNotNull(tf)
        val user = tf as SkUserTypeface
        assertEquals(3, user.glyphCount())
        assertEquals(0, user.glyphIdForUnichar('A'.code))
        assertEquals(1, user.glyphIdForUnichar('B'.code))
        assertEquals(2, user.glyphIdForUnichar('C'.code))
        assertEquals(-1, user.glyphIdForUnichar('Z'.code))

        // Path round-trip — the typeface returns the path we put in.
        assertSame(a, user.pathForGlyph(0))
        assertSame(b, user.pathForGlyph(1))
        assertSame(c, user.pathForGlyph(2))

        // Advance round-trip.
        assertEquals(1f, user.advanceForGlyph(0))
        assertEquals(2f, user.advanceForGlyph(1))
        assertEquals(3f, user.advanceForGlyph(2))
    }

    @Test
    fun `unicharsToGlyphsInternal maps registered chars and zero-fills unknowns`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, rectPath(0f, 0f, 1f, 1f))
            .setGlyph('B'.code, 1f, rectPath(0f, 0f, 1f, 1f))
            .detach()

        // Drive the SkTypeface contract via reflection-free path —
        // SkUserTypeface exposes the same public surface.
        val text = "AB?"
        val unichars = IntArray(text.length) { text[it].code }
        val glyphs = ShortArray(text.length)

        // Call the internal hook through its public Kotlin name —
        // it has `internal` visibility, callable from same-module tests.
        tf.unicharsToGlyphsInternal(unichars, text.length, glyphs)

        assertEquals(0.toShort(), glyphs[0])  // 'A' -> slot 0
        assertEquals(1.toShort(), glyphs[1])  // 'B' -> slot 1
        assertEquals(0.toShort(), glyphs[2])  // '?' unknown -> .notdef sentinel
    }

    @Test
    fun `drawable glyphs round-trip with bounds`() {
        val drawable = DummyDrawable()
        val bounds = SkRect.MakeLTRB(-5f, -10f, 5f, 0f)

        val tf = SkCustomTypefaceBuilder()
            .setGlyph('X'.code, 4f, drawable, bounds)
            .detach()

        val user = tf as SkUserTypeface
        val gid = user.glyphIdForUnichar('X'.code)
        assertEquals(0, gid)
        assertSame(drawable, user.drawableForGlyph(gid))
        assertNull(user.pathForGlyph(gid))
        val storedBounds = user.boundsForGlyph(gid)
        assertNotNull(storedBounds)
        assertEquals(bounds.left, storedBounds!!.left)
        assertEquals(bounds.top, storedBounds.top)
        assertEquals(bounds.right, storedBounds.right)
        assertEquals(bounds.bottom, storedBounds.bottom)
        assertEquals(4f, user.advanceForGlyph(gid))
    }

    @Test
    fun `re-setting the same unichar overwrites without shifting its slot`() {
        val builder = SkCustomTypefaceBuilder()
        val pathA = rectPath(0f, 0f, 1f, 1f)
        val pathB = rectPath(0f, 0f, 2f, 2f)
        val pathAprime = rectPath(0f, 0f, 9f, 9f)

        builder.setGlyph('A'.code, 1f, pathA)
            .setGlyph('B'.code, 2f, pathB)
            .setGlyph('A'.code, 7f, pathAprime)   // overwrite

        val tf = builder.detach() as SkUserTypeface
        assertEquals(2, tf.glyphCount())
        // 'A' kept its slot 0 — the overwrite did *not* push it to the end.
        assertEquals(0, tf.glyphIdForUnichar('A'.code))
        assertEquals(1, tf.glyphIdForUnichar('B'.code))
        assertSame(pathAprime, tf.pathForGlyph(0))
        assertEquals(7f, tf.advanceForGlyph(0))
    }

    @Test
    fun `using the builder after detach throws on every mutator`() {
        val builder = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, rectPath(0f, 0f, 1f, 1f))
        builder.detach()

        assertThrows(IllegalStateException::class.java) {
            builder.setGlyph('B'.code, 1f, rectPath(0f, 0f, 1f, 1f))
        }
        assertThrows(IllegalStateException::class.java) {
            builder.setGlyph('B'.code, 1f, DummyDrawable(), SkRect.MakeEmpty())
        }
        assertThrows(IllegalStateException::class.java) {
            builder.setMetrics(SkFontMetrics())
        }
        assertThrows(IllegalStateException::class.java) {
            builder.setFontStyle(SkFontStyle.Bold())
        }
        assertThrows(IllegalStateException::class.java) {
            builder.detach()
        }
    }

    @Test
    fun `detach on empty builder throws`() {
        val builder = SkCustomTypefaceBuilder()
        assertThrows(IllegalStateException::class.java) { builder.detach() }
    }

    @Test
    fun `font style propagates from builder to produced typeface`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, rectPath(0f, 0f, 1f, 1f))
            .setFontStyle(SkFontStyle.BoldItalic())
            .detach()

        assertEquals(SkFontStyle.kBold_Weight, tf.fontStyle.weight)
        assertEquals(SkFontStyle.Slant.kItalic_Slant, tf.fontStyle.slant)
    }

    @Test
    fun `setMetrics applies the uniform scale to every field`() {
        val src = SkFontMetrics().apply {
            fAscent = -10f
            fDescent = 4f
            fLeading = 1f
            fAvgCharWidth = 5f
            fMaxCharWidth = 6f
            fXHeight = 8f
            fCapHeight = 9f
            fUnderlineThickness = 1f
            fUnderlinePosition = 2f
            fStrikeoutThickness = 1f
            fStrikeoutPosition = 3f
        }
        val tf = SkCustomTypefaceBuilder()
            .setMetrics(src, scale = 2f)
            .setGlyph('A'.code, 1f, rectPath(0f, 0f, 4f, 4f))
            .detach()

        val out = SkFontMetrics()
        tf.getMetricsInternal(out, size = 1f)
        // After detach the top/bottom/xMin/xMax fields are derived from
        // the union of glyph bounds — but ascent/descent/leading/...
        // still come from the scaled user-supplied metrics.
        assertEquals(-20f, out.fAscent)
        assertEquals(8f, out.fDescent)
        assertEquals(2f, out.fLeading)
        assertEquals(10f, out.fAvgCharWidth)
        assertEquals(12f, out.fMaxCharWidth)
        assertEquals(16f, out.fXHeight)
        assertEquals(18f, out.fCapHeight)
        // Top / bottom inherit the bounds of the single glyph (0,0,4,4).
        assertEquals(0f, out.fTop)
        assertEquals(4f, out.fBottom)
    }

    @Test
    fun `family names are unique across produced typefaces`() {
        val tf1 = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, rectPath(0f, 0f, 1f, 1f))
            .detach() as SkUserTypeface
        val tf2 = SkCustomTypefaceBuilder()
            .setGlyph('A'.code, 1f, rectPath(0f, 0f, 1f, 1f))
            .detach() as SkUserTypeface

        assertTrue(tf1.familyName.startsWith("Custom-"))
        assertTrue(tf2.familyName.startsWith("Custom-"))
        assertFalse(tf1.familyName == tf2.familyName)
    }
}
