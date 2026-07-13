package org.graphiks.kanvas.text

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CustomTypefaceTest {
    @Test
    fun `custom typeface preserves direct and drawable glyphs across serialization`() {
        val direct = Path().addRect(org.graphiks.kanvas.types.Rect.fromXYWH(0f, 0f, 0.5f, 1f))
        val drawable = Path().addCircle(0.5f, 0.5f, 0.5f)
        val typeface = CustomTypeface.Builder("test-user-typeface")
            .setMetrics(FontMetrics(ascent = 0.8f, descent = -0.2f, leading = 0.1f))
            .setGlyph('A'.code, advance = 0.6f, path = direct)
            .setDrawableGlyph('B'.code, advance = 0.7f, path = drawable, paint = Paint.fill(Color.fromArgb(255, 0, 128, 0)))
            .build()

        val decoded = CustomTypeface.deserialize(typeface.serialize())

        assertEquals('A'.code, decoded.glyphIdForCodepoint('A'.code))
        assertEquals(12f, decoded.getAdvance('A'.code, 20f))
        assertNotNull(decoded.getGlyphPath('B'.code, 20f))
        assertEquals(16f, decoded.getMetrics(20f)?.ascent)
        val painted = assertIs<GlyphPaintProvider>(decoded).paintForGlyph('B'.code)
        assertEquals(Color.fromArgb(255, 0, 128, 0), painted?.color)
    }
}
