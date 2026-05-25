package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
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
 * Portable palette-override GM inspired by Skia's
 * [`gm/palette.cpp::FontPaletteGM`](https://github.com/google/skia/blob/main/gm/palette.cpp).
 *
 * The upstream GM uses `test_glyphs-glyf_colr_1.ttf`, which is not
 * shipped here. This GM keeps the same palette-selection contract on
 * the portable path by injecting tiny COLRv1/CPAL tables into bundled
 * Liberation Sans and rendering a single colour glyph through
 * [SkTypeface.makeClone].
 *
 * ## Constructor parameters
 *
 * Mirrors upstream's
 * `FontPaletteGM(const char* test_name, const SkFontArguments::Palette& paletteOverride)`
 * ctor. The 5 named test variants are exposed through [Companion]
 * factories ([defaultPalette], [light], [dark], [one], [all]) ; the
 * no-arg constructor defaults to the `default` variant (matching
 * upstream's first `DEF_GM` line).
 */
public class FontPaletteGM(
    private val testName: String,
    private val paletteOverride: SkFontArguments.Palette,
) : GM() {

    /** No-arg ctor — defaults to the `default` variant (no overrides). */
    public constructor() : this("default", SkFontArguments.Palette())

    private var typefaceDefault: SkTypeface? = null
    private var typefaceCloned: SkTypeface? = null

    override fun onOnceBeforeDraw() {
        val baseBytes = ToolUtils.GetResourceAsData("fonts/liberation/LiberationSans-Regular.ttf")?.toByteArray()
            ?: return
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(baseBytes) ?: return
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs(TEXT)
        val colorGlyphs = if (glyphs.size == TEXT.length) {
            glyphs.map { it and 0xFFFF }
        } else {
            return
        }
        val colrBytes = baseBytes
            .withFontPaletteTableContent("GPOS", "COLR", fontPaletteColrV1Solids(colorGlyphs))
            .withFontPaletteTableContent("kern", "CPAL", fontPaletteCpalV0())
        val typeface = OpenTypeTypeface.MakeFromBytes(colrBytes) ?: return
        val paletteArgs = SkFontArguments().setPalette(paletteOverride)

        typefaceDefault = typeface
        typefaceCloned = typeface.makeClone(paletteArgs)
    }

    override fun getName(): String = "font_palette_$testName"

    override fun getISize(): SkISize = SkISize.Make(1000, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorWHITE)
        val paint = SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false }

        c.translate(10f, 20f)
        typefaceDefault?.let {
            c.drawString(TEXT, 0f, 220f, SkFont(it, 200f), paint)
        }
        typefaceCloned?.let {
            c.drawString(TEXT, 440f, 220f, SkFont(it, 200f), paint)
        }
    }

    public companion object {
        private const val TEXT: String = "ABC"

        /** Upstream `kColrCpalTestFontPath` (palette.cpp line 41). */
        public const val K_COLR_CPAL_TEST_FONT_PATH: String = "fonts/test_glyphs-glyf_colr_1.ttf"

        /**
         * Mirrors upstream's `ColrV1TestDefinitions::color_circles_palette`
         * — the two synthetic glyphs that drive the palette swatch test.
         * Copied from Google Fonts' colour-circles markdown, see the
         * cpp header comment.
         */
        public val COLOR_CIRCLES_PALETTE: IntArray = intArrayOf(0xf0e00, 0xf0e01)

        // ─── Per-variant palette ctor arguments (cpp lines 43..77) ──

        /**
         * Mirrors upstream's
         * `kColorOverridesAll[]` — gradient of dark→light purple/pink
         * for the circle palette test glyph, randomly ordered (upstream
         * comment: "Randomly ordered with `shuf`"), plus a repeat
         * (entry 6 — later overrides override earlier overrides) and
         * three out-of-bounds entries (12 / 13 / -1 ; the font only
         * has 12 palette entries).
         *
         * Public so [FontPaletteTest] / sibling unit tests can pin the
         * canonical contract.
         */
        public val K_COLOR_OVERRIDES_ALL: List<SkFontArguments.Palette.Override> = listOf(
            SkFontArguments.Palette.Override(index = 6, color = 0xffffff00.toInt()),
            SkFontArguments.Palette.Override(index = 2, color = 0xff76078f.toInt()),
            SkFontArguments.Palette.Override(index = 4, color = 0xffb404c4.toInt()),
            SkFontArguments.Palette.Override(index = 1, color = 0xff510970.toInt()),
            SkFontArguments.Palette.Override(index = 6, color = 0xfffa00ff.toInt()),
            SkFontArguments.Palette.Override(index = 8, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 10, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 9, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 7, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 11, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 0, color = 0xff310b55.toInt()),
            SkFontArguments.Palette.Override(index = 3, color = 0xff9606aa.toInt()),
            SkFontArguments.Palette.Override(index = 5, color = 0xffd802e2.toInt()),
            SkFontArguments.Palette.Override(index = 13, color = 0xff00ffff.toInt()),
            SkFontArguments.Palette.Override(index = 12, color = 0xff00ffff.toInt()),
            // Upstream casts `static_cast<uint16_t>(-1)` — the unsigned
            // wrap reads back as 0xffff = 65535 (still out of bounds for
            // a 12-entry font, expected to be silently ignored).
            SkFontArguments.Palette.Override(index = 0xffff, color = 0xff00ff00.toInt()),
        )

        /** Mirrors upstream's `kColorOverridesOne[]`. */
        public val K_COLOR_OVERRIDES_ONE: List<SkFontArguments.Palette.Override> = listOf(
            SkFontArguments.Palette.Override(index = 2, color = 0xff02dfe2.toInt()),
        )

        // ─── DEF_GM factory methods (cpp lines 166..170) ────────────

        /** `DEF_GM(return new FontPaletteGM("default", SkFontArguments::Palette()))`. */
        public fun defaultPalette(): FontPaletteGM = FontPaletteGM(
            testName = "default",
            paletteOverride = SkFontArguments.Palette(),
        )

        /** `DEF_GM(return new FontPaletteGM("light", kLightPaletteOverride))`. */
        public fun light(): FontPaletteGM = FontPaletteGM(
            testName = "light",
            paletteOverride = SkFontArguments.Palette().apply { index = 2 },
        )

        /** `DEF_GM(return new FontPaletteGM("dark", kDarkPaletteOverride))`. */
        public fun dark(): FontPaletteGM = FontPaletteGM(
            testName = "dark",
            paletteOverride = SkFontArguments.Palette().apply { index = 1 },
        )

        /** `DEF_GM(return new FontPaletteGM("one", kOnePaletteOverride))`. */
        public fun one(): FontPaletteGM = FontPaletteGM(
            testName = "one",
            paletteOverride = SkFontArguments.Palette().apply {
                index = 0
                overrides = K_COLOR_OVERRIDES_ONE
            },
        )

        /** `DEF_GM(return new FontPaletteGM("all", kAllPaletteOverride))`. */
        public fun all(): FontPaletteGM = FontPaletteGM(
            testName = "all",
            paletteOverride = SkFontArguments.Palette().apply {
                index = 0
                overrides = K_COLOR_OVERRIDES_ALL
            },
        )
    }
}

private fun ByteArray.withFontPaletteTableContent(from: String, to: String, content: ByteArray): ByteArray {
    require(from.length == 4)
    require(to.length == 4)
    val copy = copyOf()
    val record = copy.fontPaletteTableDirectoryRecord(from)
    val offset = fontPaletteReadU32(copy, record + 8)
    val length = fontPaletteReadU32(copy, record + 12)
    require(content.size <= length) { "$from table too small for synthetic $to table" }
    to.toByteArray(Charsets.ISO_8859_1).copyInto(copy, record)
    fontPaletteWriteU32(copy, record + 12, content.size)
    content.copyInto(copy, offset)
    for (i in offset + content.size until offset + length) copy[i] = 0
    return copy
}

private fun ByteArray.fontPaletteTableDirectoryRecord(tag: String): Int {
    require(tag.length == 4)
    val numTables = fontPaletteReadU16(this, 4)
    var off = 12
    repeat(numTables) {
        if (String(this, off, 4, Charsets.ISO_8859_1) == tag) return off
        off += 16
    }
    error("Missing table: $tag")
}

private fun fontPaletteCpalV0(): ByteArray {
    val palettes = listOf(
        fontPaletteEntries(0xff2558d9.toInt()),
        fontPaletteEntries(0xff9a1f1f.toInt()),
        fontPaletteEntries(0xff159447.toInt()),
    )
    val numEntries = palettes.first().size
    val colorRecordsOffset = 12 + palettes.size * 2
    val bytes = ByteArray(colorRecordsOffset + palettes.size * numEntries * 4)
    fontPaletteWriteU16(bytes, 2, numEntries)
    fontPaletteWriteU16(bytes, 4, palettes.size)
    fontPaletteWriteU16(bytes, 6, palettes.size * numEntries)
    fontPaletteWriteU32(bytes, 8, colorRecordsOffset)
    palettes.forEachIndexed { paletteIndex, _ ->
        fontPaletteWriteU16(bytes, 12 + paletteIndex * 2, paletteIndex * numEntries)
    }
    palettes.flatten().forEachIndexed { index, color ->
        val off = colorRecordsOffset + index * 4
        bytes[off] = (color and 0xFF).toByte()
        bytes[off + 1] = ((color ushr 8) and 0xFF).toByte()
        bytes[off + 2] = ((color ushr 16) and 0xFF).toByte()
        bytes[off + 3] = ((color ushr 24) and 0xFF).toByte()
    }
    return bytes
}

private fun fontPaletteEntries(colorAtIndexTwo: Int): List<Int> =
    List(12) { index -> if (index == 2) colorAtIndexTwo else 0xff888888.toInt() }

private fun fontPaletteColrV1Solids(glyphs: List<Int>): ByteArray {
    val paletteIndexes = listOf(1, 2, 6)
    require(glyphs.size == paletteIndexes.size)
    val baseGlyphListOffset = 34
    val recordStart = baseGlyphListOffset + 4
    val paintStart = recordStart + glyphs.size * 6
    val paintRecordSize = 11
    val bytes = ByteArray(paintStart + glyphs.size * paintRecordSize)
    fontPaletteWriteU16(bytes, 0, 1)
    fontPaletteWriteU32(bytes, 14, baseGlyphListOffset)

    fontPaletteWriteU32(bytes, baseGlyphListOffset, glyphs.size)
    glyphs.forEachIndexed { index, glyph ->
        val recordOffset = recordStart + index * 6
        val glyphPaintOffset = paintStart + index * paintRecordSize
        val solidPaintOffset = glyphPaintOffset + 6
        fontPaletteWriteU16(bytes, recordOffset, glyph)
        fontPaletteWriteU32(bytes, recordOffset + 2, glyphPaintOffset - baseGlyphListOffset)

        bytes[glyphPaintOffset] = 10
        fontPaletteWriteU24(bytes, glyphPaintOffset + 1, solidPaintOffset - glyphPaintOffset)
        fontPaletteWriteU16(bytes, glyphPaintOffset + 4, glyph)
        bytes[solidPaintOffset] = 2
        fontPaletteWriteU16(bytes, solidPaintOffset + 1, paletteIndexes[index])
        fontPaletteWriteU16(bytes, solidPaintOffset + 3, 0x4000)
    }
    return bytes
}

private fun fontPaletteReadU16(bytes: ByteArray, off: Int): Int =
    ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)

private fun fontPaletteReadU32(bytes: ByteArray, off: Int): Int =
    ((bytes[off].toInt() and 0xFF) shl 24) or
        ((bytes[off + 1].toInt() and 0xFF) shl 16) or
        ((bytes[off + 2].toInt() and 0xFF) shl 8) or
        (bytes[off + 3].toInt() and 0xFF)

private fun fontPaletteWriteU16(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 8).toByte()
    bytes[off + 1] = value.toByte()
}

private fun fontPaletteWriteU24(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 16).toByte()
    bytes[off + 1] = (value ushr 8).toByte()
    bytes[off + 2] = value.toByte()
}

private fun fontPaletteWriteU32(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 24).toByte()
    bytes[off + 1] = (value ushr 16).toByte()
    bytes[off + 2] = (value ushr 8).toByte()
    bytes[off + 3] = value.toByte()
}
