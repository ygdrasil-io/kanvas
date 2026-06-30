package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TextBlobTest {
    @Test fun `KanvasGlyphRun`() { val r = KanvasGlyphRun(listOf(65u,66u,67u), listOf(Point(0f,0f),Point(10f,0f),Point(20f,0f))); assertEquals(3, r.glyphs.size) }
    @Test fun `TextBlob with typeface`() { val b = TextBlob(listOf(KanvasGlyphRun(listOf(72u), listOf(Point(0f,0f)))), KanvasTypeface("f.ttf"), 16f); assertEquals("f.ttf", b.typeface!!.resourcePath) }
    @Test fun `TextBlob without typeface`() { val b = TextBlob(listOf(KanvasGlyphRun(listOf(65u), listOf(Point(0f,0f))))); assertEquals(null, b.typeface); assertEquals(12f, b.fontSize) }
}
