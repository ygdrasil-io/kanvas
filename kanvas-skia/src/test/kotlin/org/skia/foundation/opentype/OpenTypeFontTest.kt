package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
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

    private fun ByteArray.withColrCpalFixture(baseGlyph: Int, layerGlyph0: Int, layerGlyph1: Int): ByteArray =
        withTableContent("GPOS", "COLR", syntheticColrV0(baseGlyph, layerGlyph0, layerGlyph1))
            .withTableContent("kern", "CPAL", syntheticCpalV0())

    private fun ByteArray.withTableContent(from: String, to: String, content: ByteArray): ByteArray {
        require(from.length == 4)
        require(to.length == 4)
        val copy = copyOf()
        val record = copy.tableDirectoryRecord(from)
        val offset = readU32(copy, record + 8)
        val length = readU32(copy, record + 12)
        require(content.size <= length) { "$from table too small for synthetic $to table" }
        to.toByteArray(Charsets.ISO_8859_1).copyInto(copy, record)
        writeU32(copy, record + 12, content.size)
        content.copyInto(copy, offset)
        for (i in offset + content.size until offset + length) copy[i] = 0
        return copy
    }

    private fun ByteArray.withTableContentKeepingOriginalLength(from: String, to: String, content: ByteArray): ByteArray {
        require(from.length == 4)
        require(to.length == 4)
        val copy = copyOf()
        val record = copy.tableDirectoryRecord(from)
        val offset = readU32(copy, record + 8)
        val length = readU32(copy, record + 12)
        require(content.size <= length) { "$from table too small for synthetic $to table" }
        to.toByteArray(Charsets.ISO_8859_1).copyInto(copy, record)
        content.copyInto(copy, offset)
        for (i in offset + content.size until offset + length) copy[i] = 0
        return copy
    }

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
    fun `makeClone applies Distortable gvar deltas to simple glyph paths`() {
        val defaultTypeface = OpenTypeTypeface.MakeFromBytes(distortableBytes())!!
        val lightTypeface = defaultTypeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 0.5f)),
                ),
            ),
        )!!
        val heavyTypeface = defaultTypeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 2f)),
                ),
            ),
        )!!

        val defaultPath = distortablePath(defaultTypeface, "a")
        val lightPath = distortablePath(lightTypeface, "a")
        val heavyPath = distortablePath(heavyTypeface, "a")
        assertFalse(lightPath.coords.toList() == defaultPath.coords.toList())
        assertFalse(heavyPath.coords.toList() == defaultPath.coords.toList())
        assertFalse(lightPath.coords.toList() == heavyPath.coords.toList())
    }

    @Test
    fun `variation clone clamps coordinates and duplicate axes use last value`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(distortableBytes())!!
        val maxTypeface = typeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 2f)),
                ),
            ),
        )!!
        val clampedTypeface = typeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 999f)),
                ),
            ),
        )!!
        val duplicateTypeface = typeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(
                        SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 0.5f),
                        SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 2f),
                    ),
                ),
            ),
        )!!

        assertEquals(distortableBounds(maxTypeface, "b"), distortableBounds(clampedTypeface, "b"))
        assertEquals(distortableBounds(maxTypeface, "b"), distortableBounds(duplicateTypeface, "b"))
    }

    @Test
    fun `unknown variation axes are ignored by OpenType clones`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(distortableBytes())!!
        val clone = typeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.Tag.of("XXXX"), 2f)),
                ),
            ),
        )!!

        assertEquals(distortableBounds(typeface, "c"), distortableBounds(clone, "c"))
    }

    @Test
    fun `malformed gvar table fails closed without disabling fvar axes`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(distortableBytes())!!
        val noGvar = OpenTypeTypeface.MakeFromBytes(distortableBytes().withTableTag("gvar", "zvar"))!!
        val clone = noGvar.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 2f)),
                ),
            ),
        )!!

        assertEquals(typeface.getVariationDesignParameters(), noGvar.getVariationDesignParameters())
        assertEquals(distortableBounds(noGvar, "a"), distortableBounds(clone, "a"))
    }

    @Test
    fun `gvar parses private packed point counts without off by one`() {
        val onePoint = OpenTypeTypeface.MakeFromBytes(
            distortableBytes().withTableContent("gvar", "gvar", syntheticGvarPrivatePointDeltas(1, byteArrayOf(0x01))),
        )!!
        val fiftyPoints = OpenTypeTypeface.MakeFromBytes(
            distortableBytes().withTableContent("gvar", "gvar", syntheticGvarPrivatePointDeltas(50, byteArrayOf(0x32))),
        )!!
        val twoByteCount = OpenTypeTypeface.MakeFromBytes(
            distortableBytes().withTableContent("gvar", "gvar", syntheticGvarPrivatePointDeltas(50, byteArrayOf(0x80.toByte(), 0x32))),
        )!!

        assertFalse(distortablePath(onePoint, "a").coords.toList() == variedDistortablePath(onePoint, "a").coords.toList())
        assertFalse(distortablePath(fiftyPoints, "a").coords.toList() == variedDistortablePath(fiftyPoints, "a").coords.toList())
        assertFalse(distortablePath(twoByteCount, "a").coords.toList() == variedDistortablePath(twoByteCount, "a").coords.toList())
    }

    @Test
    fun `gvar glyph data offset before tuple headers fails closed`() {
        val bytes = distortableBytes().withTableContent(
            "gvar",
            "gvar",
            syntheticGvarPrivatePointDeltas(1, byteArrayOf(0x01)).also {
                val glyphDataStart = readU32(it, 16)
                writeU16(it, glyphDataStart + 2, 4)
            },
        )
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!

        assertEquals(distortablePath(typeface, "a").coords.toList(), variedDistortablePath(typeface, "a").coords.toList())
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
    fun `cmap format 0 maps MacRoman glyph ids when no Unicode subtable exists`() {
        val base = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val sourceFont = SkFont(base, 12f)
        val glyphA = sourceFont.textToGlyphs("A")[0]
        val glyphEAcute = sourceFont.textToGlyphs("\u00E9")[0]
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withTableContent("cmap", "cmap", syntheticCmapFormat0(glyphA, glyphEAcute)),
        )!!
        val glyphs = ShortArray(3)

        typeface.unicharsToGlyphsInternal(intArrayOf('A'.code, '\u00E9'.code, '\u0401'.code), 3, glyphs)

        assertEquals(glyphA, glyphs[0].toInt() and 0xFFFF)
        assertEquals(glyphEAcute, glyphs[1].toInt() and 0xFFFF)
        assertEquals(0, glyphs[2].toInt() and 0xFFFF)
    }

    @Test
    fun `cmap format 6 maps trimmed MacRoman glyph ids when no Unicode subtable exists`() {
        val base = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val sourceFont = SkFont(base, 12f)
        val glyphA = sourceFont.textToGlyphs("A")[0]
        val glyphB = sourceFont.textToGlyphs("B")[0]
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withTableContent("cmap", "cmap", syntheticCmapFormat6('A'.code, intArrayOf(glyphA, glyphB))),
        )!!
        val glyphs = ShortArray(3)

        typeface.unicharsToGlyphsInternal(intArrayOf('A'.code, 'B'.code, 'C'.code), 3, glyphs)

        assertEquals(glyphA, glyphs[0].toInt() and 0xFFFF)
        assertEquals(glyphB, glyphs[1].toInt() and 0xFFFF)
        assertEquals(0, glyphs[2].toInt() and 0xFFFF)
    }

    @Test
    fun `cmap keeps Windows format 4 priority over MacRoman format 6`() {
        val base = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val sourceFont = SkFont(base, 12f)
        val glyphA = sourceFont.textToGlyphs("A")[0]
        val glyphB = sourceFont.textToGlyphs("B")[0]
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withTableContent(
                "cmap",
                "cmap",
                syntheticCmapWithFormat4AndMacRomanFormat6(format4Glyph = glyphA, format6Glyph = glyphB),
            ),
        )!!
        val glyphs = ShortArray(1)

        typeface.unicharsToGlyphsInternal(intArrayOf('A'.code), 1, glyphs)

        assertEquals(glyphA, glyphs[0].toInt() and 0xFFFF)
    }

    @Test
    fun `cmap format 6 malformed subtables fail closed`() {
        val entryCountTooLarge = syntheticCmapFormat6('A'.code, intArrayOf(1)).also {
            writeU16(it, 20, 300)
        }
        val truncatedGlyphArray = syntheticCmapFormat6('A'.code, intArrayOf(1, 2)).copyOf(20)
        val declaredLengthTooShort = syntheticCmapFormat6('A'.code, intArrayOf(1, 2)).also {
            writeU16(it, 14, 10)
        }

        assertNull(OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableContent("cmap", "cmap", entryCountTooLarge)))
        assertNull(OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableContent("cmap", "cmap", truncatedGlyphArray)))
        assertNull(OpenTypeTypeface.MakeFromBytes(liberationSansBytes().withTableContent("cmap", "cmap", declaredLengthTooShort)))
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
    fun `drawString renders COLRv0 layers with CPAL default palette`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2]),
        )!!
        val font = SkFont(typeface, 96f)
        val bitmap = SkBitmap(220, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, font, paint)

        assertTrue(bitmap.pixels.count(::isMostlyRed) > 0)
        assertTrue(bitmap.pixels.count(::isMostlyGreen) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyBlack))
        assertEquals(0xFF000000.toInt(), paint.color)
    }

    @Test
    fun `drawString renders mixed COLRv0 and monochrome glyphs in one run`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2]),
        )!!
        val font = SkFont(typeface, 72f)
        val bitmap = SkBitmap(260, 120).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("AB", 12f, 92f, font, paint)

        assertTrue(bitmap.pixels.count(::isMostlyRed) > 0)
        assertTrue(bitmap.pixels.count(::isMostlyGreen) > 0)
        assertTrue(bitmap.pixels.count(::isMostlyBlack) > 0)
    }

    @Test
    fun `drawString renders COLRv0 foreground palette layers with original paint color`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("AB")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes()
                .withTableContent("GPOS", "COLR", syntheticColrV0(glyphs[0], glyphs[1], glyphs[1], 0xFFFF, 0xFFFF))
                .withTableContent("kern", "CPAL", syntheticCpalV0()),
        )!!
        val font = SkFont(typeface, 96f)
        val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF0000FF.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, font, paint)

        assertTrue(bitmap.pixels.count(::isMostlyBlue) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyRed))
        assertEquals(0, bitmap.pixels.count(::isMostlyGreen))
    }

    @Test
    fun `drawString paints later COLRv0 layers over earlier layers`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("AB")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes()
                .withTableContent("GPOS", "COLR", syntheticColrV0(glyphs[0], glyphs[1], glyphs[1]))
                .withTableContent("kern", "CPAL", syntheticCpalV0()),
        )!!
        val font = SkFont(typeface, 96f)
        val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, font, paint)

        assertTrue(bitmap.pixels.count(::isMostlyGreen) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyRed))
    }

    @Test
    fun `makeClone palette index selects COLRv0 CPAL palette`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes()
                .withTableContent("GPOS", "COLR", syntheticColrV0(glyphs[0], glyphs[1], glyphs[2]))
                .withTableContent("kern", "CPAL", syntheticTwoPaletteCpalV0()),
        )!!
        val palette = SkFontArguments.Palette().also { it.index = 1 }
        val clone = typeface.makeClone(SkFontArguments().setPalette(palette)) as OpenTypeTypeface
        val bitmap = SkBitmap(220, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, SkFont(clone, 96f), paint)

        assertTrue(bitmap.pixels.count(::isMostlyBlue) > 0)
        assertTrue(bitmap.pixels.count(::isMostlyRed) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyGreen))
    }

    @Test
    fun `makeClone palette overrides replace selected COLRv0 CPAL entries`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2]),
        )!!
        val palette = SkFontArguments.Palette().also {
            it.overrides = listOf(SkFontArguments.Palette.Override(0, 0xFF0000FF.toInt()))
        }
        val clone = typeface.makeClone(SkFontArguments().setPalette(palette)) as OpenTypeTypeface
        val bitmap = SkBitmap(220, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, SkFont(clone, 96f), paint)

        assertTrue(bitmap.pixels.count(::isMostlyBlue) > 0)
        assertTrue(bitmap.pixels.count(::isMostlyGreen) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyRed))
    }

    @Test
    fun `invalid COLRv0 palette selection falls back to monochrome`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2]),
        )!!

        for (badIndex in listOf(-1, 99)) {
            val palette = SkFontArguments.Palette().also { it.index = badIndex }
            val clone = typeface.makeClone(SkFontArguments().setPalette(palette)) as OpenTypeTypeface
            val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
            val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

            SkCanvas(bitmap).drawString("A", 12f, 112f, SkFont(clone, 96f), paint)

            assertTrue(bitmap.pixels.count(::isMostlyBlack) > 0, "palette index $badIndex")
            assertEquals(0, bitmap.pixels.count(::isMostlyRed), "palette index $badIndex")
            assertEquals(0, bitmap.pixels.count(::isMostlyGreen), "palette index $badIndex")
            assertEquals(0, bitmap.pixels.count(::isMostlyBlue), "palette index $badIndex")
        }
    }

    @Test
    fun `invalid COLRv0 palette override falls back to monochrome`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2]),
        )!!

        for (badIndex in listOf(-1, 99)) {
            val palette = SkFontArguments.Palette().also {
                it.overrides = listOf(SkFontArguments.Palette.Override(badIndex, 0xFF0000FF.toInt()))
            }
            val clone = typeface.makeClone(SkFontArguments().setPalette(palette)) as OpenTypeTypeface
            val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
            val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

            SkCanvas(bitmap).drawString("A", 12f, 112f, SkFont(clone, 96f), paint)

            assertTrue(bitmap.pixels.count(::isMostlyBlack) > 0, "override index $badIndex")
            assertEquals(0, bitmap.pixels.count(::isMostlyRed), "override index $badIndex")
            assertEquals(0, bitmap.pixels.count(::isMostlyGreen), "override index $badIndex")
            assertEquals(0, bitmap.pixels.count(::isMostlyBlue), "override index $badIndex")
        }
    }

    @Test
    fun `drawString falls back to monochrome when color tables are malformed`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes()
                .withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2])
                .withTableLength("COLR", 4),
        )!!
        val font = SkFont(typeface, 96f)
        val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, font, paint)

        assertTrue(bitmap.pixels.count(::isMostlyBlack) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyRed))
        assertEquals(0, bitmap.pixels.count(::isMostlyGreen))
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

    @Test
    fun `COLRv0 and CPAL metadata expose palettes and color layers`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2]),
        )!!

        val palettes = typeface.colorPalettes()
        val layers = typeface.colorLayers(glyphs[0])

        assertEquals(listOf(listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())), palettes)
        assertEquals(listOf(OpenTypeColorLayer(glyphs[1], 0), OpenTypeColorLayer(glyphs[2], 1)), layers)
        assertTrue(typeface.colorLayers(glyphs[1]).isEmpty())
    }

    @Test
    fun `fonts without COLR and CPAL expose no color metadata`() {
        val typeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyph = SkFont(typeface, 12f).textToGlyphs("A").single()

        assertTrue(typeface.colorPalettes().isEmpty())
        assertTrue(typeface.colorLayers(glyph).isEmpty())
    }

    @Test
    fun `malformed color tables fail closed without rejecting the font`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val bytes = liberationSansBytes()
            .withColrCpalFixture(glyphs[0], glyphs[1], glyphs[2])
            .withTableLength("COLR", 4)
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!

        assertTrue(typeface.colorPalettes().isEmpty())
        assertTrue(typeface.colorLayers(glyphs[0]).isEmpty())
    }

    @Test
    fun `oversized color table counts fail closed before expansion`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("ABC")
        val bytes = liberationSansBytes()
            .withTableContent("GPOS", "COLR", syntheticColrV0(glyphs[0], glyphs[1], glyphs[2]))
            .withTableContentKeepingOriginalLength("kern", "CPAL", syntheticOversizedCpalV0())
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!

        assertTrue(typeface.colorPalettes().isEmpty())
        assertTrue(typeface.colorLayers(glyphs[0]).isEmpty())
    }

    @Test
    fun `duplicated COLR layer ranges fail closed before expansion`() {
        val bytes = liberationSansBytes()
            .withTableContentKeepingOriginalLength("GPOS", "COLR", syntheticOversizedColrV0())
            .withTableContent("kern", "CPAL", syntheticCpalV0())
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!

        assertTrue(typeface.colorPalettes().isEmpty())
        assertTrue(typeface.colorLayers(1).isEmpty())
    }

    @Test
    fun `COLRv1 paint graph metadata exposes solid glyph transform subset`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("AB")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes()
                .withTableContent("GPOS", "COLR", syntheticColrV1Transform(glyphs[0], glyphs[1]))
                .withTableContent("kern", "CPAL", syntheticCpalV0()),
        )!!

        val paint = typeface.colorPaint(glyphs[0])

        assertEquals(
            OpenTypeColorPaint.Transform(
                paint = OpenTypeColorPaint.Glyph(
                    glyphId = glyphs[1],
                    paint = OpenTypeColorPaint.Solid(paletteIndex = 1, alpha = 0.5f),
                ),
                xx = 1f,
                yx = 0f,
                xy = 0f,
                yy = 1f,
                dx = 12f,
                dy = -3f,
            ),
            paint,
        )
        assertTrue(typeface.colorLayers(glyphs[0]).isEmpty())
    }

    @Test
    fun `drawString falls back to monochrome for parsed COLRv1 before rendering integration`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("AB")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes()
                .withTableContent("GPOS", "COLR", syntheticColrV1Transform(glyphs[0], glyphs[1]))
                .withTableContent("kern", "CPAL", syntheticCpalV0()),
        )!!
        val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, SkFont(typeface, 96f), paint)

        assertTrue(bitmap.pixels.count(::isMostlyBlack) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyRed))
        assertEquals(0, bitmap.pixels.count(::isMostlyGreen))
        assertEquals(0, bitmap.pixels.count(::isMostlyBlue))
    }

    @Test
    fun `malformed COLRv1 paint graph fails closed without rejecting font`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("AB")
        val bytes = liberationSansBytes()
            .withTableContent("GPOS", "COLR", syntheticColrV1Transform(glyphs[0], glyphs[1]).copyOf(40))
            .withTableContent("kern", "CPAL", syntheticCpalV0())
        val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!

        assertTrue(typeface.colorPalettes().isEmpty())
        assertNull(typeface.colorPaint(glyphs[0]))
    }

    @Test
    fun `SVG table metadata exposes document records without rendering integration`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("AB")
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0h10v10z"/></svg>"""
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withTableContent("GPOS", "SVG ", syntheticSvgTable(glyphs[0], glyphs[1], svg)),
        )!!

        val document = requireNotNull(typeface.svgDocument(glyphs[0]))

        assertEquals(glyphs[0], document.startGlyphId)
        assertEquals(glyphs[1], document.endGlyphId)
        assertEquals(svg, document.text)
        assertEquals(document, typeface.svgDocument(glyphs[1]))
        assertNull(typeface.svgDocument(glyphs[1] + 1))
    }

    @Test
    fun `drawString falls back to monochrome for parsed SVG table before rendering integration`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("A")
        val typeface = OpenTypeTypeface.MakeFromBytes(
            liberationSansBytes().withTableContent("GPOS", "SVG ", syntheticSvgTable(glyphs[0], glyphs[0])),
        )!!
        val bitmap = SkBitmap(180, 140).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val paint = SkPaint(0xFF000000.toInt()).also { it.isAntiAlias = false }

        SkCanvas(bitmap).drawString("A", 12f, 112f, SkFont(typeface, 96f), paint)

        assertTrue(bitmap.pixels.count(::isMostlyBlack) > 0)
        assertEquals(0, bitmap.pixels.count(::isMostlyRed))
        assertEquals(0, bitmap.pixels.count(::isMostlyGreen))
        assertEquals(0, bitmap.pixels.count(::isMostlyBlue))
    }

    @Test
    fun `malformed SVG table fails closed without rejecting font`() {
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(liberationSansBytes())!!
        val glyph = SkFont(baseTypeface, 12f).textToGlyphs("A").single()
        val malformedTables = listOf(
            syntheticSvgTable(glyph, glyph).copyOf(18),
            syntheticSvgTable(glyph, glyph).also { writeU32(it, 2, 0) },
            syntheticSvgTable(glyph, glyph).also { writeU16(it, 10, 0) },
            syntheticSvgTable(glyph, glyph).also { writeU32(it, 16, 0) },
            syntheticSvgTable(glyph, glyph).also { writeU32(it, 20, 0) },
            syntheticSvgTableRecords(
                SyntheticSvgRecord(glyph + 1, glyph + 1),
                SyntheticSvgRecord(glyph, glyph),
            ),
            syntheticSvgTableRecords(
                SyntheticSvgRecord(glyph, glyph + 1),
                SyntheticSvgRecord(glyph + 1, glyph + 2),
            ),
        )

        malformedTables.forEach { table ->
            val bytes = liberationSansBytes().withTableContent("GPOS", "SVG ", table)
            val typeface = OpenTypeTypeface.MakeFromBytes(bytes)!!

            assertNull(typeface.svgDocument(glyph))
        }
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

    private fun isMostlyRed(color: Int): Boolean =
        SkColorGetR(color) > 200 && SkColorGetG(color) < 40 && SkColorGetB(color) < 40

    private fun isMostlyGreen(color: Int): Boolean =
        SkColorGetG(color) > 200 && SkColorGetR(color) < 40 && SkColorGetB(color) < 40

    private fun isMostlyBlue(color: Int): Boolean =
        SkColorGetB(color) > 200 && SkColorGetR(color) < 40 && SkColorGetG(color) < 40

    private fun isMostlyBlack(color: Int): Boolean =
        SkColorGetR(color) < 40 && SkColorGetG(color) < 40 && SkColorGetB(color) < 40

    private fun distortableBounds(typeface: SkTypeface, text: String): org.graphiks.math.SkRect {
        val font = SkFont(typeface, 96f)
        return font.getBounds(font.textToGlyphs(text).single())
    }

    private fun distortablePath(typeface: SkTypeface, text: String): SkPath {
        val font = SkFont(typeface, 96f)
        return requireNotNull(font.getPath(font.textToGlyphs(text).single()))
    }

    private fun variedDistortablePath(typeface: SkTypeface, text: String): SkPath {
        val clone = typeface.makeClone(
            SkFontArguments().setVariationDesignPosition(
                SkFontArguments.VariationPosition(
                    listOf(SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 2f)),
                ),
            ),
        )!!
        return distortablePath(clone, text)
    }

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

    private fun syntheticGvarPrivatePointDeltas(pointCount: Int, pointCountBytes: ByteArray): ByteArray {
        require(pointCount in 1..53)
        val glyphDataOffset = 50
        val points = ByteArray(pointCount + 1)
        points[0] = ((pointCount - 1) and 0x7F).toByte()
        points[1] = 0
        for (i in 1 until pointCount) points[i + 1] = 1
        val xDeltas = ByteArray(pointCount + 1)
        xDeltas[0] = ((pointCount - 1) and 0x3F).toByte()
        xDeltas[1] = 10
        val yDeltas = byteArrayOf((0x80 or (pointCount - 1)).toByte())
        val variationDataSize = pointCountBytes.size + points.size + xDeltas.size + yDeltas.size
        val glyphDataSize = 8 + variationDataSize
        val bytes = ByteArray(glyphDataOffset + glyphDataSize)
        writeU16(bytes, 0, 1) // majorVersion
        writeU16(bytes, 4, 1) // axisCount
        writeU16(bytes, 6, 1) // sharedTupleCount
        writeU32(bytes, 8, 48) // sharedTuplesOffset
        writeU16(bytes, 12, 6) // glyphCount
        writeU16(bytes, 14, 1) // flags: long offsets
        writeU32(bytes, 16, glyphDataOffset)
        val offsets = intArrayOf(0, 0, 0, 0, glyphDataSize, glyphDataSize, glyphDataSize)
        offsets.forEachIndexed { index, value -> writeU32(bytes, 20 + index * 4, value) }
        writeI16(bytes, 48, 0x4000) // shared peak tuple = 1.0

        val glyph = glyphDataOffset
        writeU16(bytes, glyph, 1) // tupleVariationCount
        writeU16(bytes, glyph + 2, 8) // offsetToData
        writeU16(bytes, glyph + 4, variationDataSize)
        writeU16(bytes, glyph + 6, 0x2000) // private point numbers, shared tuple 0
        var off = glyph + 8
        pointCountBytes.copyInto(bytes, off); off += pointCountBytes.size
        points.copyInto(bytes, off); off += points.size
        xDeltas.copyInto(bytes, off); off += xDeltas.size
        yDeltas.copyInto(bytes, off)
        return bytes
    }

    private fun syntheticCmapFormat0(glyphA: Int, glyphEAcute: Int): ByteArray {
        val subtableOffset = 12
        val bytes = ByteArray(subtableOffset + 262)
        writeU16(bytes, 2, 1) // numTables
        writeU16(bytes, 4, 1) // platform: Macintosh
        writeU16(bytes, 6, 0) // encoding: Roman
        writeU32(bytes, 8, subtableOffset)
        writeU16(bytes, subtableOffset, 0) // format
        writeU16(bytes, subtableOffset + 2, 262) // length
        bytes[subtableOffset + 6 + 'A'.code] = glyphA.toByte()
        bytes[subtableOffset + 6 + 0x8E] = glyphEAcute.toByte() // MacRoman e acute
        return bytes
    }

    private fun syntheticCmapFormat6(firstCode: Int, glyphs: IntArray): ByteArray {
        val subtableOffset = 12
        val bytes = ByteArray(subtableOffset + 10 + glyphs.size * 2)
        writeU16(bytes, 2, 1) // numTables
        writeU16(bytes, 4, 1) // platform: Macintosh
        writeU16(bytes, 6, 0) // encoding: Roman
        writeU32(bytes, 8, subtableOffset)
        writeCmapFormat6Subtable(bytes, subtableOffset, firstCode, glyphs)
        return bytes
    }

    private fun syntheticCmapWithFormat4AndMacRomanFormat6(format4Glyph: Int, format6Glyph: Int): ByteArray {
        val format4Offset = 20
        val format6Offset = format4Offset + 32
        val bytes = ByteArray(format6Offset + 12)
        writeU16(bytes, 2, 2) // numTables
        writeU16(bytes, 4, 3) // platform: Windows
        writeU16(bytes, 6, 1) // encoding: Unicode BMP
        writeU32(bytes, 8, format4Offset)
        writeU16(bytes, 12, 1) // platform: Macintosh
        writeU16(bytes, 14, 0) // encoding: Roman
        writeU32(bytes, 16, format6Offset)
        writeCmapFormat4SingleGlyph(bytes, format4Offset, 'A'.code, format4Glyph)
        writeCmapFormat6Subtable(bytes, format6Offset, 'A'.code, intArrayOf(format6Glyph))
        return bytes
    }

    private fun writeCmapFormat6Subtable(bytes: ByteArray, off: Int, firstCode: Int, glyphs: IntArray) {
        writeU16(bytes, off, 6) // format
        writeU16(bytes, off + 2, 10 + glyphs.size * 2) // length
        writeU16(bytes, off + 6, firstCode)
        writeU16(bytes, off + 8, glyphs.size)
        glyphs.forEachIndexed { index, glyph -> writeU16(bytes, off + 10 + index * 2, glyph) }
    }

    private fun writeCmapFormat4SingleGlyph(bytes: ByteArray, off: Int, codepoint: Int, glyph: Int) {
        writeU16(bytes, off, 4) // format
        writeU16(bytes, off + 2, 32) // length
        writeU16(bytes, off + 6, 4) // segCountX2
        writeU16(bytes, off + 8, 4) // searchRange
        writeU16(bytes, off + 10, 1) // entrySelector
        writeU16(bytes, off + 12, 0) // rangeShift
        writeU16(bytes, off + 14, codepoint)
        writeU16(bytes, off + 16, 0xFFFF)
        writeU16(bytes, off + 20, codepoint)
        writeU16(bytes, off + 22, 0xFFFF)
        writeI16(bytes, off + 24, glyph - codepoint)
        writeU16(bytes, off + 26, 1)
        writeU16(bytes, off + 28, 0)
        writeU16(bytes, off + 30, 0)
    }

    private fun syntheticCpalV0(): ByteArray {
        return syntheticCpalV0(
            listOf(
                intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
            ),
        )
    }

    private fun syntheticTwoPaletteCpalV0(): ByteArray {
        return syntheticCpalV0(
            listOf(
                intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
                intArrayOf(0xFF0000FF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
            ),
        )
    }

    private fun syntheticCpalV0(palettes: List<IntArray>): ByteArray {
        require(palettes.isNotEmpty())
        val entryCount = palettes.first().size
        require(entryCount > 0)
        require(palettes.all { it.size == entryCount })
        val colorRecordsOffset = 12 + palettes.size * 2
        val bytes = ByteArray(colorRecordsOffset + palettes.size * entryCount * 4)
        writeU16(bytes, 2, entryCount) // numPaletteEntries
        writeU16(bytes, 4, palettes.size) // numPalettes
        writeU16(bytes, 6, palettes.size * entryCount) // numColorRecords
        writeU32(bytes, 8, colorRecordsOffset) // colorRecordsArrayOffset
        palettes.forEachIndexed { paletteIndex, _ ->
            writeU16(bytes, 12 + paletteIndex * 2, paletteIndex * entryCount) // firstColorRecordIndex
        }
        var off = colorRecordsOffset
        for (palette in palettes) {
            for (color in palette) {
                writeBgra(
                    bytes,
                    off,
                    blue = SkColorGetB(color),
                    green = SkColorGetG(color),
                    red = SkColorGetR(color),
                    alpha = color ushr 24,
                )
                off += 4
            }
        }
        return bytes
    }

    private fun syntheticOversizedCpalV0(): ByteArray {
        val paletteCount = 1024
        val colorRecordsOffset = 12 + paletteCount * 2
        val bytes = ByteArray(colorRecordsOffset + 4)
        writeU16(bytes, 2, 1) // numPaletteEntries
        writeU16(bytes, 4, paletteCount)
        writeU16(bytes, 6, 1) // numColorRecords
        writeU32(bytes, 8, colorRecordsOffset)
        writeBgra(bytes, colorRecordsOffset, blue = 0, green = 0, red = 0xFF, alpha = 0xFF)
        return bytes
    }

    private fun syntheticColrV0(baseGlyph: Int, layerGlyph0: Int, layerGlyph1: Int): ByteArray {
        return syntheticColrV0(baseGlyph, layerGlyph0, layerGlyph1, 0, 1)
    }

    private fun syntheticColrV0(
        baseGlyph: Int,
        layerGlyph0: Int,
        layerGlyph1: Int,
        paletteIndex0: Int,
        paletteIndex1: Int,
    ): ByteArray {
        val bytes = ByteArray(28)
        writeU16(bytes, 2, 1) // numBaseGlyphRecords
        writeU32(bytes, 4, 14) // baseGlyphRecordsOffset
        writeU32(bytes, 8, 20) // layerRecordsOffset
        writeU16(bytes, 12, 2) // numLayerRecords
        writeU16(bytes, 14, baseGlyph)
        writeU16(bytes, 16, 0) // firstLayerIndex
        writeU16(bytes, 18, 2) // numLayers
        writeU16(bytes, 20, layerGlyph0)
        writeU16(bytes, 22, paletteIndex0) // paletteIndex
        writeU16(bytes, 24, layerGlyph1)
        writeU16(bytes, 26, paletteIndex1) // paletteIndex
        return bytes
    }

    private fun syntheticOversizedColrV0(): ByteArray {
        val baseGlyphCount = 300
        val layerCount = 256
        val baseOffset = 14
        val layerOffset = baseOffset + baseGlyphCount * 6
        val bytes = ByteArray(layerOffset + layerCount * 4)
        writeU16(bytes, 2, baseGlyphCount)
        writeU32(bytes, 4, baseOffset)
        writeU32(bytes, 8, layerOffset)
        writeU16(bytes, 12, layerCount)
        repeat(baseGlyphCount) {
            val off = baseOffset + it * 6
            writeU16(bytes, off, it + 1)
            writeU16(bytes, off + 2, 0)
            writeU16(bytes, off + 4, layerCount)
        }
        repeat(layerCount) {
            val off = layerOffset + it * 4
            writeU16(bytes, off, it + 1)
            writeU16(bytes, off + 2, 0)
        }
        return bytes
    }

    private fun syntheticColrV1Transform(baseGlyph: Int, glyph: Int): ByteArray {
        val baseGlyphListOffset = 34
        val rootPaintOffset = baseGlyphListOffset + 10
        val glyphPaintOffset = rootPaintOffset + 7
        val solidPaintOffset = glyphPaintOffset + 6
        val transformOffset = solidPaintOffset + 5
        val bytes = ByteArray(transformOffset + 24)
        writeU16(bytes, 0, 1) // version
        writeU32(bytes, 14, baseGlyphListOffset) // baseGlyphListOffset

        writeU32(bytes, baseGlyphListOffset, 1) // numBaseGlyphPaintRecords
        writeU16(bytes, baseGlyphListOffset + 4, baseGlyph)
        writeU32(bytes, baseGlyphListOffset + 6, rootPaintOffset - baseGlyphListOffset)

        bytes[rootPaintOffset] = 12 // PaintTransform
        writeU24(bytes, rootPaintOffset + 1, glyphPaintOffset - rootPaintOffset)
        writeU24(bytes, rootPaintOffset + 4, transformOffset - rootPaintOffset)

        bytes[glyphPaintOffset] = 10 // PaintGlyph
        writeU24(bytes, glyphPaintOffset + 1, solidPaintOffset - glyphPaintOffset)
        writeU16(bytes, glyphPaintOffset + 4, glyph)

        bytes[solidPaintOffset] = 2 // PaintSolid
        writeU16(bytes, solidPaintOffset + 1, 1) // paletteIndex
        writeI16(bytes, solidPaintOffset + 3, 0x2000) // alpha = 0.5

        writeFixed16Dot16(bytes, transformOffset, 1f) // xx
        writeFixed16Dot16(bytes, transformOffset + 4, 0f) // yx
        writeFixed16Dot16(bytes, transformOffset + 8, 0f) // xy
        writeFixed16Dot16(bytes, transformOffset + 12, 1f) // yy
        writeFixed16Dot16(bytes, transformOffset + 16, 12f) // dx
        writeFixed16Dot16(bytes, transformOffset + 20, -3f) // dy
        return bytes
    }

    private fun syntheticSvgTable(
        startGlyphId: Int,
        endGlyphId: Int,
        svg: String = """<svg xmlns="http://www.w3.org/2000/svg"><rect width="10" height="10"/></svg>""",
    ): ByteArray =
        syntheticSvgTableRecords(SyntheticSvgRecord(startGlyphId, endGlyphId, svg))

    private fun syntheticSvgTableRecords(
        vararg records: SyntheticSvgRecord,
    ): ByteArray {
        val documentListOffset = 10
        val recordBytes = records.map { it.svg.toByteArray(Charsets.UTF_8) }
        val recordsSize = records.size * 12
        var nextDocumentOffset = 2 + recordsSize
        val bytes = ByteArray(documentListOffset + nextDocumentOffset + recordBytes.sumOf { it.size })
        writeU32(bytes, 2, documentListOffset) // svgDocumentListOffset
        writeU16(bytes, documentListOffset, records.size) // numEntries
        records.forEachIndexed { index, record ->
            val svgBytes = recordBytes[index]
            val recordOffset = documentListOffset + 2 + index * 12
            writeU16(bytes, recordOffset, record.startGlyphId)
            writeU16(bytes, recordOffset + 2, record.endGlyphId)
            writeU32(bytes, recordOffset + 4, nextDocumentOffset)
            writeU32(bytes, recordOffset + 8, svgBytes.size)
            svgBytes.copyInto(bytes, documentListOffset + nextDocumentOffset)
            nextDocumentOffset += svgBytes.size
        }
        return bytes
    }

    private data class SyntheticSvgRecord(
        val startGlyphId: Int,
        val endGlyphId: Int,
        val svg: String = """<svg xmlns="http://www.w3.org/2000/svg"><rect width="10" height="10"/></svg>""",
    )

    private fun writeBgra(bytes: ByteArray, off: Int, blue: Int, green: Int, red: Int, alpha: Int) {
        bytes[off] = blue.toByte()
        bytes[off + 1] = green.toByte()
        bytes[off + 2] = red.toByte()
        bytes[off + 3] = alpha.toByte()
    }

    private fun writeU24(bytes: ByteArray, off: Int, value: Int) {
        bytes[off] = (value ushr 16).toByte()
        bytes[off + 1] = (value ushr 8).toByte()
        bytes[off + 2] = value.toByte()
    }

    private fun writeFixed16Dot16(bytes: ByteArray, off: Int, value: Float) {
        writeU32(bytes, off, (value * 65536f).toInt())
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
