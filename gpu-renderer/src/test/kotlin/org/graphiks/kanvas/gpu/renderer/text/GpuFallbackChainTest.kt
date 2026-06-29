package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GpuFallbackChainTest {

    @Test
    fun `fallback chain splits subruns when fonts differ`() {
        val glyphs = listOf(
            GPUFallbackGlyphPlan(0, GPUFontKey("Arial"), GPUFontKey("Arial"), GPUFallbackReason.MissingGlyph),
            GPUFallbackGlyphPlan(1, GPUFontKey("Arial"), GPUFontKey("Noto"), GPUFallbackReason.MissingGlyph),
            GPUFallbackGlyphPlan(2, GPUFontKey("Noto"), GPUFontKey("Noto"), GPUFallbackReason.MissingGlyph),
        )
        val splits = GPUFallbackBatchPolicy.splitSubruns(glyphs, maxFallbackDepth = 3)
        assertEquals(2, splits.size)
        assertEquals(GPUFontKey("Arial"), splits[0].fontKey)
        assertEquals(0..0, splits[0].glyphRange)
        assertEquals(GPUFontKey("Noto"), splits[1].fontKey)
        assertEquals(1..2, splits[1].glyphRange)
    }

    @Test
    fun `fallback chain refuses exhausted depth`() {
        val glyphs = listOf(
            GPUFallbackGlyphPlan(0, GPUFontKey("A"), GPUFontKey("B"), GPUFallbackReason.MissingGlyph),
        )
        val splits = GPUFallbackBatchPolicy.splitSubruns(glyphs, maxFallbackDepth = 0)
        assertTrue { splits.isEmpty() }
    }

    @Test
    fun `fallback reason identifies color font preference`() {
        val plan = GPUFallbackGlyphPlan(
            0, GPUFontKey("Arial"), GPUFontKey("EmojiOne"), GPUFallbackReason.ColorFontPreference,
        )
        assertEquals(GPUFontKey("EmojiOne"), plan.fallbackFont)
        assertEquals(GPUFallbackReason.ColorFontPreference, plan.reason)
    }

    @Test
    fun `fallback chain handles single glyph run`() {
        val glyphs = listOf(
            GPUFallbackGlyphPlan(0, GPUFontKey("Fira"), GPUFontKey("Fira"), GPUFallbackReason.MissingGlyph),
        )
        val splits = GPUFallbackBatchPolicy.splitSubruns(glyphs, maxFallbackDepth = 3)
        assertEquals(1, splits.size)
        assertEquals(GPUFontKey("Fira"), splits[0].fontKey)
    }

    @Test
    fun `fallback chain detects unsupported script`() {
        val plan = GPUFallbackGlyphPlan(
            glyphIndex = 42,
            originalFont = GPUFontKey("LatinFont"),
            fallbackFont = GPUFontKey("ArabicFont"),
            reason = GPUFallbackReason.UnsupportedScript,
        )
        assertEquals(GPUFallbackReason.UnsupportedScript, plan.reason)
        assertEquals(GPUFontKey("ArabicFont"), plan.fallbackFont)
    }

    @Test
    fun `exhausted fallback chain produces diagnostic with glyph index and font identity`() {
        val diag = GPUFallbackBatchPolicy.fallbackExhaustedDiagnostic(
            glyphIndex = 7,
            fontIdentity = "BoldNotoSans",
        )
        assertEquals(GPUTextDiagnosticCodes.FALLBACK_EXHAUSTED, diag.code)
        assertEquals(true, diag.terminal)
        assertTrue(diag.message.contains("glyph 7"))
        assertTrue(diag.message.contains("BoldNotoSans"))
    }
}
