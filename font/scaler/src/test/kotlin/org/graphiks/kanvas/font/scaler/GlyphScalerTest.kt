package org.graphiks.kanvas.font.scaler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GlyphScalerTest {

    @Test
    fun `scaler produces deterministic glyph outline for Liberation Sans 'A' at 32px`() {
        val fontBytes = javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val scaler = GlyphScaler.fromBytes(fontBytes)

        val codepoint = 'A'.code
        val glyph = scaler.scaleGlyph(
            glyphId = scaler.glyphIdForCodepoint(codepoint),
            size = 32.0f,
            sourceCodepoint = codepoint,
        )

        assertNotNull(glyph)
        assertEquals(codepoint, glyph.sourceCodepoint)
        assertEquals(32.0f, glyph.size)
        assertTrue(glyph.advanceWidth > 0f)
        assertTrue(glyph.bounds.width > 0f && glyph.bounds.height > 0f)
        // Deterministic: same input -> same output (hash-stable)
        val secondRun = scaler.scaleGlyph(scaler.glyphIdForCodepoint(codepoint), 32.0f, sourceCodepoint = codepoint)
        assertEquals(glyph.checksum(), secondRun.checksum())
    }

    @Test
    fun `scaler refuses unknown glyph id with stable diagnostic`() {
        val fontBytes = javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val scaler = GlyphScaler.fromBytes(fontBytes)

        val result = scaler.scaleGlyphOrDiagnostic(glyphId = 99999, size = 32.0f)

        assertTrue(result is GlyphScaleResult.Unsupported)
        assertEquals("font.scaler.glyph_id_out_of_range", (result as GlyphScaleResult.Unsupported).code)
    }
}
