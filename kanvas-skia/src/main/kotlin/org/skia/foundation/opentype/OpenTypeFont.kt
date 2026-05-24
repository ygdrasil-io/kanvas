package org.skia.foundation.opentype

import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.foundation.SkData
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin OpenType/TrueType font manager.
 *
 * This is the first non-AWT/non-JNI font backend. It intentionally starts
 * narrow: single-face TrueType fonts with `cmap`, `head`, `hhea`, `hmtx`,
 * `loca`, `glyf`, `maxp`, and optionally `name`/`OS/2`. That is enough to
 * load the bundled Liberation TTFs, map Unicode to glyph IDs, expose advance
 * widths, metrics, family names, and glyph outlines as [SkPath].
 */
public class OpenTypeFontMgr public constructor() : SkFontMgr() {
    override fun countFamilies(): Int = 0
    override fun getFamilyName(index: Int): String =
        throw IndexOutOfBoundsException("OpenTypeFontMgr does not enumerate system families")

    override fun createStyleSet(index: Int): SkFontStyleSet =
        throw IndexOutOfBoundsException("OpenTypeFontMgr does not enumerate system families")

    override fun matchFamily(familyName: String?): SkFontStyleSet = SkFontStyleSet.CreateEmpty()
    override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? = null
    override fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: Array<String>?,
        character: Int,
    ): SkTypeface? = null

    override fun makeFromData(data: SkData, ttcIndex: Int): SkTypeface? {
        if (data.size == 0) return null
        return OpenTypeTypeface.MakeFromBytes(data.toByteArray(), ttcIndex)
    }

    override fun makeFromStream(stream: InputStream, ttcIndex: Int): SkTypeface? =
        makeFromData(SkData.MakeWithCopy(stream.readAllBytesCompat()), ttcIndex)

    override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            file.inputStream().use { makeFromStream(it, ttcIndex) }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface? = null

    public companion object {
        @Suppress("FunctionName")
        public fun Create(): OpenTypeFontMgr = OpenTypeFontMgr()
    }
}

/**
 * Pure-Kotlin TrueType-backed [SkTypeface].
 */
public class OpenTypeTypeface private constructor(
    private val font: ParsedTrueTypeFont,
    override val fontStyle: SkFontStyle,
) : SkTypeface() {
    override fun countGlyphs(): Int = font.numGlyphs

    override fun getFamilyName(name: StringBuilder) {
        name.append(font.familyName)
    }

    override fun getPostScriptName(): String? = font.postScriptName

    override fun createFamilyNameIterator(): Iterator<SkTypeface.LocalizedString> =
        font.localizedFamilyNames.iterator()

    override fun unicharsToGlyphsInternal(unichars: IntArray, count: Int, glyphs: ShortArray) {
        for (i in 0 until count) {
            glyphs[i] = font.glyphForCodepoint(unichars[i]).toShort()
        }
    }

    override fun getGlyphWidthInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkScalar = font.advanceWidth(glyphId) * font.scale(size) * scaleX

    override fun getGlyphPathInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath? = font.glyphPath(glyphId, size, scaleX, skewX)

    override fun getGlyphBoundsInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkRect = font.glyphBounds(glyphId, size, scaleX, skewX)

    override fun getKerningPairAdjustments(glyphs: ShortArray): IntArray? =
        font.kerningPairAdjustments(glyphs)

    override fun copyTableData(tag: Int): ByteArray? =
        font.tableData(tag)

    override fun makeTextPath(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        isSubpixel: Boolean,
    ): SkPath? {
        if (text.isEmpty()) return null
        val builder = SkPathBuilder().setFillType(SkPathFillType.kWinding)
        var penX = x
        val glyphs = text.codePoints().toArray().let { codepoints ->
            IntArray(codepoints.size) { font.glyphForCodepoint(codepoints[it]) }
        }
        for (i in glyphs.indices) {
            val glyphId = glyphs[i]
            val glyphPath = font.glyphPath(glyphId, size, scaleX, skewX)
            if (glyphPath != null && !glyphPath.isEmpty()) {
                builder.addPathOffset(glyphPath, penX, y)
            }
            penX += font.advanceWidth(glyphId) * font.scale(size) * scaleX
            if (i < glyphs.lastIndex) {
                penX += font.kerningAdjustment(glyphId, glyphs[i + 1]) * font.scale(size) * scaleX
            }
        }
        val out = builder.detach()
        return if (out.isEmpty()) null else out
    }

    override fun measureTextInternal(
        text: String,
        byteLength: Int,
        encoding: SkTextEncoding,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        bounds: SkRect?,
    ): SkScalar {
        if (text.isEmpty() || byteLength == 0) {
            bounds?.let { it.left = 0f; it.top = 0f; it.right = 0f; it.bottom = 0f }
            return 0f
        }
        val sub = if (byteLength >= text.length) text else text.substring(0, byteLength)
        val cps = if (encoding == SkTextEncoding.kGlyphID) {
            IntArray(sub.length) { sub[it].code and 0xFFFF }
        } else {
            sub.codePoints().toArray().let { codepoints ->
                IntArray(codepoints.size) { font.glyphForCodepoint(codepoints[it]) }
            }
        }

        var advance = 0f
        var haveBounds = false
        var joined = SkRect.MakeEmpty()
        for (i in cps.indices) {
            val glyphId = cps[i]
            if (bounds != null) {
                val glyphBounds = font.glyphBounds(glyphId, size, scaleX, skewX)
                if (!glyphBounds.isEmpty) {
                    val shifted = SkRect.MakeLTRB(
                        glyphBounds.left + advance,
                        glyphBounds.top,
                        glyphBounds.right + advance,
                        glyphBounds.bottom,
                    )
                    if (haveBounds) joined.join(shifted) else {
                        joined = shifted
                        haveBounds = true
                    }
                }
            }
            advance += font.advanceWidth(glyphId) * font.scale(size) * scaleX
            if (i < cps.lastIndex) {
                advance += font.kerningAdjustment(glyphId, cps[i + 1]) * font.scale(size) * scaleX
            }
        }
        bounds?.let {
            if (haveBounds) {
                it.left = joined.left; it.top = joined.top; it.right = joined.right; it.bottom = joined.bottom
            } else {
                it.left = 0f; it.top = 0f; it.right = 0f; it.bottom = 0f
            }
        }
        return advance
    }

    override fun getMetricsInternal(metrics: SkFontMetrics, size: SkScalar): SkScalar {
        val s = font.scale(size)
        metrics.fAscent = -font.ascent * s
        metrics.fDescent = -font.descent * s
        metrics.fTop = -font.yMax * s
        metrics.fBottom = -font.yMin * s
        metrics.fLeading = font.lineGap * s
        metrics.fAvgCharWidth = font.avgCharWidth * s
        metrics.fMaxCharWidth = font.maxAdvanceWidth * s
        metrics.fXMin = font.xMin * s
        metrics.fXMax = font.xMax * s
        metrics.fXHeight = -font.xHeight * s
        metrics.fCapHeight = -font.capHeight * s
        metrics.fUnderlineThickness = font.underlineThickness * s
        metrics.fUnderlinePosition = -(font.underlinePosition + font.underlineThickness / 2f) * s
        metrics.fStrikeoutThickness = font.strikeoutThickness * s
        metrics.fStrikeoutPosition = -font.strikeoutPosition * s
        metrics.fFlags =
            SkFontMetrics.kUnderlineThicknessIsValid_Flag or
                SkFontMetrics.kUnderlinePositionIsValid_Flag or
                SkFontMetrics.kStrikeoutThicknessIsValid_Flag or
                SkFontMetrics.kStrikeoutPositionIsValid_Flag
        return (font.ascent - font.descent + font.lineGap) * s
    }

    override fun makeClone(args: SkFontArguments): SkTypeface? {
        // Variation deltas are deliberately not applied in this first slice.
        return OpenTypeTypeface(font, fontStyle)
    }

    internal fun withFontStyle(style: SkFontStyle): OpenTypeTypeface =
        OpenTypeTypeface(font, style)

    public companion object {
        @Suppress("FunctionName")
        public fun MakeFromBytes(bytes: ByteArray, ttcIndex: Int = 0): OpenTypeTypeface? =
            ParsedTrueTypeFont.parse(bytes, ttcIndex)?.let { OpenTypeTypeface(it, SkFontStyle.Normal()) }
    }
}

private class ParsedTrueTypeFont(
    private val bytes: ByteArray,
    private val tables: Map<String, TableRecord>,
    val unitsPerEm: Int,
    val indexToLocFormat: Int,
    val numGlyphs: Int,
    val numHMetrics: Int,
    val ascent: Int,
    val descent: Int,
    val lineGap: Int,
    val maxAdvanceWidth: Int,
    val xMin: Int,
    val yMin: Int,
    val xMax: Int,
    val yMax: Int,
    val avgCharWidth: Int,
    val xHeight: Int,
    val capHeight: Int,
    val underlinePosition: Int,
    val underlineThickness: Int,
    val strikeoutPosition: Int,
    val strikeoutThickness: Int,
    val familyName: String,
    val postScriptName: String?,
    val localizedFamilyNames: List<SkTypeface.LocalizedString>,
    private val cmap: Cmap,
    private val advanceWidths: IntArray,
    private val leftSideBearings: ShortArray,
    private val glyphOffsets: IntArray,
    private val kern: KernTable?,
) {
    private val pathCache = HashMap<Int, GlyphOutline?>()

    fun scale(size: Float): Float = size / unitsPerEm.toFloat()

    fun glyphForCodepoint(cp: Int): Int = cmap.glyphId(cp).coerceIn(0, numGlyphs - 1)

    fun advanceWidth(glyphId: Int): Int {
        if (glyphId < 0 || glyphId >= numGlyphs) return 0
        return advanceWidths[min(glyphId, advanceWidths.lastIndex)]
    }

    fun kerningPairAdjustments(glyphs: ShortArray): IntArray? {
        kern ?: return null
        if (glyphs.size <= 1) return IntArray(0)
        return IntArray(glyphs.size - 1) { i ->
            kerningAdjustment(glyphs[i].toInt() and 0xFFFF, glyphs[i + 1].toInt() and 0xFFFF)
        }
    }

    fun kerningAdjustment(leftGlyphId: Int, rightGlyphId: Int): Int =
        kern?.adjustment(leftGlyphId, rightGlyphId) ?: 0

    fun tableData(tag: Int): ByteArray? {
        val record = tables[openTypeTagToString(tag)] ?: return null
        return bytes.copyOfRange(record.offset, record.offset + record.length)
    }

    fun glyphBounds(glyphId: Int, size: Float, scaleX: Float, skewX: Float): SkRect {
        val outline = glyphOutline(glyphId) ?: return SkRect.MakeEmpty()
        if (outline.points.isEmpty()) return SkRect.MakeEmpty()
        var l = Float.POSITIVE_INFINITY
        var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY
        var b = Float.NEGATIVE_INFINITY
        for (p in outline.points) {
            val x = transformX(p.x, p.y, size, scaleX, skewX)
            val y = transformY(p.y, size)
            l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y)
        }
        return if (l.isFinite()) SkRect.MakeLTRB(l, t, r, b) else SkRect.MakeEmpty()
    }

    fun glyphPath(glyphId: Int, size: Float, scaleX: Float, skewX: Float): SkPath? {
        val outline = glyphOutline(glyphId) ?: return null
        if (outline.contours.isEmpty()) return null
        val builder = SkPathBuilder().setFillType(SkPathFillType.kWinding)
        for (contour in outline.contours) {
            emitContour(builder, contour, size, scaleX, skewX)
        }
        val out = builder.detach()
        return if (out.isEmpty()) null else out
    }

    private fun glyphOutline(glyphId: Int): GlyphOutline? =
        pathCache.getOrPut(glyphId) { readGlyph(glyphId, depth = 0) }

    private fun readGlyph(glyphId: Int, depth: Int): GlyphOutline? {
        if (glyphId < 0 || glyphId >= numGlyphs || depth > 8) return null
        val glyf = tables["glyf"] ?: return null
        val start = glyphOffsets[glyphId]
        val end = glyphOffsets.getOrElse(glyphId + 1) { start }
        if (start == end) return GlyphOutline(emptyList())
        if (start < 0 || end < start || !fits(glyf.offset, end, bytes.size) || end > glyf.length) return null
        val p = glyf.offset + start
        if (!fits(p, 10, bytes.size)) return null
        val numberOfContours = i16(p).toInt()
        if (numberOfContours >= 0) return readSimpleGlyph(p, numberOfContours)
        return readCompositeGlyph(p, depth)
    }

    private fun readSimpleGlyph(p: Int, numberOfContours: Int): GlyphOutline? {
        if (numberOfContours == 0) return GlyphOutline(emptyList())
        var off = p + 10
        if (!fits(off, numberOfContours * 2, bytes.size)) return null
        val endPts = IntArray(numberOfContours)
        for (i in 0 until numberOfContours) {
            endPts[i] = u16(off); off += 2
        }
        if (!fits(off, 2, bytes.size)) return null
        val instructionLength = u16(off)
        val instructionStart = off + 2
        if (!fits(instructionStart, instructionLength, bytes.size)) return null
        off = instructionStart + instructionLength
        val pointCount = endPts.last() + 1
        if (pointCount < 0) return null
        val flags = IntArray(pointCount)
        var i = 0
        while (i < pointCount) {
            if (!fits(off, 1, bytes.size)) return null
            val flag = u8(off++)
            flags[i++] = flag
            if ((flag and FLAG_REPEAT) != 0) {
                if (!fits(off, 1, bytes.size)) return null
                val repeat = u8(off++)
                repeat(repeat) { if (i < pointCount) flags[i++] = flag }
            }
        }
        val xs = IntArray(pointCount)
        var x = 0
        for (j in 0 until pointCount) {
            val flag = flags[j]
            val dx = if ((flag and FLAG_X_SHORT) != 0) {
                if (!fits(off, 1, bytes.size)) return null
                val v = u8(off++)
                if ((flag and FLAG_X_SAME_OR_POSITIVE) != 0) v else -v
            } else {
                if ((flag and FLAG_X_SAME_OR_POSITIVE) != 0) 0 else {
                    if (!fits(off, 2, bytes.size)) return null
                    val v = i16(off).toInt(); off += 2; v
                }
            }
            x += dx
            xs[j] = x
        }
        val ys = IntArray(pointCount)
        var y = 0
        for (j in 0 until pointCount) {
            val flag = flags[j]
            val dy = if ((flag and FLAG_Y_SHORT) != 0) {
                if (!fits(off, 1, bytes.size)) return null
                val v = u8(off++)
                if ((flag and FLAG_Y_SAME_OR_POSITIVE) != 0) v else -v
            } else {
                if ((flag and FLAG_Y_SAME_OR_POSITIVE) != 0) 0 else {
                    if (!fits(off, 2, bytes.size)) return null
                    val v = i16(off).toInt(); off += 2; v
                }
            }
            y += dy
            ys[j] = y
        }
        val contours = ArrayList<List<TtPoint>>(numberOfContours)
        var start = 0
        for (end in endPts) {
            val pts = ArrayList<TtPoint>(end - start + 1)
            for (idx in start..end) {
                pts.add(TtPoint(xs[idx], ys[idx], (flags[idx] and FLAG_ON_CURVE) != 0))
            }
            contours.add(pts)
            start = end + 1
        }
        return GlyphOutline(contours)
    }

    private fun readCompositeGlyph(p: Int, depth: Int): GlyphOutline? {
        var off = p + 10
        val contours = ArrayList<List<TtPoint>>()
        do {
            if (!fits(off, 4, bytes.size)) return null
            val flags = u16(off); off += 2
            val componentGlyph = u16(off); off += 2
            val arg1: Int
            val arg2: Int
            if ((flags and ARG_1_AND_2_ARE_WORDS) != 0) {
                if (!fits(off, 4, bytes.size)) return null
                arg1 = i16(off).toInt(); arg2 = i16(off + 2).toInt(); off += 4
            } else {
                if (!fits(off, 2, bytes.size)) return null
                arg1 = i8(off).toInt(); arg2 = i8(off + 1).toInt(); off += 2
            }
            val dx = if ((flags and ARGS_ARE_XY_VALUES) != 0) arg1 else 0
            val dy = if ((flags and ARGS_ARE_XY_VALUES) != 0) arg2 else 0
            var a = 1f
            var b = 0f
            var c = 0f
            var d = 1f
            when {
                (flags and WE_HAVE_A_SCALE) != 0 -> {
                    if (!fits(off, 2, bytes.size)) return null
                    a = f2dot14(off); d = a; off += 2
                }
                (flags and WE_HAVE_AN_X_AND_Y_SCALE) != 0 -> {
                    if (!fits(off, 4, bytes.size)) return null
                    a = f2dot14(off); d = f2dot14(off + 2); off += 4
                }
                (flags and WE_HAVE_A_TWO_BY_TWO) != 0 -> {
                    if (!fits(off, 8, bytes.size)) return null
                    a = f2dot14(off); b = f2dot14(off + 2)
                    c = f2dot14(off + 4); d = f2dot14(off + 6); off += 8
                }
            }
            val child = readGlyph(componentGlyph, depth + 1)
            child?.contours?.forEach { contour ->
                contours.add(contour.map { pt ->
                    val x = a * pt.x + b * pt.y + dx
                    val y = c * pt.x + d * pt.y + dy
                    TtPoint(x.toInt(), y.toInt(), pt.onCurve)
                })
            }
        } while ((flags and MORE_COMPONENTS) != 0)
        return GlyphOutline(contours)
    }

    private fun transformX(x: Int, y: Int, size: Float, scaleX: Float, skewX: Float): Float {
        val s = scale(size)
        val sy = -y * s
        return x * s * scaleX + skewX * sy
    }

    private fun transformY(y: Int, size: Float): Float = -y * scale(size)

    private fun emitContour(
        builder: SkPathBuilder,
        contour: List<TtPoint>,
        size: Float,
        scaleX: Float,
        skewX: Float,
    ) {
        if (contour.isEmpty()) return
        val n = contour.size
        val first = contour.first()
        val last = contour.last()
        val start = when {
            first.onCurve -> first
            last.onCurve -> last
            else -> midpoint(last, first)
        }
        builder.moveTo(transformX(start.x, start.y, size, scaleX, skewX), transformY(start.y, size))
        var prev = start
        var i = if (first.onCurve) 1 else 0
        var emitted = 0
        while (emitted < n) {
            val curr = contour[i % n]
            if (curr === start && emitted > 0) break
            if (curr.onCurve) {
                builder.lineTo(transformX(curr.x, curr.y, size, scaleX, skewX), transformY(curr.y, size))
                prev = curr
                i++
                emitted++
            } else {
                val next = contour[(i + 1) % n]
                val end = if (next.onCurve) next else midpoint(curr, next)
                builder.quadTo(
                    transformX(curr.x, curr.y, size, scaleX, skewX),
                    transformY(curr.y, size),
                    transformX(end.x, end.y, size, scaleX, skewX),
                    transformY(end.y, size),
                )
                prev = end
                i += if (next.onCurve) 2 else 1
                emitted += if (next.onCurve) 2 else 1
            }
        }
        if (prev != start) builder.close() else builder.close()
    }

    private fun midpoint(a: TtPoint, b: TtPoint): TtPoint =
        TtPoint((a.x + b.x) / 2, (a.y + b.y) / 2, true)

    private fun u8(off: Int): Int = bytes[off].toInt() and 0xFF
    private fun i8(off: Int): Byte = bytes[off]
    private fun u16(off: Int): Int = ((u8(off) shl 8) or u8(off + 1))
    private fun i16(off: Int): Short = u16(off).toShort()
    private fun u32(off: Int): Long =
        ((u8(off).toLong() shl 24) or (u8(off + 1).toLong() shl 16) or
            (u8(off + 2).toLong() shl 8) or u8(off + 3).toLong())
    private fun f2dot14(off: Int): Float = i16(off).toInt() / 16384f

    companion object {
        fun parse(input: ByteArray, ttcIndex: Int): ParsedTrueTypeFont? {
            return try {
                parseUnchecked(input, ttcIndex)
            } catch (e: IndexOutOfBoundsException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun parseUnchecked(input: ByteArray, ttcIndex: Int): ParsedTrueTypeFont? {
            val bytes = sliceTtc(input, ttcIndex) ?: return null
            val reader = SfntReader(bytes)
            if (bytes.size < 12) return null
            val sfnt = reader.tag(0) ?: return null
            if (sfnt != "\u0000\u0001\u0000\u0000" && sfnt != "true") return null
            val numTables = reader.u16(4) ?: return null
            if (!reader.fits(12, numTables * 16)) return null
            val tables = HashMap<String, TableRecord>()
            var off = 12
            repeat(numTables) {
                val name = reader.tag(off) ?: return null
                val offset = reader.u32(off + 8)?.toIntOrNull()
                val length = reader.u32(off + 12)?.toIntOrNull()
                if (offset != null && length != null && reader.fits(offset, length)) {
                    tables[name] = TableRecord(offset, length)
                }
                off += 16
            }
            val head = tables["head"] ?: return null
            val hhea = tables["hhea"] ?: return null
            val maxp = tables["maxp"] ?: return null
            val hmtx = tables["hmtx"] ?: return null
            val loca = tables["loca"] ?: return null
            val glyf = tables["glyf"] ?: return null
            val cmapTable = tables["cmap"] ?: return null
            if (glyf.length == 0) return null
            if (head.length < 54 || hhea.length < 36 || maxp.length < 6) return null

            val unitsPerEm = reader.u16(head.offset + 18) ?: return null
            val xMin = reader.i16(head.offset + 36)?.toInt() ?: return null
            val yMin = reader.i16(head.offset + 38)?.toInt() ?: return null
            val xMax = reader.i16(head.offset + 40)?.toInt() ?: return null
            val yMax = reader.i16(head.offset + 42)?.toInt() ?: return null
            val indexToLocFormat = reader.i16(head.offset + 50)?.toInt() ?: return null
            val hheaAscent = reader.i16(hhea.offset + 4)?.toInt() ?: return null
            val hheaDescent = reader.i16(hhea.offset + 6)?.toInt() ?: return null
            val hheaLineGap = reader.i16(hhea.offset + 8)?.toInt() ?: return null
            val maxAdvanceWidth = reader.u16(hhea.offset + 10) ?: return null
            val numHMetrics = reader.u16(hhea.offset + 34) ?: return null
            val numGlyphs = reader.u16(maxp.offset + 4) ?: return null
            if (unitsPerEm <= 0 || numGlyphs <= 0 || numHMetrics <= 0) return null
            if (numHMetrics > numGlyphs) return null
            if (hmtx.length < numHMetrics * 4) return null

            val advances = IntArray(numGlyphs)
            val bearings = ShortArray(numGlyphs)
            var h = hmtx.offset
            for (i in 0 until numHMetrics) {
                advances[i] = reader.u16(h) ?: return null; h += 2
                bearings[i] = reader.i16(h) ?: return null; h += 2
            }
            for (i in numHMetrics until numGlyphs) {
                advances[i] = advances[numHMetrics - 1]
                bearings[i] = if (h + 2 <= hmtx.offset + hmtx.length) reader.i16(h) ?: return null else 0
                h += 2
            }

            val offsets = IntArray(numGlyphs + 1)
            if (indexToLocFormat == 0) {
                if (loca.length < (numGlyphs + 1) * 2) return null
                for (i in 0..numGlyphs) offsets[i] = (reader.u16(loca.offset + i * 2) ?: return null) * 2
            } else if (indexToLocFormat == 1) {
                if (loca.length < (numGlyphs + 1) * 4) return null
                for (i in 0..numGlyphs) offsets[i] = reader.u32(loca.offset + i * 4)?.toIntOrNull() ?: return null
            } else return null
            if (offsets.any { it < 0 || it > glyf.length }) return null
            for (i in 0 until offsets.lastIndex) if (offsets[i] > offsets[i + 1]) return null
            val cmap = Cmap.parse(bytes, cmapTable) ?: return null
            val names = parseNameTable(bytes, tables["name"])
            val familyName = names?.familyName ?: "OpenType"
            val localizedNames = names?.localizedFamilyNames
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(SkTypeface.LocalizedString(familyName, "und"))
            val os2 = tables["OS/2"]
            if (os2 != null && os2.length < 4) return null
            val typoMetrics = os2?.takeIf {
                it.length >= 74 && ((reader.u16(it.offset + 62) ?: return null) and OS2_USE_TYPO_METRICS) != 0
            }
            val ascent = typoMetrics?.let { reader.i16(it.offset + 68)?.toInt() ?: return null } ?: hheaAscent
            val descent = typoMetrics?.let { reader.i16(it.offset + 70)?.toInt() ?: return null } ?: hheaDescent
            val lineGap = typoMetrics?.let { reader.i16(it.offset + 72)?.toInt() ?: return null } ?: hheaLineGap
            val avg = os2?.let { reader.i16(it.offset + 2)?.toInt() ?: return null } ?: advances.average().toInt()
            val sxHeight = os2?.takeIf { it.length >= 88 }?.let { reader.i16(it.offset + 86)?.toInt() ?: return null } ?: unitsPerEm / 2
            val sCapHeight = os2?.takeIf { it.length >= 90 }?.let { reader.i16(it.offset + 88)?.toInt() ?: return null } ?: (unitsPerEm * 7 / 10)
            val post = tables["post"]
            val underlinePosition = post?.takeIf { it.length >= 12 }?.let { reader.i16(it.offset + 8)?.toInt() ?: return null } ?: -(unitsPerEm / 9)
            val underlineThickness = post?.takeIf { it.length >= 12 }?.let { reader.i16(it.offset + 10)?.toInt() ?: return null } ?: max(1, unitsPerEm / 14)
            val strikeoutThickness = os2?.takeIf { it.length >= 30 }?.let { reader.i16(it.offset + 26)?.toInt() ?: return null } ?: max(1, unitsPerEm / 14)
            val strikeoutPosition = os2?.takeIf { it.length >= 30 }?.let { reader.i16(it.offset + 28)?.toInt() ?: return null } ?: unitsPerEm * 3 / 10
            val kern = parseKernTable(bytes, tables["kern"])

            return ParsedTrueTypeFont(
                bytes, tables, unitsPerEm, indexToLocFormat, numGlyphs, numHMetrics,
                ascent, descent, lineGap, maxAdvanceWidth, xMin, yMin, xMax, yMax,
                avg, sxHeight, sCapHeight, underlinePosition, underlineThickness,
                strikeoutPosition, strikeoutThickness, familyName, names?.postScriptName, localizedNames,
                cmap, advances, bearings, offsets, kern,
            )
        }

        private fun sliceTtc(bytes: ByteArray, ttcIndex: Int): ByteArray? {
            val reader = SfntReader(bytes)
            if (bytes.size >= 12 && reader.tag(0) == "ttcf") {
                val numFonts = reader.u32(8)?.toIntOrNull() ?: return null
                if (ttcIndex < 0 || ttcIndex >= numFonts) return null
                if (!reader.fits(12, numFonts.toLong() * 4L)) return null
                val offset = reader.u32(12 + ttcIndex * 4)?.toIntOrNull() ?: return null
                if (offset < 0 || offset >= bytes.size) return null
                return bytes.copyOfRange(offset, bytes.size)
            }
            return if (ttcIndex == 0) bytes else null
        }

        private fun parseNameTable(bytes: ByteArray, table: TableRecord?): OpenTypeNames? {
            table ?: return null
            if (table.length < 6) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val count = reader.u16(table.offset + 2) ?: return null
            val stringOffset = table.offset + (reader.u16(table.offset + 4) ?: return null)
            if (!reader.fits(table.offset + 6, count.toLong() * 12L)) return null
            if (stringOffset !in table.offset..tableEnd) return null
            val familyCandidates = ArrayList<NameRecord>()
            val postScriptCandidates = ArrayList<NameRecord>()
            val localized = ArrayList<SkTypeface.LocalizedString>()
            val seenLocalized = HashSet<Pair<String, String>>()
            var off = table.offset + 6
            repeat(count) {
                val platform = reader.u16(off) ?: return null
                val encoding = reader.u16(off + 2) ?: return null
                val language = reader.u16(off + 4) ?: return null
                val nameId = reader.u16(off + 6) ?: return null
                val length = reader.u16(off + 8) ?: return null
                val strOff = stringOffset + (reader.u16(off + 10) ?: return null)
                if (strOff >= stringOffset && reader.fits(strOff, length)) {
                    val s = decodeName(bytes, strOff, length, platform, encoding)
                    if (!s.isNullOrBlank()) {
                        val record = NameRecord(s, platform, language)
                        when (nameId) {
                            1 -> familyCandidates.add(record)
                            6 -> postScriptCandidates.add(record)
                        }
                        if (nameId == 1 || nameId == 4) {
                            val localizedName = SkTypeface.LocalizedString(s, languageTag(platform, language))
                            if (seenLocalized.add(localizedName.fString to localizedName.fLanguage)) {
                                localized.add(localizedName)
                            }
                        }
                    }
                }
                off += 12
            }
            val familyName = chooseFamilyName(familyCandidates)
            return OpenTypeNames(familyName, chooseName(postScriptCandidates), localized)
        }

        private fun parseKernTable(bytes: ByteArray, table: TableRecord?): KernTable? {
            table ?: return null
            if (table.length < 4) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            if (reader.u16(table.offset) != 0) return null
            val subtableCount = reader.u16(table.offset + 2) ?: return null
            val pairs = HashMap<Int, Int>()
            var off = table.offset + 4
            var hasUsableSubtable = false
            repeat(subtableCount) {
                if (!reader.fits(off, 6)) return null
                val subtableVersion = reader.u16(off) ?: return null
                val subtableLength = reader.u16(off + 2) ?: return null
                val coverage = reader.u16(off + 4) ?: return null
                if (subtableVersion != 0 || subtableLength < 6 || !reader.fits(off, subtableLength)) return null

                val format = coverage ushr 8
                val horizontal = (coverage and KERN_HORIZONTAL) != 0
                val minimum = (coverage and KERN_MINIMUM) != 0
                val crossStream = (coverage and KERN_CROSS_STREAM) != 0
                if (format == 0 && horizontal && !minimum && !crossStream) {
                    parseKernFormat0(bytes, off + 6, off + subtableLength, pairs)
                        ?: return null
                    hasUsableSubtable = true
                }
                off += subtableLength
            }
            if (!hasUsableSubtable) return null
            return KernTable(pairs)
        }

        private fun parseKernFormat0(
            bytes: ByteArray,
            off: Int,
            limit: Int,
            pairs: MutableMap<Int, Int>,
        ): Unit? {
            val reader = SfntReader(bytes, limit)
            if (!reader.fits(off, 8)) return null
            val pairCount = reader.u16(off) ?: return null
            if (!reader.fits(off + 8, pairCount.toLong() * 6L)) return null
            var p = off + 8
            repeat(pairCount) {
                val left = reader.u16(p) ?: return null
                val right = reader.u16(p + 2) ?: return null
                val value = reader.i16(p + 4)?.toInt() ?: return null
                val key = kernPairKey(left, right)
                pairs[key] = (pairs[key] ?: 0) + value
                p += 6
            }
            return Unit
        }

        private fun chooseFamilyName(records: List<NameRecord>): String? =
            records.firstOrNull { it.platform == 3 && it.language == 0x0409 }?.value
                ?: records.firstOrNull()?.value

        private fun chooseName(records: List<NameRecord>): String? =
            records.firstOrNull { it.platform == 3 && it.language == 0x0409 }?.value
                ?: records.firstOrNull { it.platform == 0 }?.value
                ?: records.firstOrNull { it.platform == 1 && it.language == 0 }?.value
                ?: records.firstOrNull()?.value

        private fun decodeName(bytes: ByteArray, off: Int, len: Int, platform: Int, encoding: Int): String? =
            try {
                when (platform) {
                    0, 3 -> String(bytes, off, len, Charsets.UTF_16BE)
                    1 -> String(bytes, off, len, Charsets.ISO_8859_1)
                    else -> if (encoding == 1) String(bytes, off, len, Charsets.UTF_16BE) else null
                }
            } catch (e: RuntimeException) {
                null
            }

        private fun languageTag(platform: Int, language: Int): String =
            when (platform) {
                1 -> macLanguageTag(language)
                3 -> windowsLanguageTag(language)
                else -> "und"
            }

        private fun macLanguageTag(language: Int): String =
            when (language) {
                0 -> "en"
                1 -> "fr"
                2 -> "de"
                3 -> "it"
                4 -> "nl"
                5 -> "sv"
                6 -> "es"
                11 -> "ja"
                12 -> "ar"
                19 -> "zh-Hant"
                23 -> "ko"
                33 -> "zh-Hans"
                else -> "und"
            }

        private fun windowsLanguageTag(language: Int): String =
            when (language) {
                0x0401 -> "ar-SA"
                0x0404 -> "zh-TW"
                0x0407 -> "de-DE"
                0x0409 -> "en-US"
                0x040A -> "es-ES"
                0x040C -> "fr-FR"
                0x0410 -> "it-IT"
                0x0411 -> "ja-JP"
                0x0412 -> "ko-KR"
                0x0413 -> "nl-NL"
                0x041D -> "sv-SE"
                0x0804 -> "zh-CN"
                0x0809 -> "en-GB"
                0x0C0A -> "es-ES"
                else -> "und"
            }

    }
}

private sealed interface Cmap {
    fun glyphId(cp: Int): Int

    companion object {
        fun parse(bytes: ByteArray, table: TableRecord): Cmap? {
            if (table.length < 4) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val numTables = reader.u16(table.offset + 2) ?: return null
            if (!reader.fits(table.offset + 4, numTables * 8)) return null
            var best: Pair<Int, Int>? = null
            var off = table.offset + 4
            repeat(numTables) {
                val platform = reader.u16(off) ?: return null
                val encoding = reader.u16(off + 2) ?: return null
                val subOffset = reader.u32(off + 4)?.toIntOrNull()
                if (subOffset == null || subOffset < 0 || subOffset >= table.length) {
                    off += 8
                    return@repeat
                }
                val abs = table.offset + subOffset
                if (reader.fits(abs, 2)) {
                    val format = reader.u16(abs) ?: return null
                    val score = when {
                        format == 12 && platform == 3 && encoding == 10 -> 100
                        format == 4 && platform == 3 && encoding == 1 -> 90
                        format == 4 && platform == 0 -> 80
                        format == 4 -> 50
                        else -> 0
                    }
                    if (score > (best?.first ?: -1)) best = score to abs
                }
                off += 8
            }
            val chosen = best?.second ?: return null
            return when (reader.u16(chosen) ?: return null) {
                4 -> CmapFormat4.parse(bytes, chosen, tableEnd)
                12 -> CmapFormat12.parse(bytes, chosen, tableEnd)
                else -> null
            }
        }
    }
}

private class CmapFormat4(
    private val endCodes: IntArray,
    private val startCodes: IntArray,
    private val idDeltas: IntArray,
    private val idRangeOffsets: IntArray,
    private val glyphIdArray: IntArray,
) : Cmap {
    override fun glyphId(cp: Int): Int {
        if (cp !in 0..0xFFFF) return 0
        for (i in endCodes.indices) {
            if (cp < startCodes[i]) continue
            if (cp > endCodes[i]) continue
            if (idRangeOffsets[i] == 0) return (cp + idDeltas[i]) and 0xFFFF
            val index = idRangeOffsets[i] / 2 + (cp - startCodes[i]) - (endCodes.size - i)
            if (index !in glyphIdArray.indices) return 0
            val glyph = glyphIdArray[index]
            return if (glyph == 0) 0 else (glyph + idDeltas[i]) and 0xFFFF
        }
        return 0
    }

    companion object {
        fun parse(bytes: ByteArray, off: Int, limit: Int): CmapFormat4? {
            val reader = SfntReader(bytes, limit)
            if (!reader.fits(off, 14)) return null
            val length = reader.u16(off + 2) ?: return null
            if (length < 16 || !reader.fits(off, length)) return null
            val subtable = SfntReader(bytes, off + length)
            val segCount = (subtable.u16(off + 6) ?: return null) / 2
            if (segCount <= 0) return null
            var p = off + 14
            if (!subtable.fits(p, segCount * 2 + 2)) return null
            val end = IntArray(segCount) { subtable.u16(p + it * 2) ?: return null }
            p += segCount * 2 + 2
            if (!subtable.fits(p, segCount * 2)) return null
            val start = IntArray(segCount) { subtable.u16(p + it * 2) ?: return null }
            p += segCount * 2
            if (!subtable.fits(p, segCount * 2)) return null
            val deltas = IntArray(segCount) { subtable.i16(p + it * 2)?.toInt() ?: return null }
            p += segCount * 2
            if (!subtable.fits(p, segCount * 2)) return null
            val rangeOffsets = IntArray(segCount) { subtable.u16(p + it * 2) ?: return null }
            p += segCount * 2
            val glyphCount = max(0, (off + length - p) / 2)
            val glyphs = IntArray(glyphCount) { subtable.u16(p + it * 2) ?: return null }
            return CmapFormat4(end, start, deltas, rangeOffsets, glyphs)
        }
    }
}

private class CmapFormat12(private val groups: List<Group>) : Cmap {
    override fun glyphId(cp: Int): Int {
        for (g in groups) {
            if (cp in g.startChar..g.endChar) return (g.startGlyph + (cp - g.startChar)).toInt()
        }
        return 0
    }

    data class Group(val startChar: Long, val endChar: Long, val startGlyph: Long)

    companion object {
        fun parse(bytes: ByteArray, off: Int, limit: Int): CmapFormat12? {
            val reader = SfntReader(bytes, limit)
            if (!reader.fits(off, 16)) return null
            val length = reader.u32(off + 4)?.toIntOrNull() ?: return null
            if (length < 16 || !reader.fits(off, length)) return null
            val subtable = SfntReader(bytes, off + length)
            val nGroups = subtable.u32(off + 12)?.toIntOrNull() ?: return null
            if (!subtable.fits(off + 16, nGroups.toLong() * 12L)) return null
            val groups = ArrayList<Group>(nGroups)
            var p = off + 16
            repeat(nGroups) {
                val startChar = subtable.u32(p) ?: return null
                val endChar = subtable.u32(p + 4) ?: return null
                val startGlyph = subtable.u32(p + 8) ?: return null
                groups.add(Group(startChar, endChar, startGlyph))
                p += 12
            }
            return CmapFormat12(groups)
        }
    }
}

private class SfntReader(
    private val bytes: ByteArray,
    private val limit: Int = bytes.size,
) {
    fun fits(offset: Int, length: Int): Boolean =
        fits(offset, length.toLong())

    fun fits(offset: Int, length: Long): Boolean =
        offset >= 0 && length >= 0 && offset.toLong() + length <= limit.toLong()

    fun u16(offset: Int): Int? {
        if (!fits(offset, 2)) return null
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    fun i16(offset: Int): Short? =
        u16(offset)?.toShort()

    fun u32(offset: Int): Long? {
        if (!fits(offset, 4)) return null
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    fun tag(offset: Int): String? {
        if (!fits(offset, 4)) return null
        return String(
            byteArrayOf(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]),
            Charsets.ISO_8859_1,
        )
    }
}

private data class TableRecord(val offset: Int, val length: Int)
private data class OpenTypeNames(
    val familyName: String?,
    val postScriptName: String?,
    val localizedFamilyNames: List<SkTypeface.LocalizedString>,
)
private data class KernTable(private val pairs: Map<Int, Int>) {
    fun adjustment(leftGlyph: Int, rightGlyph: Int): Int =
        pairs[kernPairKey(leftGlyph, rightGlyph)] ?: 0
}
private data class NameRecord(val value: String, val platform: Int, val language: Int)
private data class TtPoint(val x: Int, val y: Int, val onCurve: Boolean)
private data class GlyphOutline(val contours: List<List<TtPoint>>) {
    val points: List<TtPoint> = contours.flatten()
}

private const val FLAG_ON_CURVE = 0x01
private const val FLAG_X_SHORT = 0x02
private const val FLAG_Y_SHORT = 0x04
private const val FLAG_REPEAT = 0x08
private const val FLAG_X_SAME_OR_POSITIVE = 0x10
private const val FLAG_Y_SAME_OR_POSITIVE = 0x20

private const val ARG_1_AND_2_ARE_WORDS = 0x0001
private const val ARGS_ARE_XY_VALUES = 0x0002
private const val WE_HAVE_A_SCALE = 0x0008
private const val MORE_COMPONENTS = 0x0020
private const val WE_HAVE_AN_X_AND_Y_SCALE = 0x0040
private const val WE_HAVE_A_TWO_BY_TWO = 0x0080

private const val KERN_HORIZONTAL = 0x0001
private const val KERN_MINIMUM = 0x0002
private const val KERN_CROSS_STREAM = 0x0004
private const val OS2_USE_TYPO_METRICS = 0x0080

private fun openTypeTagToString(tag: Int): String = buildString(4) {
    append(((tag ushr 24) and 0xFF).toChar())
    append(((tag ushr 16) and 0xFF).toChar())
    append(((tag ushr 8) and 0xFF).toChar())
    append((tag and 0xFF).toChar())
}

private fun kernPairKey(leftGlyph: Int, rightGlyph: Int): Int =
    ((leftGlyph and 0xFFFF) shl 16) or (rightGlyph and 0xFFFF)

private fun fits(offset: Int, length: Int, limit: Int): Boolean =
    fits(offset, length.toLong(), limit)

private fun fits(offset: Int, length: Long, limit: Int): Boolean =
    offset >= 0 && length >= 0 && offset.toLong() + length <= limit.toLong()

private fun Long.toIntOrNull(): Int? =
    if (this <= Int.MAX_VALUE) toInt() else null

private fun InputStream.readAllBytesCompat(): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
