package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import kotlin.random.Random

class OpenTypeFontTest {
    private fun liberationSansBytes(): ByteArray {
        val resource = "/fonts/liberation/LiberationSans-Regular.ttf"
        val stream = OpenTypeFontTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    private fun distortableBytes(): ByteArray {
        val resource = "/fonts/Distortable.ttf"
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

    private fun ByteArray.withoutKerningTables(): ByteArray =
        withTableTag("kern", "zern").withTableTag("GPOS", "zPOS")

    private fun ByteArray.withGposFeatureTag(from: String, to: String): ByteArray {
        require(from.length == 4)
        require(to.length == 4)
        val copy = copyOf()
        val record = tableDirectoryRecord("GPOS")
        val gposOffset = readU32(copy, record + 8)
        val gposLength = readU32(copy, record + 12)
        val fromBytes = from.toByteArray(Charsets.ISO_8859_1)
        val toBytes = to.toByteArray(Charsets.ISO_8859_1)
        for (off in gposOffset..(gposOffset + gposLength - fromBytes.size)) {
            if (fromBytes.indices.all { copy[off + it] == fromBytes[it] }) {
                toBytes.copyInto(copy, off)
                return copy
            }
        }
        error("Missing GPOS feature tag: $from")
    }

    private fun ByteArray.tableRecord(tag: String): Int {
        val record = tableDirectoryRecord(tag)
        return readU32(this, record + 8)
    }

    private fun ByteArray.tableDirectoryRecord(tag: String): Int {
        require(tag.length == 4)
        val numTables = readU16(this, 4)
        var off = 12
        repeat(numTables) {
            if (String(this, off, 4, Charsets.ISO_8859_1) == tag) {
                return off
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
    fun `copyTableData returns raw OpenType table bytes defensively`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val nameTag = SkFontVariation.Tag.of("name").raw

        val table = requireNotNull(typeface.copyTableData(nameTag))
        val secondRead = requireNotNull(typeface.copyTableData(nameTag))
        table[0] = (table[0].toInt() xor 0x7F).toByte()

        assertTrue(table.size > 6)
        assertEquals(0, secondRead[0].toInt())
        assertEquals(null, typeface.copyTableData(SkFontVariation.Tag.of("ZZZZ").raw))
        assertEquals(null, SkTypeface.MakeEmpty().copyTableData(nameTag))
    }

    @Test
    fun `variable font fvar axes expose Distortable weight range`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(distortableBytes())!!

        val axes = typeface.getVariationDesignParameters()

        assertEquals(1, axes.size)
        assertEquals(SkFontVariation.WEIGHT.raw, axes.single().tag)
        assertEquals(0.5f, axes.single().min, 0.0001f)
        assertEquals(1f, axes.single().default, 0.0001f)
        assertEquals(2f, axes.single().max, 0.0001f)
        assertEquals(1, axes.single().flags)
        assertEquals(256, axes.single().nameId)
    }

    @Test
    fun `non-variable fonts expose no fvar axes`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!

        assertTrue(typeface.getVariationDesignParameters().isEmpty())
        assertTrue(SkTypeface.MakeEmpty().getVariationDesignParameters().isEmpty())
    }

    @Test
    fun `malformed fvar table does not prevent font loading`() {
        val renamed = distortableBytes().withTableTag("fvar", "zvar")
        val truncated = distortableBytes().withTableLength("fvar", 12)

        assertTrue(OpenTypeTypeface.MakeFromBytes(renamed)!!.getVariationDesignParameters().isEmpty())
        assertTrue(OpenTypeTypeface.MakeFromBytes(truncated)!!.getVariationDesignParameters().isEmpty())
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
    fun `simple glyph paths close contours and emit implicit quadratic curves`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 48f)
        val glyphS = font.textToGlyphs("S").single()

        val path = requireNotNull(font.getPath(glyphS))
        val contourCount = path.verbs.count { it == SkPath.Verb.kMove }

        assertTrue(contourCount > 0)
        assertEquals(contourCount, path.verbs.count { it == SkPath.Verb.kClose })
        assertTrue(path.verbs.count { it == SkPath.Verb.kQuad } >= 4)
        assertTrue(path.computeTightBounds().width() > 0f)
        assertTrue(path.computeTightBounds().height() > 0f)
    }

    @Test
    fun `composite glyph paths apply translation scale xy scale and matrix transforms`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val baseFont = SkFont(baseTypeface, 2048f)
        val baseGlyph = baseFont.textToGlyphs("A").single()
        val basePath = requireNotNull(baseFont.getPath(baseGlyph))
        val compositeCodepoint = "\u00e9"
        val compositeGlyph = baseFont.textToGlyphs(compositeCodepoint).single()

        assertCompositePathTransform(
            bytes = liberationSansBytes().withCompositeGlyph(compositeGlyph, baseGlyph, dx = 256, dy = 128),
            codepoint = compositeCodepoint,
            basePath = basePath,
            a = 1f,
            b = 0f,
            c = 0f,
            d = 1f,
            dx = 256f,
            dy = 128f,
        )
        assertCompositePathTransform(
            bytes = liberationSansBytes().withCompositeGlyph(compositeGlyph, baseGlyph, a = 0.5f, d = 0.5f),
            codepoint = compositeCodepoint,
            basePath = basePath,
            a = 0.5f,
            b = 0f,
            c = 0f,
            d = 0.5f,
        )
        assertCompositePathTransform(
            bytes = liberationSansBytes().withCompositeGlyph(compositeGlyph, baseGlyph, a = 0.5f, d = 1.25f),
            codepoint = compositeCodepoint,
            basePath = basePath,
            a = 0.5f,
            b = 0f,
            c = 0f,
            d = 1.25f,
        )
        assertCompositePathTransform(
            bytes = liberationSansBytes().withCompositeGlyph(compositeGlyph, baseGlyph, b = 0.25f, c = 0.5f),
            codepoint = compositeCodepoint,
            basePath = basePath,
            a = 1f,
            b = 0.25f,
            c = 0.5f,
            d = 1f,
        )
    }

    @Test
    fun `glyph paths cover simple and composite Liberation glyphs`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 48f)

        for ((text, expectedGlyphId) in listOf("A" to 36, "\u00e9" to 171, "\u00c5" to 135)) {
            val glyphId = font.textToGlyphs(text).single()
            val path = requireNotNull(font.getPath(glyphId))
            val pathBounds = path.computeTightBounds()
            val glyphBounds = font.getBounds(glyphId)

            assertEquals(expectedGlyphId, glyphId)
            assertTrue(glyphId > 0, "$text must resolve to a non-zero glyph")
            assertFalse(path.isEmpty(), "$text glyph path must contain verbs")
            assertTrue(pathBounds.width() > 0f, "$text path width must be positive")
            assertTrue(pathBounds.height() > 0f, "$text path height must be positive")
            assertTrue(glyphBounds.width() > 0f, "$text glyph width must be positive")
            assertTrue(glyphBounds.height() > 0f, "$text glyph height must be positive")
        }
    }

    @Test
    fun `glyph bounds scale with font size for simple and composite glyphs`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val small = SkFont(typeface, 24f)
        val large = SkFont(typeface, 48f)

        for (text in listOf("A", "\u00e9")) {
            val smallGlyph = small.textToGlyphs(text).single()
            val largeGlyph = large.textToGlyphs(text).single()
            val smallBounds = small.getBounds(smallGlyph)
            val largeBounds = large.getBounds(largeGlyph)

            assertEquals(smallGlyph, largeGlyph)
            assertEquals(smallBounds.width() * 2f, largeBounds.width(), 0.001f)
            assertEquals(smallBounds.height() * 2f, largeBounds.height(), 0.001f)
        }
    }

    @Test
    fun `drawString with OpenTypeTypeface paints visible pixels`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 42f)
        val bitmap = SkBitmap(160, 90).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = true }

        SkCanvas(bitmap).drawString("A\u00e9\u00c5", 8f, 62f, font, paint)

        assertTrue(bitmap.pixels.any { it != 0xFFFFFFFF.toInt() })
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
    fun `measureText applies OpenType kern adjustments`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 20f)
        val glyphs = font.textToGlyphs("AV")
        val unkerned = font.getWidth(glyphs[0]) + font.getWidth(glyphs[1])
        val expectedKerning = -152f * font.size / 2048f

        assertEquals(unkerned + expectedKerning, font.measureText("AV"), 0.001f)
        assertTrue(font.measureText("AV") < unkerned)
    }

    @Test
    fun `measureText applies kerning to bounds glyph id encoding and scaleX`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val font = SkFont(typeface, 20f)
        val glyphs = font.textToGlyphs("AV")
        val rawGlyphIds = glyphString(glyphs)
        val noKerningFont = SkFont(OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withoutKerningTables())!!, 20f)
        val kernedBounds = org.graphiks.math.SkRect.MakeEmpty()
        val unkernedBounds = org.graphiks.math.SkRect.MakeEmpty()
        val unkerned = font.getWidth(glyphs[0]) + font.getWidth(glyphs[1])
        val expectedKerning = -152f * font.size / 2048f

        val kerned = font.measureText("AV", bounds = kernedBounds)
        noKerningFont.measureText("AV", bounds = unkernedBounds)

        assertEquals(kerned, font.measureText(rawGlyphIds, encoding = SkTextEncoding.kGlyphID), 0.001f)
        assertEquals(unkerned + expectedKerning, kerned, 0.001f)
        assertEquals(unkerned, noKerningFont.measureText("AV"), 0.001f)
        assertTrue(kernedBounds.right < unkernedBounds.right)

        font.scaleX = 2f
        assertEquals(2f * (unkerned + expectedKerning), font.measureText("AV"), 0.001f)
    }

    @Test
    fun `measureText applies GPOS pair positioning when legacy kern table is absent`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableTag("kern", "zern"))!!
        val font = SkFont(typeface, 20f)
        val glyphs = font.textToGlyphs("AV")
        val unkerned = font.getWidth(glyphs[0]) + font.getWidth(glyphs[1])
        val expectedKerning = -152f * font.size / 2048f

        val adjustments = requireNotNull(typeface.getKerningPairAdjustments(glyphs.toShortArray()))

        assertEquals(-152, adjustments[0])
        assertEquals(unkerned + expectedKerning, font.measureText("AV"), 0.001f)
    }

    @Test
    fun `GPOS fallback only uses enabled kern feature lookups`() {
        val bytes = liberationSansBytes()
            .withTableTag("kern", "zern")
            .withGposFeatureTag("kern", "zzzz")
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!
        val glyphs = SkFont(typeface, 12f).textToGlyphs("AV").toShortArray()

        assertNull(typeface.getKerningPairAdjustments(glyphs))
    }

    @Test
    fun `makeTextPath applies OpenType kern adjustments`() {
        val kernedTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val unkernedTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withoutKerningTables())!!
        val kernedPath = requireNotNull(SkFont(kernedTypeface, 64f).makeTextPath("AV", 0f, 0f))
        val unkernedPath = requireNotNull(SkFont(unkernedTypeface, 64f).makeTextPath("AV", 0f, 0f))

        assertTrue(kernedPath.computeTightBounds().right < unkernedPath.computeTightBounds().right)
    }

    @Test
    fun `getKerningPairAdjustments returns null when font has no kerning tables`() {
        val bytes = liberationSansBytes().withoutKerningTables()
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

    @Test
    fun `getKerningPairAdjustments ignores malformed GPOS table`() {
        val bytes = liberationSansBytes()
            .withTableTag("kern", "zern")
            .withTableLength("GPOS", 4)
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!
        val glyphs = SkFont(typeface, 12f).textToGlyphs("AV").toShortArray()

        assertNull(typeface.getKerningPairAdjustments(glyphs))
    }

    private fun assertCompositePathTransform(
        bytes: ByteArray,
        codepoint: String,
        basePath: SkPath,
        a: Float,
        b: Float,
        c: Float,
        d: Float,
        dx: Float = 0f,
        dy: Float = 0f,
    ) {
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!
        val font = SkFont(typeface, 2048f)
        val path = requireNotNull(font.getPath(font.textToGlyphs(codepoint).single()))

        assertEquals(basePath.verbs.toList(), path.verbs.toList())
        assertEquals(basePath.coords.size, path.coords.size)
        for (i in basePath.coords.indices step 2) {
            val x = basePath.coords[i]
            val y = basePath.coords[i + 1]
            val transformedX = (a * x - b * y + dx).toInt().toFloat()
            val transformedY = -((c * x - d * y + dy).toInt()).toFloat()
            assertEquals(transformedX, path.coords[i], 0.001f, "x coord $i")
            assertEquals(transformedY, path.coords[i + 1], 0.001f, "y coord ${i + 1}")
        }
    }

    private fun ByteArray.withCompositeGlyph(
        glyphId: Int,
        componentGlyphId: Int,
        dx: Int = 0,
        dy: Int = 0,
        a: Float = 1f,
        b: Float = 0f,
        c: Float = 0f,
        d: Float = 1f,
    ): ByteArray {
        val copy = copyOf()
        val glyphStart = copy.glyphDataOffset(glyphId)
        val glyphEnd = copy.glyphDataOffset(glyphId + 1)
        val glyphLength = glyphEnd - glyphStart
        var flags = ARG_1_AND_2_ARE_WORDS or ARGS_ARE_XY_VALUES
        val transformBytes = when {
            b != 0f || c != 0f -> {
                flags = flags or WE_HAVE_A_TWO_BY_TWO
                floatArrayOf(a, b, c, d)
            }
            a != d -> {
                flags = flags or WE_HAVE_AN_X_AND_Y_SCALE
                floatArrayOf(a, d)
            }
            a != 1f -> {
                flags = flags or WE_HAVE_A_SCALE
                floatArrayOf(a)
            }
            else -> floatArrayOf()
        }
        val componentLength = 10 + 4 + 4 + transformBytes.size * 2
        require(componentLength <= glyphLength)

        writeU16(copy, glyphStart, 0xFFFF)
        for (i in 2 until 10) copy[glyphStart + i] = 0
        var off = glyphStart + 10
        writeU16(copy, off, flags); off += 2
        writeU16(copy, off, componentGlyphId); off += 2
        writeI16(copy, off, dx); off += 2
        writeI16(copy, off, dy); off += 2
        transformBytes.forEach {
            writeI16(copy, off, toF2Dot14(it)); off += 2
        }
        return copy
    }

    private fun ByteArray.glyphDataOffset(glyphId: Int): Int {
        val head = tableRecord("head")
        val loca = tableRecord("loca")
        val glyf = tableRecord("glyf")
        val indexToLocFormat = readI16(this, head + 50).toInt()
        val glyphOffset = when (indexToLocFormat) {
            0 -> readU16(this, loca + glyphId * 2) * 2
            1 -> readU32(this, loca + glyphId * 4)
            else -> error("Unsupported loca format: $indexToLocFormat")
        }
        return glyf + glyphOffset
    }

    private fun readU16(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)

    private fun readI16(bytes: ByteArray, off: Int): Short = readU16(bytes, off).toShort()

    private fun readU32(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xFF) shl 24) or
            ((bytes[off + 1].toInt() and 0xFF) shl 16) or
            ((bytes[off + 2].toInt() and 0xFF) shl 8) or
            (bytes[off + 3].toInt() and 0xFF)

    private fun IntArray.toShortArray(): ShortArray =
        ShortArray(size) { this[it].toShort() }

    private fun glyphString(glyphs: IntArray): String =
        glyphs.joinToString(separator = "") { (it and 0xFFFF).toChar().toString() }

    private fun writeU16(bytes: ByteArray, off: Int, value: Int) {
        bytes[off] = (value ushr 8).toByte()
        bytes[off + 1] = value.toByte()
    }

    private fun writeI16(bytes: ByteArray, off: Int, value: Int) {
        writeU16(bytes, off, value and 0xFFFF)
    }

    private fun writeU32(bytes: ByteArray, off: Int, value: Int) {
        bytes[off] = (value ushr 24).toByte()
        bytes[off + 1] = (value ushr 16).toByte()
        bytes[off + 2] = (value ushr 8).toByte()
        bytes[off + 3] = value.toByte()
    }

    private fun toF2Dot14(value: Float): Int =
        (value * 16384f).toInt()

    private companion object {
        private const val ARG_1_AND_2_ARE_WORDS = 0x0001
        private const val ARGS_ARE_XY_VALUES = 0x0002
        private const val WE_HAVE_A_SCALE = 0x0008
        private const val WE_HAVE_AN_X_AND_Y_SCALE = 0x0040
        private const val WE_HAVE_A_TWO_BY_TWO = 0x0080
    }
}
