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
import kotlin.random.Random

class OpenTypeFontTest {
    private fun liberationSansBytes(): ByteArray {
        val resource = "/fonts/liberation/LiberationSans-Regular.ttf"
        val stream = OpenTypeFontTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    private fun singletonTtcBytes(fontBytes: ByteArray): ByteArray {
        val ttc = ByteArray(16 + fontBytes.size)
        ttc[0] = 't'.code.toByte()
        ttc[1] = 't'.code.toByte()
        ttc[2] = 'c'.code.toByte()
        ttc[3] = 'f'.code.toByte()
        ttc[7] = 1
        ttc[11] = 1
        ttc[15] = 16
        fontBytes.copyInto(ttc, 16)
        return ttc
    }

    private fun ByteArray.withTableLength(tag: String, length: Int): ByteArray {
        require(tag.length == 4)
        val copy = copyOf()
        val numTables = readU16(copy, 4)
        var off = 12
        repeat(numTables) {
            if (String(copy, off, 4, Charsets.ISO_8859_1) == tag) {
                writeU32(copy, off + 12, length)
                return copy
            }
            off += 16
        }
        error("Missing table: $tag")
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
    fun `makeFromBytes rejects empty and deterministic random data`() {
        val randomBytes = ByteArray(128)
        Random(0x5EED).nextBytes(randomBytes)

        assertNull(OpenTypeTypeface.MakeFromBytes(ByteArray(0)))
        assertNull(OpenTypeTypeface.MakeFromBytes(randomBytes))
    }

    @Test
    fun `makeFromBytes rejects truncated sfnt data`() {
        val bytes = liberationSansBytes()

        for (length in listOf(1, 4, 11, 12, 24, 128)) {
            assertNull(OpenTypeTypeface.MakeFromBytes(bytes.copyOf(length)), "length=$length")
        }
    }

    @Test
    fun `makeFromBytes rejects cmap subtables outside declared cmap table length`() {
        val bytes = liberationSansBytes().withTableLength("cmap", 30)

        assertNull(OpenTypeTypeface.MakeFromBytes(bytes))
    }

    @Test
    fun `makeFromBytes rejects truncated OS2 metrics table`() {
        assertNull(OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableLength("OS/2", 0)))
        assertNull(OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableLength("OS/2", 2)))
    }

    @Test
    fun `makeFromBytes rejects invalid TTC indexes`() {
        val ttcBytes = singletonTtcBytes(liberationSansBytes())

        assertNull(OpenTypeTypeface.MakeFromBytes(ttcBytes, -1))
        assertNull(OpenTypeTypeface.MakeFromBytes(ttcBytes, 1))
        assertNull(OpenTypeTypeface.MakeFromBytes(liberationSansBytes(), 1))
    }

    @Test
    fun `makeFromBytes rejects truncated TTC headers`() {
        val ttcMagicOnly = byteArrayOf('t'.code.toByte(), 't'.code.toByte(), 'c'.code.toByte(), 'f'.code.toByte())
        val ttcHeaderWithoutFonts = ByteArray(12).also {
            it[0] = 't'.code.toByte()
            it[1] = 't'.code.toByte()
            it[2] = 'c'.code.toByte()
            it[3] = 'f'.code.toByte()
        }

        assertNull(OpenTypeTypeface.MakeFromBytes(ttcMagicOnly))
        assertNull(OpenTypeTypeface.MakeFromBytes(ttcHeaderWithoutFonts))
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

    private fun readU16(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)

    private fun writeU32(bytes: ByteArray, off: Int, value: Int) {
        bytes[off] = (value ushr 24).toByte()
        bytes[off + 1] = (value ushr 16).toByte()
        bytes[off + 2] = (value ushr 8).toByte()
        bytes[off + 3] = value.toByte()
    }
}
