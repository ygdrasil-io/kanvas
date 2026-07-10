package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `rasterized empty glyph has a zero-area placement rect`() {
        val fontBytes = javaClass.classLoader
            .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val typeface = FontTypeface(fontBytes, fontName = "LiberationSans-Regular")
        val font = Font(typeface, size = 24f)

        val gpuBlob = TextBridge.rasterize(font.toTextBlob("A A", 0f, 0f))!!

        val (firstA, space, secondA) = gpuBlob.glyphRects
        assertBaselineRelativeDrawable(firstA)
        assertEquals(0f, space.width)
        assertEquals(0f, space.height)
        assertBaselineRelativeDrawable(secondA)
    }

    private fun assertBaselineRelativeDrawable(rect: Rect) {
        assertTrue(rect.width > 0f, "glyph must have drawable width: $rect")
        assertTrue(rect.height > 0f, "glyph must have drawable height: $rect")
        assertTrue(rect.top < 0f, "glyph top should be above baseline: $rect")
        assertTrue(rect.bottom <= 2f, "glyph bottom should stay near or below baseline: $rect")
        assertTrue(rect != Rect(0f, 0f, rect.width, rect.height), "glyph must preserve baseline-relative vertical offset")
    }
}
