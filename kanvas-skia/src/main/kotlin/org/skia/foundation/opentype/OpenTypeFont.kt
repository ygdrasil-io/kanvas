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
) : SkTypeface() {
    public override val fontStyle: SkFontStyle = SkFontStyle.Normal()

    override fun countGlyphs(): Int = font.numGlyphs

    override fun getFamilyName(name: StringBuilder) {
        name.append(font.familyName)
    }

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
        val cps = text.codePoints().toArray()
        for (cp in cps) {
            val glyphId = font.glyphForCodepoint(cp)
            val glyphPath = font.glyphPath(glyphId, size, scaleX, skewX)
            if (glyphPath != null && !glyphPath.isEmpty()) {
                builder.addPathOffset(glyphPath, penX, y)
            }
            penX += font.advanceWidth(glyphId) * font.scale(size) * scaleX
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
        for (glyphId in cps) {
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
        metrics.fUnderlineThickness = max(1f, size / 14f)
        metrics.fUnderlinePosition = size / 9f
        metrics.fStrikeoutThickness = max(1f, size / 14f)
        metrics.fStrikeoutPosition = -size * 0.3f
        metrics.fFlags =
            SkFontMetrics.kUnderlineThicknessIsValid_Flag or
                SkFontMetrics.kUnderlinePositionIsValid_Flag or
                SkFontMetrics.kStrikeoutThicknessIsValid_Flag or
                SkFontMetrics.kStrikeoutPositionIsValid_Flag
        return (font.ascent - font.descent + font.lineGap) * s
    }

    override fun makeClone(args: SkFontArguments): SkTypeface? {
        // Variation deltas are deliberately not applied in this first slice.
        return OpenTypeTypeface(font)
    }

    public companion object {
        @Suppress("FunctionName")
        public fun MakeFromBytes(bytes: ByteArray, ttcIndex: Int = 0): OpenTypeTypeface? =
            ParsedTrueTypeFont.parse(bytes, ttcIndex)?.let(::OpenTypeTypeface)
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
    val familyName: String,
    private val cmap: Cmap,
    private val advanceWidths: IntArray,
    private val leftSideBearings: ShortArray,
    private val glyphOffsets: IntArray,
) {
    private val pathCache = HashMap<Int, GlyphOutline?>()

    fun scale(size: Float): Float = size / unitsPerEm.toFloat()

    fun glyphForCodepoint(cp: Int): Int = cmap.glyphId(cp).coerceIn(0, numGlyphs - 1)

    fun advanceWidth(glyphId: Int): Int {
        if (glyphId < 0 || glyphId >= numGlyphs) return 0
        return advanceWidths[min(glyphId, advanceWidths.lastIndex)]
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
        if (start < 0 || end < start || glyf.offset + end > bytes.size) return null
        val p = glyf.offset + start
        val numberOfContours = i16(p).toInt()
        if (numberOfContours >= 0) return readSimpleGlyph(p, numberOfContours)
        return readCompositeGlyph(p, depth)
    }

    private fun readSimpleGlyph(p: Int, numberOfContours: Int): GlyphOutline? {
        if (numberOfContours == 0) return GlyphOutline(emptyList())
        var off = p + 10
        val endPts = IntArray(numberOfContours)
        for (i in 0 until numberOfContours) {
            endPts[i] = u16(off); off += 2
        }
        val instructionLength = u16(off); off += 2 + instructionLength
        if (off > bytes.size) return null
        val pointCount = endPts.last() + 1
        val flags = IntArray(pointCount)
        var i = 0
        while (i < pointCount) {
            val flag = u8(off++)
            flags[i++] = flag
            if ((flag and FLAG_REPEAT) != 0) {
                val repeat = u8(off++)
                repeat(repeat) { if (i < pointCount) flags[i++] = flag }
            }
        }
        val xs = IntArray(pointCount)
        var x = 0
        for (j in 0 until pointCount) {
            val flag = flags[j]
            val dx = if ((flag and FLAG_X_SHORT) != 0) {
                val v = u8(off++)
                if ((flag and FLAG_X_SAME_OR_POSITIVE) != 0) v else -v
            } else {
                if ((flag and FLAG_X_SAME_OR_POSITIVE) != 0) 0 else {
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
                val v = u8(off++)
                if ((flag and FLAG_Y_SAME_OR_POSITIVE) != 0) v else -v
            } else {
                if ((flag and FLAG_Y_SAME_OR_POSITIVE) != 0) 0 else {
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
            val flags = u16(off); off += 2
            val componentGlyph = u16(off); off += 2
            val arg1: Int
            val arg2: Int
            if ((flags and ARG_1_AND_2_ARE_WORDS) != 0) {
                arg1 = i16(off).toInt(); arg2 = i16(off + 2).toInt(); off += 4
            } else {
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
                    a = f2dot14(off); d = a; off += 2
                }
                (flags and WE_HAVE_AN_X_AND_Y_SCALE) != 0 -> {
                    a = f2dot14(off); d = f2dot14(off + 2); off += 4
                }
                (flags and WE_HAVE_A_TWO_BY_TWO) != 0 -> {
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
            val bytes = sliceTtc(input, ttcIndex) ?: return null
            if (bytes.size < 12) return null
            val sfnt = tag(bytes, 0)
            if (sfnt != "\u0000\u0001\u0000\u0000" && sfnt != "true") return null
            val numTables = readU16(bytes, 4)
            if (12 + numTables * 16 > bytes.size) return null
            val tables = HashMap<String, TableRecord>()
            var off = 12
            repeat(numTables) {
                val name = tag(bytes, off)
                val offset = readU32(bytes, off + 8).toInt()
                val length = readU32(bytes, off + 12).toInt()
                if (offset >= 0 && length >= 0 && offset + length <= bytes.size) {
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

            val unitsPerEm = readU16(bytes, head.offset + 18)
            val xMin = readI16(bytes, head.offset + 36).toInt()
            val yMin = readI16(bytes, head.offset + 38).toInt()
            val xMax = readI16(bytes, head.offset + 40).toInt()
            val yMax = readI16(bytes, head.offset + 42).toInt()
            val indexToLocFormat = readI16(bytes, head.offset + 50).toInt()
            val ascent = readI16(bytes, hhea.offset + 4).toInt()
            val descent = readI16(bytes, hhea.offset + 6).toInt()
            val lineGap = readI16(bytes, hhea.offset + 8).toInt()
            val maxAdvanceWidth = readU16(bytes, hhea.offset + 10)
            val numHMetrics = readU16(bytes, hhea.offset + 34)
            val numGlyphs = readU16(bytes, maxp.offset + 4)
            if (unitsPerEm <= 0 || numGlyphs <= 0 || numHMetrics <= 0) return null

            val advances = IntArray(numGlyphs)
            val bearings = ShortArray(numGlyphs)
            var h = hmtx.offset
            for (i in 0 until numHMetrics) {
                advances[i] = readU16(bytes, h); h += 2
                bearings[i] = readI16(bytes, h); h += 2
            }
            for (i in numHMetrics until numGlyphs) {
                advances[i] = advances[numHMetrics - 1]
                bearings[i] = if (h + 2 <= hmtx.offset + hmtx.length) readI16(bytes, h) else 0
                h += 2
            }

            val offsets = IntArray(numGlyphs + 1)
            if (indexToLocFormat == 0) {
                for (i in 0..numGlyphs) offsets[i] = readU16(bytes, loca.offset + i * 2) * 2
            } else {
                for (i in 0..numGlyphs) offsets[i] = readU32(bytes, loca.offset + i * 4).toInt()
            }
            val cmap = Cmap.parse(bytes, cmapTable) ?: return null
            val familyName = parseName(bytes, tables["name"]) ?: "OpenType"
            val os2 = tables["OS/2"]
            val avg = os2?.let { readI16(bytes, it.offset + 2).toInt() } ?: advances.average().toInt()
            val sxHeight = os2?.takeIf { it.length >= 88 }?.let { readI16(bytes, it.offset + 86).toInt() } ?: unitsPerEm / 2
            val sCapHeight = os2?.takeIf { it.length >= 90 }?.let { readI16(bytes, it.offset + 88).toInt() } ?: (unitsPerEm * 7 / 10)

            return ParsedTrueTypeFont(
                bytes, tables, unitsPerEm, indexToLocFormat, numGlyphs, numHMetrics,
                ascent, descent, lineGap, maxAdvanceWidth, xMin, yMin, xMax, yMax,
                avg, sxHeight, sCapHeight, familyName, cmap, advances, bearings, offsets,
            )
        }

        private fun sliceTtc(bytes: ByteArray, ttcIndex: Int): ByteArray? {
            if (bytes.size >= 12 && tag(bytes, 0) == "ttcf") {
                val numFonts = readU32(bytes, 8).toInt()
                if (ttcIndex < 0 || ttcIndex >= numFonts) return null
                val offset = readU32(bytes, 12 + ttcIndex * 4).toInt()
                if (offset < 0 || offset >= bytes.size) return null
                return bytes.copyOfRange(offset, bytes.size)
            }
            return if (ttcIndex == 0) bytes else null
        }

        private fun parseName(bytes: ByteArray, table: TableRecord?): String? {
            table ?: return null
            if (table.length < 6) return null
            val count = readU16(bytes, table.offset + 2)
            val stringOffset = table.offset + readU16(bytes, table.offset + 4)
            var fallback: String? = null
            var off = table.offset + 6
            repeat(count) {
                if (off + 12 <= table.offset + table.length) {
                    val platform = readU16(bytes, off)
                    val encoding = readU16(bytes, off + 2)
                    val language = readU16(bytes, off + 4)
                    val nameId = readU16(bytes, off + 6)
                    val length = readU16(bytes, off + 8)
                    val strOff = stringOffset + readU16(bytes, off + 10)
                    if (nameId == 1 && strOff >= table.offset && strOff + length <= table.offset + table.length) {
                        val s = decodeName(bytes, strOff, length, platform, encoding)
                        if (!s.isNullOrBlank()) {
                            if (platform == 3 && language == 0x0409) return s
                            fallback = fallback ?: s
                        }
                    }
                }
                off += 12
            }
            return fallback
        }

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

        private fun readU16(bytes: ByteArray, off: Int): Int =
            (((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF))

        private fun readI16(bytes: ByteArray, off: Int): Short = readU16(bytes, off).toShort()

        private fun readU32(bytes: ByteArray, off: Int): Long =
            (((bytes[off].toLong() and 0xFF) shl 24) or
                ((bytes[off + 1].toLong() and 0xFF) shl 16) or
                ((bytes[off + 2].toLong() and 0xFF) shl 8) or
                (bytes[off + 3].toLong() and 0xFF))

        private fun tag(bytes: ByteArray, off: Int): String =
            String(byteArrayOf(bytes[off], bytes[off + 1], bytes[off + 2], bytes[off + 3]), Charsets.ISO_8859_1)
    }
}

private sealed interface Cmap {
    fun glyphId(cp: Int): Int

    companion object {
        fun parse(bytes: ByteArray, table: TableRecord): Cmap? {
            if (table.length < 4) return null
            val numTables = readU16(bytes, table.offset + 2)
            var best: Pair<Int, Int>? = null
            var off = table.offset + 4
            repeat(numTables) {
                val platform = readU16(bytes, off)
                val encoding = readU16(bytes, off + 2)
                val subOffset = readU32(bytes, off + 4).toInt()
                val abs = table.offset + subOffset
                if (abs + 2 <= table.offset + table.length) {
                    val format = readU16(bytes, abs)
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
            return when (readU16(bytes, chosen)) {
                4 -> CmapFormat4.parse(bytes, chosen)
                12 -> CmapFormat12.parse(bytes, chosen)
                else -> null
            }
        }

        private fun readU16(bytes: ByteArray, off: Int): Int =
            (((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF))

        private fun readU32(bytes: ByteArray, off: Int): Long =
            (((bytes[off].toLong() and 0xFF) shl 24) or
                ((bytes[off + 1].toLong() and 0xFF) shl 16) or
                ((bytes[off + 2].toLong() and 0xFF) shl 8) or
                (bytes[off + 3].toLong() and 0xFF))
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
        fun parse(bytes: ByteArray, off: Int): CmapFormat4? {
            val length = readU16(bytes, off + 2)
            val segCount = readU16(bytes, off + 6) / 2
            if (segCount <= 0) return null
            var p = off + 14
            val end = IntArray(segCount) { readU16(bytes, p + it * 2) }
            p += segCount * 2 + 2
            val start = IntArray(segCount) { readU16(bytes, p + it * 2) }
            p += segCount * 2
            val deltas = IntArray(segCount) { readI16(bytes, p + it * 2).toInt() }
            p += segCount * 2
            val rangeOffsets = IntArray(segCount) { readU16(bytes, p + it * 2) }
            p += segCount * 2
            val glyphCount = max(0, (off + length - p) / 2)
            val glyphs = IntArray(glyphCount) { readU16(bytes, p + it * 2) }
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
        fun parse(bytes: ByteArray, off: Int): CmapFormat12? {
            val nGroups = readU32(bytes, off + 12).toInt()
            val groups = ArrayList<Group>(nGroups)
            var p = off + 16
            repeat(nGroups) {
                groups.add(Group(readU32(bytes, p), readU32(bytes, p + 4), readU32(bytes, p + 8)))
                p += 12
            }
            return CmapFormat12(groups)
        }
    }
}

private data class TableRecord(val offset: Int, val length: Int)
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

private fun readU16(bytes: ByteArray, off: Int): Int =
    (((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF))

private fun readI16(bytes: ByteArray, off: Int): Short = readU16(bytes, off).toShort()

private fun readU32(bytes: ByteArray, off: Int): Long =
    (((bytes[off].toLong() and 0xFF) shl 24) or
        ((bytes[off + 1].toLong() and 0xFF) shl 16) or
        ((bytes[off + 2].toLong() and 0xFF) shl 8) or
        (bytes[off + 3].toLong() and 0xFF))

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
