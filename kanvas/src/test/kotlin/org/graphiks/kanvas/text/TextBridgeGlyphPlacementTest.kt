package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertTrue
import org.graphiks.kanvas.types.Rect

class TextBridgeGlyphPlacementTest {
    @Test
    fun `rasterized glyph rects preserve baseline-relative placement`() {
        val fontBytes = javaClass.classLoader
            .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val typeface = FontTypeface(fontBytes, fontName = "LiberationSans-Regular")
        val font = Font(typeface, size = 24f)

        val gpuBlob = TextBridge.rasterize(font.toTextBlob("A", 0f, 0f))!!

        val rect = gpuBlob.glyphRects.single()
        assertTrue(rect.width > 0f)
        assertTrue(rect.height > 0f)
        assertTrue(rect.top < 0f, "top should be above baseline after y-down mapping: $rect")
        assertTrue(rect.bottom <= 2f, "bottom should stay near or below baseline: $rect")
        assertTrue(rect != Rect(0f, 0f, rect.width, rect.height), "glyph rect must preserve baseline-relative vertical offset")
    }
}
