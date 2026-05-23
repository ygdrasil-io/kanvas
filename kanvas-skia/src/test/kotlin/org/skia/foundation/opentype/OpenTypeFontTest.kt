package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkPath
import org.skia.foundation.SkTextEncoding

class OpenTypeFontTest {
    private fun liberationSansBytes(): ByteArray {
        val resource = "/fonts/liberation/LiberationSans-Regular.ttf"
        val stream = OpenTypeFontTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `makeFromData loads bundled Liberation TTF without AWT`() {
        val mgr = OpenTypeFontMgr.Create()
        val loaded = mgr.makeFromData(SkData.MakeWithCopy(liberationSansBytes()))
        val typeface = requireNotNull(loaded)

        assertTrue(typeface is OpenTypeTypeface)
        assertEquals("Liberation Sans", typeface.getFamilyName())
        assertTrue(typeface.countGlyphs() > 100)
    }

    @Test
    fun `makeFromData rejects empty and garbage data`() {
        val mgr = OpenTypeFontMgr.Create()

        assertNull(mgr.makeFromData(SkData.EMPTY))
        assertNull(mgr.makeFromData(SkData.MakeWithCopy(ByteArray(64) { it.toByte() })))
    }

    @Test
    fun `cmap maps Latin A to a non-zero glyph`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = ShortArray(1)

        typeface.unicharsToGlyphsInternal(intArrayOf('A'.code), 1, glyphs)

        assertTrue((glyphs[0].toInt() and 0xFFFF) > 0)
    }

    @Test
    fun `glyph width and text measurement scale with font size`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val small = SkFont(typeface, 12f)
        val large = SkFont(typeface, 24f)

        val smallWidth = small.measureText("ABC")
        val largeWidth = large.measureText("ABC")

        assertTrue(small.getWidth(small.textToGlyphs("A")[0]) > 0f)
        assertTrue(smallWidth > 0f)
        assertEquals(smallWidth * 2f, largeWidth, 0.01f)
    }

    @Test
    fun `glyph path for A contains TrueType quadratic contours`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 48f)
        val glyphA = font.textToGlyphs("A", SkTextEncoding.kUTF8)[0]

        val path = requireNotNull(font.getPath(glyphA))

        assertTrue(path.verbs.contains(SkPath.Verb.kMove))
        assertTrue(path.verbs.contains(SkPath.Verb.kQuad) || path.verbs.contains(SkPath.Verb.kLine))
        assertTrue(path.verbs.contains(SkPath.Verb.kClose))
        assertTrue(path.computeTightBounds().width() > 0f)
        assertTrue(path.computeTightBounds().height() > 0f)
    }

    @Test
    fun `font metrics are populated from OpenType tables`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 20f)
        val metrics = SkFontMetrics()

        val spacing = font.getMetrics(metrics)

        assertTrue(spacing > 0f)
        assertTrue(metrics.fAscent < 0f)
        assertTrue(metrics.fDescent > 0f)
        assertTrue(metrics.fMaxCharWidth > 0f)
    }
}
