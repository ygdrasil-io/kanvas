package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GpuFallbackChainTest {

    @Test
    fun `fallback chain splits subruns when fonts differ`() {
        val glyphs = listOf(
            GpuFallbackGlyphPlan(0, "Arial", "Arial", GpuFallbackReason.MissingGlyph),
            GpuFallbackGlyphPlan(1, "Arial", "Noto", GpuFallbackReason.MissingGlyph),
            GpuFallbackGlyphPlan(2, "Noto", "Noto", GpuFallbackReason.MissingGlyph),
        )
        val splits = GpuFallbackBatchPolicy.splitSubruns(glyphs, maxFallbackDepth = 3)
        assertEquals(2, splits.size)
        assertEquals("Arial", splits[0].fontKey)
        assertEquals(0..0, splits[0].glyphRange)
        assertEquals("Noto", splits[1].fontKey)
        assertEquals(1..2, splits[1].glyphRange)
    }

    @Test
    fun `fallback chain refuses exhausted depth`() {
        val glyphs = listOf(
            GpuFallbackGlyphPlan(0, "A", "B", GpuFallbackReason.MissingGlyph),
        )
        val splits = GpuFallbackBatchPolicy.splitSubruns(glyphs, maxFallbackDepth = 0)
        assertTrue { splits.isEmpty() }
    }

    @Test
    fun `fallback reason identifies color font preference`() {
        val plan = GpuFallbackGlyphPlan(0, "Arial", "EmojiOne", GpuFallbackReason.ColorFontPreference)
        assertEquals("EmojiOne", plan.fallbackFont)
        assertEquals(GpuFallbackReason.ColorFontPreference, plan.reason)
    }

    @Test
    fun `fallback chain handles single glyph run`() {
        val glyphs = listOf(
            GpuFallbackGlyphPlan(0, "Fira", "Fira", GpuFallbackReason.MissingGlyph),
        )
        val splits = GpuFallbackBatchPolicy.splitSubruns(glyphs, maxFallbackDepth = 3)
        assertEquals(1, splits.size)
        assertEquals("Fira", splits[0].fontKey)
    }

    @Test
    fun `fallback chain detects unsupported script`() {
        val plan = GpuFallbackGlyphPlan(
            glyphIndex = 42,
            originalFont = "LatinFont",
            fallbackFont = "ArabicFont",
            reason = GpuFallbackReason.UnsupportedScript,
        )
        assertEquals(GpuFallbackReason.UnsupportedScript, plan.reason)
        assertEquals("ArabicFont", plan.fallbackFont)
    }
}
