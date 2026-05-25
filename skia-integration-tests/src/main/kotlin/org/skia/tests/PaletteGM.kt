package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTypeface
import org.skia.foundation.opentype.OpenTypeTypeface
import org.skia.tools.ToolUtils

/**
 * Palette surface GM for the portable OpenType path.
 *
 * The upstream `gm/palette.cpp` path renders a COLR v1 colour font with
 * CPAL palette selection and overrides. Until #1020 accepts binary
 * fixtures and upstream-style references, this GM keeps the actionable
 * portable contract alive without touching the `cpu-raster` `SkColrV1`
 * stub: build a tiny COLRv1/CPAL font from bundled Liberation Sans,
 * draw its default palette, then draw a [SkTypeface.makeClone] variant
 * with palette overrides.
 */
public class PaletteGM : GM() {
    private var typefaceDefault: OpenTypeTypeface? = null
    private var typefaceOverride: SkTypeface? = null

    override fun getName(): String = "palette"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onOnceBeforeDraw() {
        val baseBytes = ToolUtils.GetResourceAsData("fonts/liberation/LiberationSans-Regular.ttf")?.toByteArray()
            ?: return
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(baseBytes) ?: return
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs("A")
        val glyph = if (glyphs.size == 1) glyphs[0] and 0xFFFF else return
        val colrBytes = baseBytes
            .withTableContent("GPOS", "COLR", syntheticColrV1Solid(glyph, paletteIndex = 1))
            .withTableContent("kern", "CPAL", syntheticCpalV0())
        val tf = OpenTypeTypeface.MakeFromBytes(colrBytes) ?: return
        typefaceDefault = tf
        val palette = SkFontArguments.Palette().also { p ->
            p.overrides = listOf(
                SkFontArguments.Palette.Override(index = 1, color = SK_ColorBLUE),
                SkFontArguments.Palette.Override(index = 1, color = SK_ColorGREEN),
                SkFontArguments.Palette.Override(index = -1, color = SK_ColorBLACK),
                SkFontArguments.Palette.Override(index = BASE_PALETTE.size, color = SK_ColorBLACK),
            )
        }
        typefaceOverride = tf.makeClone(SkFontArguments().setPalette(palette))
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorWHITE)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }
        typefaceDefault?.let {
            c.drawString("A", 56f, 112f, SkFont(it, 96f), paint)
        }
        typefaceOverride?.let {
            c.drawString("A", 56f, 220f, SkFont(it, 96f), paint)
        }
    }

    public companion object {
        public val BASE_PALETTE: List<Int> = listOf(SK_ColorRED, SK_ColorBLUE)
    }
}

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

private fun syntheticCpalV0(): ByteArray {
    val colorRecordsOffset = 14
    val bytes = ByteArray(colorRecordsOffset + PaletteGM.BASE_PALETTE.size * 4)
    writeU16(bytes, 2, PaletteGM.BASE_PALETTE.size)
    writeU16(bytes, 4, 1)
    writeU16(bytes, 6, PaletteGM.BASE_PALETTE.size)
    writeU32(bytes, 8, colorRecordsOffset)
    writeU16(bytes, 12, 0)
    PaletteGM.BASE_PALETTE.forEachIndexed { index, color ->
        val off = colorRecordsOffset + index * 4
        bytes[off] = (color and 0xFF).toByte()
        bytes[off + 1] = ((color ushr 8) and 0xFF).toByte()
        bytes[off + 2] = ((color ushr 16) and 0xFF).toByte()
        bytes[off + 3] = ((color ushr 24) and 0xFF).toByte()
    }
    return bytes
}

private fun syntheticColrV1Solid(glyph: Int, paletteIndex: Int): ByteArray {
    val baseGlyphListOffset = 34
    val glyphPaintOffset = baseGlyphListOffset + 10
    val solidPaintOffset = glyphPaintOffset + 6
    val bytes = ByteArray(solidPaintOffset + 5)
    writeU16(bytes, 0, 1)
    writeU32(bytes, 14, baseGlyphListOffset)

    writeU32(bytes, baseGlyphListOffset, 1)
    writeU16(bytes, baseGlyphListOffset + 4, glyph)
    writeU32(bytes, baseGlyphListOffset + 6, glyphPaintOffset - baseGlyphListOffset)

    bytes[glyphPaintOffset] = 10
    writeU24(bytes, glyphPaintOffset + 1, solidPaintOffset - glyphPaintOffset)
    writeU16(bytes, glyphPaintOffset + 4, glyph)

    bytes[solidPaintOffset] = 2
    writeU16(bytes, solidPaintOffset + 1, paletteIndex)
    writeU16(bytes, solidPaintOffset + 3, 0x4000)
    return bytes
}

private fun readU16(bytes: ByteArray, off: Int): Int =
    ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)

private fun readU32(bytes: ByteArray, off: Int): Int =
    ((bytes[off].toInt() and 0xFF) shl 24) or
        ((bytes[off + 1].toInt() and 0xFF) shl 16) or
        ((bytes[off + 2].toInt() and 0xFF) shl 8) or
        (bytes[off + 3].toInt() and 0xFF)

private fun writeU16(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 8).toByte()
    bytes[off + 1] = value.toByte()
}

private fun writeU24(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 16).toByte()
    bytes[off + 1] = (value ushr 8).toByte()
    bytes[off + 2] = value.toByte()
}

private fun writeU32(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 24).toByte()
    bytes[off + 1] = (value ushr 16).toByte()
    bytes[off + 2] = (value ushr 8).toByte()
    bytes[off + 3] = value.toByte()
}
