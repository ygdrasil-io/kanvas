package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private fun ByteArray.withTableTag(from: String, to: String): ByteArray {
        require(from.length == 4)
        require(to.length == 4)
        val copy = copyOf()
        val numTables = readU16(copy, 4)
        var off = 12
        repeat(numTables) {
            if (String(copy, off, 4, Charsets.ISO_8859_1) == from) {
                to.toByteArray(Charsets.ISO_8859_1).copyInto(copy, off)
                return copy
            }
            off += 16
        }
        error("Missing table: $from")
    }

    private fun ByteArray.withOs2UseTypoMetrics(): ByteArray {
        val copy = copyOf()
        val os2 = copy.tableRecord("OS/2")
        val fsSelectionOffset = os2 + 62
        val fsSelection = readU16(copy, fsSelectionOffset) or 0x0080
        writeU16(copy, fsSelectionOffset, fsSelection)
        return copy
    }

    private fun ByteArray.tableRecord(tag: String): Int {
        require(tag.length == 4)
        val numTables = readU16(this, 4)
        var off = 12
        repeat(numTables) {
            if (String(this, off, 4, Charsets.ISO_8859_1) == tag) {
                return readU32(this, off + 8)
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
    fun `getPostScriptName returns bundled Liberation Sans name table entry`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!

        assertEquals("LiberationSans", typeface.getPostScriptName())
    }

    @Test
    fun `createFamilyNameIterator exposes family and full names from bundled Liberation Sans`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!

        val names = typeface.createFamilyNameIterator().asSequence().toList()

        assertTrue(names.size >= 2)
        assertTrue(names.any { it.fString == "Liberation Sans" })
        assertTrue(names.any { it.fLanguage == "en" })
    }

    @Test
    fun `malformed name table falls back to documented OpenType family name`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableLength("name", 0))!!

        val names = typeface.createFamilyNameIterator().asSequence().toList()

        assertEquals("OpenType", typeface.getFamilyName())
        assertNull(typeface.getPostScriptName())
        assertFalse(names.isEmpty())
        assertTrue(names.any { it.fString == "OpenType" })
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
        assertEquals(-1854f * 20f / 2048f, metrics.fAscent, 0.001f)
        assertEquals(434f * 20f / 2048f, metrics.fDescent, 0.001f)
        assertEquals(67f * 20f / 2048f, metrics.fLeading, 0.001f)
        assertEquals(-1082f * 20f / 2048f, metrics.fXHeight, 0.001f)
        assertEquals(-1409f * 20f / 2048f, metrics.fCapHeight, 0.001f)
        assertEquals(150f * 20f / 2048f, metrics.fUnderlineThickness, 0.001f)
        assertEquals(-8f * 20f / 2048f, metrics.fUnderlinePosition, 0.001f)
        assertEquals(102f * 20f / 2048f, metrics.fStrikeoutThickness, 0.001f)
        assertEquals(-530f * 20f / 2048f, metrics.fStrikeoutPosition, 0.001f)
        assertTrue((metrics.fFlags and SkFontMetrics.kUnderlineThicknessIsValid_Flag) != 0)
        assertTrue((metrics.fFlags and SkFontMetrics.kUnderlinePositionIsValid_Flag) != 0)
        assertTrue((metrics.fFlags and SkFontMetrics.kStrikeoutThicknessIsValid_Flag) != 0)
        assertTrue((metrics.fFlags and SkFontMetrics.kStrikeoutPositionIsValid_Flag) != 0)
    }

    @Test
    fun `font metrics prefer OS2 typo metrics when requested`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withOs2UseTypoMetrics())!!
        val font = SkFont(typeface, 20f)
        val metrics = SkFontMetrics()

        val spacing = font.getMetrics(metrics)

        assertEquals(-1491f * 20f / 2048f, metrics.fAscent, 0.001f)
        assertEquals(431f * 20f / 2048f, metrics.fDescent, 0.001f)
        assertEquals(307f * 20f / 2048f, metrics.fLeading, 0.001f)
        assertEquals((1491f + 431f + 307f) * 20f / 2048f, spacing, 0.001f)
    }

    @Test
    fun `getKerningPairAdjustments returns one adjustment per adjacent glyph pair`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(typeface, 12f).textToGlyphs("AV").toShortArray()

        val adjustments = requireNotNull(typeface.getKerningPairAdjustments(glyphs))

        assertEquals(glyphs.size - 1, adjustments.size)
        assertEquals(-152, adjustments[0])
        assertTrue(adjustments[0] != 0)
    }

    @Test
    fun `getKerningPairAdjustments returns null when font has no kern table`() {
        val bytes = liberationSansBytes().withTableTag("kern", "zern")
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!
        val glyphs = SkFont(typeface, 12f).textToGlyphs("AV").toShortArray()

        assertNull(typeface.getKerningPairAdjustments(glyphs))
    }

    @Test
    fun `getKerningPairAdjustments ignores malformed kern table`() {
        val bytes = liberationSansBytes().withTableLength("kern", 4)
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!
        val glyphs = SkFont(typeface, 12f).textToGlyphs("AV").toShortArray()

        assertNull(typeface.getKerningPairAdjustments(glyphs))
    }

    private fun readU16(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)

    private fun readU32(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xFF) shl 24) or
            ((bytes[off + 1].toInt() and 0xFF) shl 16) or
            ((bytes[off + 2].toInt() and 0xFF) shl 8) or
            (bytes[off + 3].toInt() and 0xFF)

    private fun IntArray.toShortArray(): ShortArray =
        ShortArray(size) { this[it].toShort() }

    private fun writeU16(bytes: ByteArray, off: Int, value: Int) {
        bytes[off] = (value ushr 8).toByte()
        bytes[off + 1] = value.toByte()
    }

    private fun writeU32(bytes: ByteArray, off: Int, value: Int) {
        bytes[off] = (value ushr 24).toByte()
        bytes[off + 1] = (value ushr 16).toByte()
        bytes[off + 2] = (value ushr 8).toByte()
        bytes[off + 3] = value.toByte()
    }
}
