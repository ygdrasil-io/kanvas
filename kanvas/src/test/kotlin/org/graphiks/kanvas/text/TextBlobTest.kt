package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TextBlobTest {
    @Test fun `KanvasGlyphRun`() { val r = KanvasGlyphRun(listOf(65u,66u,67u), listOf(Point(0f,0f),Point(10f,0f),Point(20f,0f))); assertEquals(3, r.glyphs.size) }
    @Test fun `TextBlob with typeface`() { val b = TextBlob(listOf(KanvasGlyphRun(listOf(72u), listOf(Point(0f,0f)))), KanvasTypeface("f.ttf"), 16f); assertEquals("f.ttf", (b.typeface as KanvasTypeface).resourcePath) }
    @Test fun `TextBlob without typeface`() { val b = TextBlob(listOf(KanvasGlyphRun(listOf(65u), listOf(Point(0f,0f))))); assertEquals(null, b.typeface); assertEquals(12f, b.fontSize) }

    @Test
    fun `Font toTextBlob stores local positions from requested local origin`() {
        val typeface = RecordingTypeface()
        val font = Font(typeface, size = 20f)

        val blob = font.toTextBlob("AB", 3f, 4f)

        assertEquals(
            listOf(Point(3f, 4f), Point(13f, 4f)),
            blob.glyphRuns.single().positions,
        )
    }

    @Test
    fun `Canvas drawString stores draw origin once`() {
        val surface = org.graphiks.kanvas.surface.Surface(width = 64, height = 64)
        val typeface = RecordingTypeface()
        val font = Font(typeface, size = 20f)

        surface.canvas().drawString("A", 15f, 25f, font, org.graphiks.kanvas.paint.Paint())

        val op = surface.snapshotOps().filterIsInstance<org.graphiks.kanvas.canvas.DisplayOp.DrawText>().single()
        assertEquals(15f, op.x)
        assertEquals(25f, op.y)
        assertEquals(listOf(Point(0f, 0f)), op.blob.glyphRuns.single().positions)
    }

    private class RecordingTypeface : Typeface {
        override val fontName: String = "recording"
        override fun glyphIdForCodepoint(codepoint: Int): Int = codepoint
        override fun getAdvance(glyphId: Int, fontSize: Float): Float = fontSize * 0.5f
        override fun getGlyphPath(glyphId: Int, fontSize: Float): org.graphiks.kanvas.geometry.Path? = null
    }
}
