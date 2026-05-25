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
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.foundation.opentype.OpenTypeTypeface
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/colrv1.cpp`](https://github.com/google/skia/blob/main/gm/colrv1.cpp)
 * — exercises the COLR v1 colour-glyph paint-graph resolver across 60+
 * test glyphs from Google Fonts' `test_glyphs-glyf_colr_1[_variable].ttf`
 * synthetic test font. Each `DEF_GM` instance picks one named category
 * (`gradient_stops_repeat`, `sweep_varsweep`, `paint_scale`, …) and
 * draws its codepoint group at 4 text sizes (12 / 18 / 30 / 120 pt) in
 * 4 colours (black / green / red / blue), optionally pre-rotated and
 * skewed and with the variable-font axes pinned to specific design
 * coordinates.
 *
 * ## Portable fallback status
 *
 * The portable OpenType backend now renders the supported COLR v1
 * paint graph subset. The upstream `test_glyphs-glyf_colr_1.ttf`
 * fixtures are bundled as test resources with their Apache-2.0 license.
 * When those fixtures are absent in a downstream build, the default
 * no-variation GM falls back to a tiny generated COLRv1/CPAL font based
 * on bundled Liberation Sans. Full variable-axis and reference-image
 * parity remain follow-up scope:
 *
 *  1. **Variable COLRv1 parity** — upstream wires some variable-axis
 *     cases through Google's
 *     [`fontations`](https://github.com/googlefonts/fontations) Rust
 *     crate. The pure Kotlin renderer currently covers base/default
 *     COLR variation paint values, not COLR `ItemVariationStore` deltas.
 *  2. **GM references** — some upstream reference images remain broader
 *     than the currently-supported pure Kotlin COLRv1 paint subset.
 *     Reference parity is tracked in #1020.
 *
 * The upstream body below is preserved for real fixtures. The fallback
 * path is intentionally smaller: it draws `ABCD` through COLRv1 solid
 * paints so the integration suite can verify portable colour-glyph
 * rendering without AWT/JNI or binary upstream assets.
 *
 * ## Constructor parameters
 *
 * Mirrors upstream's `ColrV1GM(name, codepoints, skewX, rotateDeg,
 * variations)` ctor — every `DEF_GM` line in the cpp picks one of the
 * pre-defined codepoint arrays from `ColrV1TestDefinitions` and pairs
 * it with a transform / variation tuple. The 18 named test categories
 * are exposed through [Companion] constants below ; the no-arg
 * constructor defaults to `gradient_stops_repeat` (the smallest, most
 * representative variant).
 */
public class ColrV1GM(
    private val testName: String,
    private val codepoints: IntArray,
    private val skewX: Float,
    private val rotateDeg: Float,
    private val variations: List<SkFontArguments.VariationPosition.Coordinate>,
) : GM() {

    /** No-arg ctor — defaults to the `gradient_stops_repeat` variant. */
    public constructor() : this(
        testName = "gradient_stops_repeat",
        codepoints = GRADIENT_STOPS_REPEAT,
        skewX = 0f,
        rotateDeg = 0f,
        variations = emptyList(),
    )

    private val variationPosition: SkFontArguments.VariationPosition =
        SkFontArguments.VariationPosition(variations)

    private var typeface: SkTypeface? = null
    private var fallbackText: String? = null
    public var didLoadBundledFixture: Boolean = false
        private set

    override fun onOnceBeforeDraw() {
        val resource = if (variations.isNotEmpty()) K_TEST_FONT_NAME_VARIABLE else K_TEST_FONT_NAME
        typeface = ToolUtils.CreateTypefaceFromResource(resource)
        didLoadBundledFixture = typeface != null
        if (typeface != null || variations.isNotEmpty()) return

        val baseBytes = ToolUtils.GetResourceAsData("fonts/liberation/LiberationSans-Regular.ttf")?.toByteArray()
            ?: return
        val baseTypeface = OpenTypeTypeface.MakeFromBytes(baseBytes) ?: return
        val glyphs = SkFont(baseTypeface, 12f).textToGlyphs(FALLBACK_TEXT)
        if (glyphs.size != FALLBACK_TEXT.length) return
        val colrBytes = baseBytes
            .withColrV1SubsetTableContent("GPOS", "COLR", colrV1SubsetColr(glyphs.map { it and 0xFFFF }))
            .withColrV1SubsetTableContent("kern", "CPAL", colrV1SubsetCpal())
        typeface = OpenTypeTypeface.MakeFromBytes(colrBytes)
        fallbackText = typeface?.let { FALLBACK_TEXT }
    }

    override fun getName(): String {
        val sb = StringBuilder("colrv1_").append(testName)
        if (skewX != 0f) sb.append("_skew_%.2f".format(skewX))
        if (rotateDeg != 0f) sb.append("_rotate_%.2f".format(rotateDeg))
        for (c in variationPosition.coordinates) {
            val tagName = SkFontVariation.Tag(c.axis).toString()
            sb.append("_%s_%.2f".format(tagName, c.value))
        }
        return sb.toString()
    }

    override fun getISize(): SkISize {
        // Sweep tests get a slightly wider canvas so that glyphs from one
        // group fit in one row — mirrors upstream's `equals("sweep_varsweep")`
        // branch.
        return if (testName == "sweep_varsweep") {
            SkISize.Make(X_WIDTH + 500, X_WIDTH)
        } else {
            SkISize.Make(X_WIDTH, X_WIDTH)
        }
    }

    /**
     * Mirrors upstream's `makeVariedTypeface()` — clones the base
     * typeface with the configured variation coordinates applied. The
     * COLR v1 test font uses custom axes (`SWPS`, `COL1`, `GRR0`, …);
     * those remain gated by the upstream fixture/reference path tracked
     * in #1020, so this stays a compile-pinned call graph for now.
     */
    private fun makeVariedTypeface(): SkTypeface? {
        val tf = typeface ?: return null
        if (variationPosition.coordinates.isEmpty()) return tf
        val args = SkFontArguments().setVariationDesignPosition(variationPosition)
        return tf.makeClone(args)
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        canvas.drawColor(SK_ColorWHITE)
        val paint = SkPaint()

        canvas.translate(X_TRANSLATE.toFloat(), 20f)

        val tf = typeface ?: return
        val text = fallbackText
        if (text != null) {
            canvas.drawString(text, 0f, 180f, SkFont(tf, 160f), SkPaint(SK_ColorBLACK).also { it.isAntiAlias = false })
            return
        }

        val upstreamTypeface = typeface ?: run {
            // Mirror upstream's `errorMsg = "Did not recognize COLR v1
            // font format."` + `DrawResult::kSkip` — without the test
            // font, there's nothing further to draw. Leave the canvas
            // cleared to white as upstream does before the kSkip return.
            return
        }

        canvas.rotate(rotateDeg)
        canvas.skew(skewX, 0f)

        val variedTypeface = makeVariedTypeface() ?: upstreamTypeface
        val font = SkFont(variedTypeface)

        val metrics = SkFontMetrics()
        var y = 0f
        val paintColors = intArrayOf(SK_ColorBLACK, SK_ColorGREEN, SK_ColorRED, SK_ColorBLUE)
        var colorIdx = 0
        for (textSize in K_TEXT_SIZES) {
            font.size = textSize
            font.getMetrics(metrics)
            font.hinting = SkFontHinting.kNone
            val yShift = -(metrics.fAscent + metrics.fDescent + metrics.fLeading) * 1.2f
            y += yShift
            paint.color = paintColors[colorIdx]
            var x = 0f
            // Perform simple line breaking to fit more glyphs into the GM
            // canvas (matches upstream's `getISize().width() - xTranslate
            // < x + glyphAdvance` check).
            for (i in codepoints.indices) {
                val cp = codepoints[i]
                // codepointToString — single-codepoint UTF-16 string, which
                // collapses to the single UTF-32 code point upstream measures.
                val cpStr = String(Character.toChars(cp))
                val glyphAdvance = font.measureText(
                    text = cpStr,
                    byteLength = cpStr.length,
                    encoding = SkTextEncoding.kUTF32,
                )
                if (0f < x && getISize().width - X_TRANSLATE < x + glyphAdvance) {
                    y += yShift
                    x = 0f
                }
                canvas.drawSimpleText(
                    text = cpStr,
                    byteLength = cpStr.length,
                    encoding = SkTextEncoding.kUTF32,
                    x = x,
                    y = y,
                    font = font,
                    paint = paint,
                )
                x += glyphAdvance + glyphAdvance * 0.05f
            }
            colorIdx++
        }
    }

    public companion object {
        private const val X_WIDTH = 1200
        private const val X_TRANSLATE = 200
        private const val FALLBACK_TEXT = "ABCD"
        private val K_TEXT_SIZES: FloatArray = floatArrayOf(12f, 18f, 30f, 120f)

        private const val K_TEST_FONT_NAME = "fonts/test_glyphs-glyf_colr_1.ttf"
        private const val K_TEST_FONT_NAME_VARIABLE = "fonts/test_glyphs-glyf_colr_1_variable.ttf"

        // ─── Codepoint arrays per ColrV1TestDefinitions (upstream cpp:191) ──
        // Generated via Google Fonts' `test_glyphs-glyf_colr_1.py` script ;
        // copied verbatim from the upstream `.cpp` definitions block. Each
        // group corresponds to one test category (gradient_stops_repeat,
        // sweep_varsweep, …) and selects N glyphs from the synthetic test
        // font that exercise one COLR v1 paint-graph feature.

        public val GRADIENT_STOPS_REPEAT: IntArray = intArrayOf(0xf0100, 0xf0101, 0xf0102, 0xf0103)

        public val SWEEP_VARSWEEP: IntArray = intArrayOf(
            0xf0200, 0xf0201, 0xf0202, 0xf0203, 0xf0204, 0xf0205, 0xf0206, 0xf0207, 0xf0208,
            0xf0209, 0xf020a, 0xf020b, 0xf020c, 0xf020d, 0xf020e, 0xf020f, 0xf0210, 0xf0211,
            0xf0212, 0xf0213, 0xf0214, 0xf0215, 0xf0216, 0xf0217, 0xf0218, 0xf0219, 0xf021a,
            0xf021b, 0xf021c, 0xf021d, 0xf021e, 0xf021f, 0xf0220, 0xf0221, 0xf0222, 0xf0223,
            0xf0224, 0xf0225, 0xf0226, 0xf0227, 0xf0228, 0xf0229, 0xf022a, 0xf022b, 0xf022c,
            0xf022d, 0xf022e, 0xf022f, 0xf0230, 0xf0231, 0xf0232, 0xf0233, 0xf0234, 0xf0235,
            0xf0236, 0xf0237, 0xf0238, 0xf0239, 0xf023a, 0xf023b, 0xf023c, 0xf023d, 0xf023e,
            0xf023f, 0xf0240, 0xf0241, 0xf0242, 0xf0243, 0xf0244, 0xf0245, 0xf0246, 0xf0247,
        )

        public val PAINT_SCALE: IntArray = intArrayOf(0xf0300, 0xf0301, 0xf0302, 0xf0303, 0xf0304, 0xf0305)

        public val EXTEND_MODE: IntArray = intArrayOf(
            0xf0500, 0xf0501, 0xf0502, 0xf0503, 0xf0504, 0xf0505, 0xf0506, 0xf0507, 0xf0508,
        )

        public val PAINT_ROTATE: IntArray = intArrayOf(0xf0600, 0xf0601, 0xf0602, 0xf0603)

        public val PAINT_SKEW: IntArray = intArrayOf(0xf0700, 0xf0701, 0xf0702, 0xf0703, 0xf0704, 0xf0705)

        public val PAINT_TRANSFORM: IntArray = intArrayOf(0xf0800, 0xf0801, 0xf0802, 0xf0803)

        public val PAINT_TRANSLATE: IntArray = intArrayOf(
            0xf0900, 0xf0901, 0xf0902, 0xf0903, 0xf0904, 0xf0905, 0xf0906,
        )

        public val COMPOSITE_MODE: IntArray = intArrayOf(
            0xf0a00, 0xf0a01, 0xf0a02, 0xf0a03, 0xf0a04, 0xf0a05, 0xf0a06,
            0xf0a07, 0xf0a08, 0xf0a09, 0xf0a0a, 0xf0a0b, 0xf0a0c, 0xf0a0d,
            0xf0a0e, 0xf0a0f, 0xf0a10, 0xf0a11, 0xf0a12, 0xf0a13, 0xf0a14,
            0xf0a15, 0xf0a16, 0xf0a17, 0xf0a18, 0xf0a19, 0xf0a1a, 0xf0a1b,
        )

        public val FOREGROUND_COLOR: IntArray = intArrayOf(
            0xf0b00, 0xf0b01, 0xf0b02, 0xf0b03, 0xf0b04, 0xf0b05, 0xf0b06, 0xf0b07,
        )

        public val CLIPBOX: IntArray = intArrayOf(0xf0c00, 0xf0c01, 0xf0c02, 0xf0c03, 0xf0c04)

        public val GRADIENT_P2_SKEWED: IntArray = intArrayOf(0xf0d00)

        public val VARIABLE_ALPHA: IntArray = intArrayOf(0xf1000)

        public val PAINTCOLRGLYPH_CYCLE: IntArray = intArrayOf(0xf1100, 0xf1101, 0xf1200)

        public val SWEEP_COINCIDENT: IntArray = intArrayOf(
            0xf1300, 0xf1301, 0xf1302, 0xf1303, 0xf1304, 0xf1305,
            0xf1306, 0xf1307, 0xf1308, 0xf1309, 0xf130a, 0xf130b,
            0xf130c, 0xf130d, 0xf130e, 0xf130f, 0xf1310, 0xf1311,
            0xf1312, 0xf1313, 0xf1314, 0xf1315, 0xf1316, 0xf1317,
        )

        public val PAINT_GLYPH_NESTED: IntArray = intArrayOf(
            0xf1400, 0xf1401, 0xf1402, 0xf1403,
            0xf1404, 0xf1405, 0xf1406, 0xf1407,
            0xf1408, 0xf1409, 0xf140a, 0xf140b,
            0xf140c, 0xf140d, 0xf140e, 0xf140f,
        )

        /**
         * Helper mirroring upstream's `""_t` user-defined literal —
         * packs a 4-char axis tag (`"SWPS"`, `"COL1"`, …) into the
         * Int form expected by [SkFontArguments.VariationPosition.Coordinate].
         */
        public fun tag(s: String): Int = SkFontVariation.Tag.of(s).raw

        /**
         * Shorthand for an `{axis, value}` coordinate literal — mirrors
         * upstream's `{{"SWPS"_t, 90.f}}` initialiser-list syntax.
         */
        public fun coord(axis: String, value: Float): SkFontArguments.VariationPosition.Coordinate =
            SkFontArguments.VariationPosition.Coordinate(tag(axis), value)
    }
}

private fun ByteArray.withColrV1SubsetTableContent(from: String, to: String, content: ByteArray): ByteArray {
    require(from.length == 4)
    require(to.length == 4)
    val copy = copyOf()
    val record = copy.colrV1SubsetTableDirectoryRecord(from)
    val offset = colrV1SubsetReadU32(copy, record + 8)
    val length = colrV1SubsetReadU32(copy, record + 12)
    require(content.size <= length) { "$from table too small for synthetic $to table" }
    to.toByteArray(Charsets.ISO_8859_1).copyInto(copy, record)
    colrV1SubsetWriteU32(copy, record + 12, content.size)
    content.copyInto(copy, offset)
    for (i in offset + content.size until offset + length) copy[i] = 0
    return copy
}

private fun ByteArray.colrV1SubsetTableDirectoryRecord(tag: String): Int {
    require(tag.length == 4)
    val numTables = colrV1SubsetReadU16(this, 4)
    var off = 12
    repeat(numTables) {
        if (String(this, off, 4, Charsets.ISO_8859_1) == tag) return off
        off += 16
    }
    error("Missing table: $tag")
}

private fun colrV1SubsetCpal(): ByteArray {
    val palette = listOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, 0xffff00ff.toInt())
    val colorRecordsOffset = 14
    val bytes = ByteArray(colorRecordsOffset + palette.size * 4)
    colrV1SubsetWriteU16(bytes, 2, palette.size)
    colrV1SubsetWriteU16(bytes, 4, 1)
    colrV1SubsetWriteU16(bytes, 6, palette.size)
    colrV1SubsetWriteU32(bytes, 8, colorRecordsOffset)
    colrV1SubsetWriteU16(bytes, 12, 0)
    palette.forEachIndexed { index, color ->
        val off = colorRecordsOffset + index * 4
        bytes[off] = (color and 0xFF).toByte()
        bytes[off + 1] = ((color ushr 8) and 0xFF).toByte()
        bytes[off + 2] = ((color ushr 16) and 0xFF).toByte()
        bytes[off + 3] = ((color ushr 24) and 0xFF).toByte()
    }
    return bytes
}

private fun colrV1SubsetColr(glyphs: List<Int>): ByteArray {
    val baseGlyphListOffset = 34
    val recordStart = baseGlyphListOffset + 4
    val paintStart = recordStart + glyphs.size * 6
    val paintRecordSize = 11
    val bytes = ByteArray(paintStart + glyphs.size * paintRecordSize)
    colrV1SubsetWriteU16(bytes, 0, 1)
    colrV1SubsetWriteU32(bytes, 14, baseGlyphListOffset)

    colrV1SubsetWriteU32(bytes, baseGlyphListOffset, glyphs.size)
    glyphs.forEachIndexed { index, glyph ->
        val recordOffset = recordStart + index * 6
        val glyphPaintOffset = paintStart + index * paintRecordSize
        val solidPaintOffset = glyphPaintOffset + 6
        colrV1SubsetWriteU16(bytes, recordOffset, glyph)
        colrV1SubsetWriteU32(bytes, recordOffset + 2, glyphPaintOffset - baseGlyphListOffset)

        bytes[glyphPaintOffset] = 10
        colrV1SubsetWriteU24(bytes, glyphPaintOffset + 1, solidPaintOffset - glyphPaintOffset)
        colrV1SubsetWriteU16(bytes, glyphPaintOffset + 4, glyph)
        bytes[solidPaintOffset] = 2
        colrV1SubsetWriteU16(bytes, solidPaintOffset + 1, index)
        colrV1SubsetWriteU16(bytes, solidPaintOffset + 3, 0x4000)
    }
    return bytes
}

private fun colrV1SubsetReadU16(bytes: ByteArray, off: Int): Int =
    ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)

private fun colrV1SubsetReadU32(bytes: ByteArray, off: Int): Int =
    ((bytes[off].toInt() and 0xFF) shl 24) or
        ((bytes[off + 1].toInt() and 0xFF) shl 16) or
        ((bytes[off + 2].toInt() and 0xFF) shl 8) or
        (bytes[off + 3].toInt() and 0xFF)

private fun colrV1SubsetWriteU16(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 8).toByte()
    bytes[off + 1] = value.toByte()
}

private fun colrV1SubsetWriteU24(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 16).toByte()
    bytes[off + 1] = (value ushr 8).toByte()
    bytes[off + 2] = value.toByte()
}

private fun colrV1SubsetWriteU32(bytes: ByteArray, off: Int, value: Int) {
    bytes[off] = (value ushr 24).toByte()
    bytes[off + 1] = (value ushr 16).toByte()
    bytes[off + 2] = (value ushr 8).toByte()
    bytes[off + 3] = value.toByte()
}
