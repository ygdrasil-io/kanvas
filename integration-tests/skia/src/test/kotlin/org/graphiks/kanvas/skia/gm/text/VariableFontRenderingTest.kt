package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class VariableFontRenderingTest {
    @Test
    fun `weight coordinate reaches TrueType glyph outlines`() {
        val typeface = requireNotNull(Typefaces.fromResource("fonts/Variable.ttf"))
        val glyphId = typeface.glyphIdForCodepoint('t'.code)
        val regular = requireNotNull(typeface.getGlyphPath(glyphId, 100f))
        val weighted = requireNotNull(
            typeface.getGlyphPath(glyphId, 100f, mapOf("wght" to 721f)),
        )

        assertNotEquals(render(regular), render(weighted))
        assertEquals(
            mapOf("wght" to 721f),
            Font(typeface, size = 100f, variationCoordinates = mapOf("wght" to 721f))
                .toTextBlob("t", 0f, 0f)
                .variationCoordinates,
        )
    }

    private fun render(path: org.graphiks.kanvas.geometry.Path): List<UByte> =
        Surface(160, 160).also { surface ->
            surface.canvas {
                drawPath(path.transform(20f, 120f, 1f, 1f), Paint())
            }
        }.render().pixels.toList()
}
