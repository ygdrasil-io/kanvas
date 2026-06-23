package org.skia.foundation.opentype

import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.sfnt.DefaultOpenTypeFaceParser
import org.graphiks.kanvas.font.sfnt.OpenTypeFaceData
import org.graphiks.kanvas.font.sfnt.OpenTypeFaceEvidence
import org.graphiks.kanvas.font.sfnt.OpenTypeStyle
import org.graphiks.kanvas.font.sfnt.OpenTypeStyleSlant
import org.graphiks.kanvas.font.sfnt.rawTableBytes
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.core.SkColorSpaceXformSteps
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkData
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkShader
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkTypeface
import org.skia.foundation.lookupStop
import org.skia.foundation.lookupStopF16
import org.skia.foundation.transformStopColors
import org.skia.foundation.transformStopColorsF16
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.uuid.Uuid

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
    private val paletteSelection: OpenTypePaletteSelection = OpenTypePaletteSelection.Default,
    private val variationPosition: OpenTypeVariationPosition = OpenTypeVariationPosition.Default,
    private val faceData: OpenTypeFaceData? = null,
) : SkTypeface() {
    /**
     * Stable typeface identity from the pure Kotlin font core, when the
     * [DefaultOpenTypeFaceParser] path was used for construction. Null for
     * the legacy [ParsedTrueTypeFont]-only constructor path.
     */
    val typefaceId: TypefaceID? get() = faceData?.id

    /**
     * Private constructor for the pure Kotlin core fact path.
     * Accepts both [faceData] (pure Kotlin OpenType facts) and [font]
     * (legacy scaler/glyph operations). OpenType facts such as family
     * name, style, glyph count, table access, and variation axes are
     * routed through [faceData]; glyph rendering operations remain on
     * the legacy [font] scaler.
     */
    private constructor(
        faceData: OpenTypeFaceData,
        font: ParsedTrueTypeFont,
        fontStyle: SkFontStyle,
        paletteSelection: OpenTypePaletteSelection = OpenTypePaletteSelection.Default,
        variationPosition: OpenTypeVariationPosition = OpenTypeVariationPosition.Default,
    ) : this(font, fontStyle, paletteSelection, variationPosition, faceData)

    override fun countGlyphs(): Int =
        faceData?.metrics?.numGlyphs ?: font.numGlyphs

    override fun getFamilyName(name: StringBuilder) {
        val familyName = faceData?.names?.preferredFamilyName() ?: font.familyName
        name.append(familyName)
    }

    override fun getPostScriptName(): String? =
        faceData?.names?.preferredPostScriptName() ?: font.postScriptName

    internal val hasParsedFontStyle: Boolean
        get() = font.hasParsedFontStyle

    override fun createFamilyNameIterator(): Iterator<SkTypeface.LocalizedString> {
        if (faceData != null) {
            val localized = faceData.names.localizedFamilyNames()
            if (localized.isNotEmpty()) {
                return localized.map { name ->
                    SkTypeface.LocalizedString(name.value, name.languageTag)
                }.iterator()
            }
        }
        return font.localizedFamilyNames.iterator()
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
    ): SkPath? = font.glyphPath(glyphId, size, scaleX, skewX, variationPosition)

    override fun getGlyphBoundsInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkRect = font.glyphBounds(glyphId, size, scaleX, skewX, variationPosition)

    override fun getKerningPairAdjustments(glyphs: ShortArray): IntArray? =
        font.kerningPairAdjustments(glyphs)

    override fun copyTableData(tag: Int): ByteArray? {
        if (faceData != null) {
            val tagStr = openTypeTagToString(tag)
            val table = faceData.rawTableBytes(tagStr)
            if (table != null) return table
        }
        return font.tableData(tag)
    }

    override fun getVariationDesignParameters(): List<SkFontVariation.Axis> {
        if (faceData != null && faceData.variations.axes.isNotEmpty()) {
            return faceData.variations.axes.map { axis ->
                SkFontVariation.Axis(
                    tag = axis.tag.rawValue,
                    min = axis.minimum.value.toFloat(),
                    default = axis.defaultValue.value.toFloat(),
                    max = axis.maximum.value.toFloat(),
                    flags = axis.flags,
                    nameId = axis.nameId,
                )
            }
        }
        return font.variationAxes
    }

    internal fun colorPalettes(): List<List<Int>> =
        font.colorPalettes()

    internal fun colorLayers(glyphId: Int): List<OpenTypeColorLayer> =
        font.colorLayers(glyphId)

    internal fun colorPaint(glyphId: Int): OpenTypeColorPaint? =
        font.colorPaint(glyphId)

    internal fun svgDocument(glyphId: Int): OpenTypeSvgDocument? =
        font.svgDocument(glyphId)

    internal fun bitmapGlyph(glyphId: Int): OpenTypeBitmapGlyph? =
        font.bitmapGlyph(glyphId)

    internal fun makeBitmapTextGlyphs(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
    ): List<OpenTypePositionedBitmapGlyph> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<OpenTypePositionedBitmapGlyph>()
        var penX = x
        val glyphs = text.codePoints().toArray().let { codepoints ->
            IntArray(codepoints.size) { font.glyphForCodepoint(codepoints[it]) }
        }
        for (i in glyphs.indices) {
            val glyphId = glyphs[i]
            val bitmapGlyph = font.bitmapGlyph(glyphId)
            if (bitmapGlyph != null) {
                out += OpenTypePositionedBitmapGlyph(bitmapGlyph, penX, y)
            }
            penX += font.advanceWidth(glyphId) * font.scale(size) * scaleX
            if (i < glyphs.lastIndex) {
                penX += font.kerningAdjustment(glyphId, glyphs[i + 1]) * font.scale(size) * scaleX
            }
        }
        return out
    }

    internal fun makeColorTextPaths(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        @Suppress("UNUSED_PARAMETER") isSubpixel: Boolean,
    ): List<OpenTypeColorPath>? {
        if (text.isEmpty()) return null
        val palette = paletteSelection.resolve(font.colorPalettes()) ?: return null
        val out = ArrayList<OpenTypeColorPath>()
        var hasColorGlyph = false
        var penX = x
        val glyphs = text.codePoints().toArray().let { codepoints ->
            IntArray(codepoints.size) { font.glyphForCodepoint(codepoints[it]) }
        }
        for (i in glyphs.indices) {
            val glyphId = glyphs[i]
            val layers = font.colorLayers(glyphId)
            if (layers.isEmpty()) {
                val colorPaths = font.colorPaint(glyphId)
                    ?.let { paint ->
                        val clipPaths = font.colorClipPath(glyphId, penX, y, size, scaleX, skewX, SkMatrix.Identity)
                            ?.let(::listOf)
                            .orEmpty()
                        colorPaintPaths(
                            paint = paint,
                            seenColorGlyphs = setOf(glyphId),
                            palette = palette,
                            penX = penX,
                            baselineY = y,
                            size = size,
                            scaleX = scaleX,
                            skewX = skewX,
                            clipPaths = clipPaths,
                            depth = 0,
                        )
                    }
                    .orEmpty()
                if (colorPaths.isNotEmpty()) {
                    hasColorGlyph = true
                    out.addAll(colorPaths)
                } else {
                    val glyphPath = font.glyphPath(glyphId, size, scaleX, skewX, variationPosition)
                    if (glyphPath != null && !glyphPath.isEmpty()) {
                        out.add(OpenTypeColorPath(null, positionedPath(glyphPath, penX, y)))
                    }
                }
            } else {
                hasColorGlyph = true
                for (layer in layers) {
                    val color = when (layer.paletteIndex) {
                        COLR_FOREGROUND_PALETTE_INDEX -> null
                        else -> palette.getOrNull(layer.paletteIndex) ?: return null
                    }
                    val layerPath = font.glyphPath(layer.glyphId, size, scaleX, skewX, variationPosition) ?: continue
                    if (!layerPath.isEmpty()) {
                        out.add(OpenTypeColorPath(color, positionedPath(layerPath, penX, y)))
                    }
                }
            }
            penX += font.advanceWidth(glyphId) * font.scale(size) * scaleX
            if (i < glyphs.lastIndex) {
                penX += font.kerningAdjustment(glyphId, glyphs[i + 1]) * font.scale(size) * scaleX
            }
        }
        return out.takeIf { hasColorGlyph && it.isNotEmpty() }
    }

    private fun colorPaintPaths(
        paint: OpenTypeColorPaint,
        seenColorGlyphs: Set<Int>,
        palette: List<Int>,
        penX: SkScalar,
        baselineY: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        transform: SkMatrix = SkMatrix.Identity,
        glyphPath: SkPath? = null,
        clipPaths: List<SkPath> = emptyList(),
        depth: Int,
    ): List<OpenTypeColorPath> {
        if (depth > MAX_COLOR_PAINT_DEPTH) return emptyList()
        return when (paint) {
            is OpenTypeColorPaint.Layers -> {
                if (paint.paints.size > MAX_LAYERS_PER_COLOR_GLYPH) return emptyList()
                paint.paints.flatMap { layerPaint ->
                    colorPaintPaths(
                        paint = layerPaint,
                        seenColorGlyphs = seenColorGlyphs,
                        palette = palette,
                        penX = penX,
                        baselineY = baselineY,
                        size = size,
                        scaleX = scaleX,
                        skewX = skewX,
                        transform = transform,
                        glyphPath = glyphPath,
                        clipPaths = clipPaths,
                        depth = depth + 1,
                    )
                }
            }
            is OpenTypeColorPaint.Solid -> {
                val currentPath = glyphPath ?: return emptyList()
                val color = when (paint.paletteIndex) {
                    COLR_FOREGROUND_PALETTE_INDEX -> null
                    else -> palette.getOrNull(paint.paletteIndex) ?: return emptyList()
                }
                if (currentPath.isEmpty()) {
                    emptyList()
                } else {
                    listOf(OpenTypeColorPath(color, positionedPath(currentPath, penX, baselineY), paint.alpha, clipPaths))
                }
            }
            is OpenTypeColorPaint.LinearGradient -> {
                val currentPath = glyphPath ?: return emptyList()
                if (currentPath.isEmpty()) return emptyList()
                val shader = colorLinearGradientShader(paint, palette, penX, baselineY, size, scaleX, skewX, transform)
                    ?: return emptyList()
                listOf(OpenTypeColorPath(null, positionedPath(currentPath, penX, baselineY), shader = shader, clipPaths = clipPaths))
            }
            is OpenTypeColorPaint.RadialGradient -> {
                val currentPath = glyphPath ?: return emptyList()
                if (currentPath.isEmpty()) return emptyList()
                val shader = colorRadialGradientShader(paint, palette, penX, baselineY, size, scaleX, skewX, transform)
                    ?: return emptyList()
                listOf(OpenTypeColorPath(null, positionedPath(currentPath, penX, baselineY), shader = shader, clipPaths = clipPaths))
            }
            is OpenTypeColorPaint.SweepGradient -> {
                val currentPath = glyphPath ?: return emptyList()
                if (currentPath.isEmpty()) return emptyList()
                val shader = colorSweepGradientShader(paint, palette, penX, baselineY, size, scaleX, skewX, transform)
                    ?: return emptyList()
                listOf(OpenTypeColorPath(null, positionedPath(currentPath, penX, baselineY), shader = shader, clipPaths = clipPaths))
            }
            is OpenTypeColorPaint.Composite -> {
                val backdrop = colorPaintPaths(
                    paint = paint.backdrop,
                    seenColorGlyphs = seenColorGlyphs,
                    palette = palette,
                    penX = penX,
                    baselineY = baselineY,
                    size = size,
                    scaleX = scaleX,
                    skewX = skewX,
                    transform = transform,
                    glyphPath = glyphPath,
                    clipPaths = clipPaths,
                    depth = depth + 1,
                )
                if (backdrop.isEmpty()) return emptyList()
                val source = colorPaintPaths(
                    paint = paint.source,
                    seenColorGlyphs = seenColorGlyphs,
                    palette = palette,
                    penX = penX,
                    baselineY = baselineY,
                    size = size,
                    scaleX = scaleX,
                    skewX = skewX,
                    transform = transform,
                    glyphPath = glyphPath,
                    clipPaths = clipPaths,
                    depth = depth + 1,
                )
                if (source.isEmpty()) return emptyList()
                listOf(
                    OpenTypeColorPath(
                        null,
                        emptyColorPath(),
                        children = backdrop + OpenTypeColorPath(
                            null,
                            emptyColorPath(),
                            blendMode = paint.mode,
                            children = source,
                        ),
                    ),
                )
            }
            is OpenTypeColorPaint.Glyph -> {
                val childPath = font.glyphPath(paint.glyphId, size, scaleX, skewX, variationPosition)
                    ?.makeTransform(transform)
                    ?: return emptyList()
                colorPaintPaths(
                    paint = paint.paint,
                    seenColorGlyphs = seenColorGlyphs,
                    palette = palette,
                    penX = penX,
                    baselineY = baselineY,
                    size = size,
                    scaleX = scaleX,
                    skewX = skewX,
                    transform = transform,
                    glyphPath = childPath,
                    clipPaths = clipPaths,
                    depth = depth + 1,
                )
            }
            is OpenTypeColorPaint.ColrGlyph -> {
                if (paint.glyphId in seenColorGlyphs) return emptyList()
                val referenced = font.colorPaint(paint.glyphId) ?: return emptyList()
                val referencedClip = font.colorClipPath(paint.glyphId, penX, baselineY, size, scaleX, skewX, transform)
                colorPaintPaths(
                    paint = referenced,
                    seenColorGlyphs = seenColorGlyphs + paint.glyphId,
                    palette = palette,
                    penX = penX,
                    baselineY = baselineY,
                    size = size,
                    scaleX = scaleX,
                    skewX = skewX,
                    transform = transform,
                    glyphPath = glyphPath,
                    clipPaths = if (referencedClip == null) clipPaths else clipPaths + referencedClip,
                    depth = depth + 1,
                )
            }
            is OpenTypeColorPaint.Transform -> {
                val matrix = colrFontMatrixToCanvas(paint.xx, paint.yx, paint.xy, paint.yy, paint.dx, paint.dy, size, scaleX, skewX)
                colorPaintPaths(
                    paint = paint.paint,
                    seenColorGlyphs = seenColorGlyphs,
                    palette = palette,
                    penX = penX,
                    baselineY = baselineY,
                    size = size,
                    scaleX = scaleX,
                    skewX = skewX,
                    transform = transform.preConcat(matrix),
                    glyphPath = glyphPath?.makeTransform(matrix),
                    clipPaths = clipPaths,
                    depth = depth + 1,
                )
            }
            is OpenTypeColorPaint.Translate -> {
                val matrix = colrFontMatrixToCanvas(1f, 0f, 0f, 1f, paint.dx.toFloat(), paint.dy.toFloat(), size, scaleX, skewX)
                colorPaintPaths(
                    paint = paint.paint,
                    seenColorGlyphs = seenColorGlyphs,
                    palette = palette,
                    penX = penX,
                    baselineY = baselineY,
                    size = size,
                    scaleX = scaleX,
                    skewX = skewX,
                    transform = transform.preConcat(matrix),
                    glyphPath = glyphPath?.makeTransform(matrix),
                    clipPaths = clipPaths,
                    depth = depth + 1,
                )
            }
        }
    }

    private fun colorLinearGradientShader(
        paint: OpenTypeColorPaint.LinearGradient,
        palette: List<Int>,
        penX: Float,
        baselineY: Float,
        size: Float,
        scaleX: Float,
        skewX: Float,
        transform: SkMatrix,
    ): SkShader? {
        val stops = colorShaderStops(paint.colorLine, palette) ?: return null
        val p0 = colorFontPointToCanvas(paint.x0, paint.y0, size, scaleX, skewX, transform, penX, baselineY)
        val p1 = colorFontPointToCanvas(paint.x1, paint.y1, size, scaleX, skewX, transform, penX, baselineY)
        val p2 = colorFontPointToCanvas(paint.x2, paint.y2, size, scaleX, skewX, transform, penX, baselineY)
        val qx = p2.fX - p0.fX
        val qy = p2.fY - p0.fY
        val nx = -qy
        val ny = qx
        val denom = nx * nx + ny * ny
        if (denom <= 0.000001f) return null
        val vx = p1.fX - p0.fX
        val vy = p1.fY - p0.fY
        val projectedScale = (vx * nx + vy * ny) / denom
        val end = SkPoint(p0.fX + nx * projectedScale, p0.fY + ny * projectedScale)
        val dx = end.fX - p0.fX
        val dy = end.fY - p0.fY
        if (dx * dx + dy * dy <= 0.000001f) return null
        return OpenTypeLinearGradientShader(
            p0 = p0,
            p1 = end,
            srcColors = stops.colors,
            positions = stops.positions,
            tileMode = stops.tileMode,
            tBias = stops.tBias,
            tScale = stops.tScale,
        )
    }

    private fun colorRadialGradientShader(
        paint: OpenTypeColorPaint.RadialGradient,
        palette: List<Int>,
        penX: Float,
        baselineY: Float,
        size: Float,
        scaleX: Float,
        skewX: Float,
        transform: SkMatrix,
    ): SkShader? {
        if (!transform.isIdentity) return null
        val stops = colorShaderStops(paint.colorLine, palette) ?: return null
        val c0 = colorFontPointToCanvas(paint.x0, paint.y0, size, scaleX, skewX, transform, penX, baselineY)
        val c1 = colorFontPointToCanvas(paint.x1, paint.y1, size, scaleX, skewX, transform, penX, baselineY)
        val s = font.scale(size)
        val r0 = paint.radius0 * s * abs(scaleX)
        val r1 = paint.radius1 * s * abs(scaleX)
        if (r0 < 0f || r1 < 0f) return null
        if (c0.fX == c1.fX && c0.fY == c1.fY && r0 == r1) return null
        return OpenTypeTwoPointRadialGradientShader(c0, r0, c1, r1, stops.colors, stops.positions, stops.tileMode, stops.tBias, stops.tScale)
    }

    private fun colorSweepGradientShader(
        paint: OpenTypeColorPaint.SweepGradient,
        palette: List<Int>,
        penX: Float,
        baselineY: Float,
        size: Float,
        scaleX: Float,
        skewX: Float,
        transform: SkMatrix,
    ): SkShader? {
        if (!transform.isIdentity) return null
        val stops = colorShaderStops(paint.colorLine, palette) ?: return null
        val center = colorFontPointToCanvas(paint.centerX, paint.centerY, size, scaleX, skewX, transform, penX, baselineY)
        val startAngle = colorSweepAngleToDesignDegrees(paint.startAngle)
        val endAngle = colorSweepAngleToDesignDegrees(paint.endAngle)
        if (!startAngle.isFinite() || !endAngle.isFinite() || startAngle == endAngle) return null
        return OpenTypeSweepGradientShader(center, startAngle, endAngle, stops.colors, stops.positions, stops.tileMode, stops.tBias, stops.tScale)
    }

    private fun colorShaderStops(colorLine: OpenTypeColorLine, palette: List<Int>): OpenTypeShaderStops? {
        val sortedStops = colorLine.stops
            .withIndex()
            .sortedWith(compareBy<IndexedValue<OpenTypeColorStop>> { it.value.offset }.thenBy { it.index })
            .map { it.value }
        val firstOffset = sortedStops.first().offset
        val lastOffset = sortedStops.last().offset
        val offsetSpan = lastOffset - firstOffset
        if (offsetSpan <= 0.000001f) return null
        val colors = IntArray(sortedStops.size)
        val positions = FloatArray(sortedStops.size)
        for (i in sortedStops.indices) {
            val stop = sortedStops[i]
            if (stop.paletteIndex == COLR_FOREGROUND_PALETTE_INDEX) return null
            val color = palette.getOrNull(stop.paletteIndex) ?: return null
            val alpha = ((SkColorGetA(color) / 255f) * stop.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
            colors[i] = SkColorSetARGB(alpha, SkColorGetR(color), SkColorGetG(color), SkColorGetB(color))
            positions[i] = (stop.offset - firstOffset) / offsetSpan
        }
        return OpenTypeShaderStops(colors, positions, colorLine.extend.toTileMode(), firstOffset, offsetSpan)
    }

    private fun colorSweepAngleToDesignDegrees(angle: Float): Float = (angle + 1f) * 180f

    private fun emptyColorPath(): SkPath =
        SkPathBuilder().setFillType(SkPathFillType.kWinding).detach()

    private fun colorFontPointToCanvas(
        x: Int,
        y: Int,
        size: Float,
        scaleX: Float,
        skewX: Float,
        transform: SkMatrix,
        penX: Float,
        baselineY: Float,
    ): SkPoint {
        val s = font.scale(size)
        val localY = -y * s
        val localX = x * s * scaleX + skewX * localY
        val tx = transform.sx * localX + transform.kx * localY + transform.tx
        val ty = transform.ky * localX + transform.sy * localY + transform.ty
        return SkPoint(penX + tx, baselineY + ty)
    }

    private fun ParsedTrueTypeFont.colorClipPath(
        glyphId: Int,
        penX: Float,
        baselineY: Float,
        size: Float,
        scaleX: Float,
        skewX: Float,
        transform: SkMatrix,
    ): SkPath? {
        val box = colorClipBox(glyphId) ?: return null
        val s = scale(size)
        fun tx(x: Float, y: Float): Float {
            val sy = -y * s
            return x * s * scaleX + skewX * sy
        }
        fun ty(y: Float): Float = -y * s
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kWinding)
            .moveTo(tx(box.xMin.toFloat(), box.yMin.toFloat()), ty(box.yMin.toFloat()))
            .lineTo(tx(box.xMax.toFloat(), box.yMin.toFloat()), ty(box.yMin.toFloat()))
            .lineTo(tx(box.xMax.toFloat(), box.yMax.toFloat()), ty(box.yMax.toFloat()))
            .lineTo(tx(box.xMin.toFloat(), box.yMax.toFloat()), ty(box.yMax.toFloat()))
            .close()
            .detach()
            .makeTransform(transform)
        return if (path.isEmpty()) null else positionedPath(path, penX, baselineY)
    }

    private fun colrFontMatrixToCanvas(
        xx: Float,
        yx: Float,
        xy: Float,
        yy: Float,
        dx: Float,
        dy: Float,
        size: Float,
        scaleX: Float,
        skewX: Float,
    ): SkMatrix {
        val s = font.scale(size)
        val effectiveScaleX = scaleX.takeIf { it != 0f } ?: 1f
        return SkMatrix(
            sx = xx - (skewX / effectiveScaleX) * yx,
            kx = -(effectiveScaleX * xy - skewX * yy + skewX * xx - (skewX * skewX / effectiveScaleX) * yx),
            tx = s * (effectiveScaleX * dx - skewX * dy),
            ky = -yx / effectiveScaleX,
            sy = yy + (skewX / effectiveScaleX) * yx,
            ty = -s * dy,
        )
    }

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
            val glyphPath = font.glyphPath(glyphId, size, scaleX, skewX, variationPosition)
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
                val glyphBounds = font.glyphBounds(glyphId, size, scaleX, skewX, variationPosition)
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
        return OpenTypeTypeface(
            font,
            fontStyle,
            OpenTypePaletteSelection.from(args.palette),
            font.variationPosition(args.variationDesignPosition),
        )
    }

    internal fun withFontStyle(style: SkFontStyle): OpenTypeTypeface =
        OpenTypeTypeface(font, style, paletteSelection, variationPosition)

    private fun positionedPath(path: SkPath, x: SkScalar, y: SkScalar): SkPath =
        SkPathBuilder()
            .setFillType(SkPathFillType.kWinding)
            .addPathOffset(path, x, y)
            .detach()

    /**
     * Returns a facade parity evidence dump comparing the pure Kotlin core
     * [OpenTypeFaceData] facts with the legacy [ParsedTrueTypeFont] facts.
     *
     * The dump includes the typeface identity, family name, PostScript name,
     * font style, glyph count, table tags, variation axis count, and diagnostic
     * counts. Returns null when the pure Kotlin core path was not used
     * (legacy-only construction).
     */
    internal fun facadeParityEvidence(): OpenTypeFaceParityDump? {
        val fd = faceData ?: return null
        return OpenTypeFaceParityDump(
            typefaceId = fd.id.value.toString(),
            coreFamilyName = fd.names.preferredFamilyName(),
            legacyFamilyName = font.familyName,
            corePostScriptName = fd.names.preferredPostScriptName(),
            legacyPostScriptName = font.postScriptName,
            coreGlyphCount = fd.metrics.numGlyphs,
            legacyGlyphCount = font.numGlyphs,
            coreTableTags = fd.directory.tables.map { it.tag.value }.sorted(),
            legacyTableTags = font.tableTags(),
            coreAxisCount = fd.variations.axes.size,
            legacyAxisCount = font.variationAxes.size,
            coreDiagnosticCount = fd.diagnostics.size,
        )
    }

    public companion object {
        @Suppress("FunctionName")
        public fun MakeFromBytes(bytes: ByteArray, ttcIndex: Int = 0): OpenTypeTypeface? =
            ParsedTrueTypeFont.parse(bytes, ttcIndex)?.let { OpenTypeTypeface(it, it.fontStyle) }

        /**
         * Creates an [OpenTypeTypeface] using the pure Kotlin
         * [DefaultOpenTypeFaceParser] for OpenType facts while keeping the
         * [ParsedTrueTypeFont] for glyph scaler operations.
         *
         * This is the recommended construction path for M13 code. The legacy
         * [MakeFromBytes] path remains available for backward compatibility.
         *
         * OpenType facts such as family name, PostScript name, style, glyph
         * count, table access, and variation axes are routed through the pure
         * Kotlin core types. Glyph rendering (paths, widths, bounds, color)
         * continues through the legacy [ParsedTrueTypeFont] scaler.
         *
         * Returns null when the font bytes are too malformed for either parser.
         * When the [DefaultOpenTypeFaceParser] fails but [ParsedTrueTypeFont]
         * succeeds, the typeface is constructed with only the legacy path
         * (equivalent to [MakeFromBytes]) and the [faceData] field is null.
         */
        @Suppress("FunctionName")
        public fun MakeFromBytesWithCorePath(bytes: ByteArray, ttcIndex: Int = 0): OpenTypeTypeface? {
            val parsed = ParsedTrueTypeFont.parse(bytes, ttcIndex) ?: return null
            val source = FontSource(
                id = FontSourceID(Uuid.random()),
                kind = FontSourceKind.MEMORY,
                displayName = "OpenType Typeface",
                bytes = bytes,
            )
            return try {
                val parser = DefaultOpenTypeFaceParser()
                val faceData = parser.parse(source, ttcIndex)
                OpenTypeTypeface(
                    faceData = faceData,
                    font = parsed,
                    fontStyle = faceData.style.toSkFontStyle(),
                )
            } catch (_: IllegalArgumentException) {
                OpenTypeTypeface(parsed, parsed.fontStyle)
            }
        }
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
    val fontStyle: SkFontStyle,
    val hasParsedFontStyle: Boolean,
    val variationAxes: List<SkFontVariation.Axis>,
    private val cmap: Cmap,
    private val advanceWidths: IntArray,
    private val leftSideBearings: ShortArray,
    private val glyphOffsets: IntArray,
    private val kern: KernTable?,
    private val gpos: GposPairTable?,
    private val color: OpenTypeColorFont?,
    private val svg: OpenTypeSvgTable?,
    private val bitmap: OpenTypeBitmapFont?,
    private val gvar: GvarTable?,
) {
    private val pathCache = HashMap<Int, GlyphOutline?>()

    fun scale(size: Float): Float = size / unitsPerEm.toFloat()

    fun glyphForCodepoint(cp: Int): Int = cmap.glyphId(cp).coerceIn(0, numGlyphs - 1)

    fun advanceWidth(glyphId: Int): Int {
        if (glyphId < 0 || glyphId >= numGlyphs) return 0
        return advanceWidths[min(glyphId, advanceWidths.lastIndex)]
    }

    fun kerningPairAdjustments(glyphs: ShortArray): IntArray? {
        if (kern == null && gpos == null) return null
        if (glyphs.size <= 1) return IntArray(0)
        return IntArray(glyphs.size - 1) { i ->
            kerningAdjustment(glyphs[i].toInt() and 0xFFFF, glyphs[i + 1].toInt() and 0xFFFF)
        }
    }

    fun kerningAdjustment(leftGlyphId: Int, rightGlyphId: Int): Int =
        kern?.adjustment(leftGlyphId, rightGlyphId)
            ?: gpos?.adjustment(leftGlyphId, rightGlyphId)
            ?: 0

    fun tableTags(): List<String> = tables.keys.toList()

    fun tableData(tag: Int): ByteArray? {
        val record = tables[openTypeTagToString(tag)] ?: return null
        return bytes.copyOfRange(record.offset, record.offset + record.length)
    }

    fun colorPalettes(): List<List<Int>> =
        color?.palettes ?: emptyList()

    fun colorLayers(glyphId: Int): List<OpenTypeColorLayer> =
        color?.layersByGlyph?.get(glyphId) ?: emptyList()

    fun colorPaint(glyphId: Int): OpenTypeColorPaint? =
        color?.v1PaintsByGlyph?.get(glyphId)

    fun colorClipBox(glyphId: Int): OpenTypeClipBox? =
        color?.clipBoxes?.firstOrNull { glyphId in it.startGlyphId..it.endGlyphId }?.box

    fun svgDocument(glyphId: Int): OpenTypeSvgDocument? =
        svg?.documentForGlyph(glyphId)

    fun bitmapGlyph(glyphId: Int): OpenTypeBitmapGlyph? =
        bitmap?.glyph(glyphId)

    fun variationPosition(position: SkFontArguments.VariationPosition): OpenTypeVariationPosition {
        if (variationAxes.isEmpty() || position.coordinates.isEmpty()) return OpenTypeVariationPosition.Default
        val values = variationAxes.associate { it.tag to it.default }.toMutableMap()
        for (coordinate in position.coordinates) {
            val axis = variationAxes.firstOrNull { it.tag == coordinate.axis } ?: continue
            values[axis.tag] = coordinate.value.coerceIn(axis.min, axis.max)
        }
        val normalized = FloatArray(variationAxes.size)
        variationAxes.forEachIndexed { index, axis ->
            val value = values[axis.tag] ?: axis.default
            normalized[index] = when {
                value == axis.default -> 0f
                value < axis.default && axis.default != axis.min -> (value - axis.default) / (axis.default - axis.min)
                value > axis.default && axis.max != axis.default -> (value - axis.default) / (axis.max - axis.default)
                else -> 0f
            }.coerceIn(-1f, 1f)
        }
        return if (normalized.all { it == 0f }) OpenTypeVariationPosition.Default else OpenTypeVariationPosition(normalized)
    }

    fun glyphBounds(glyphId: Int, size: Float, scaleX: Float, skewX: Float, variation: OpenTypeVariationPosition): SkRect {
        val outline = glyphOutline(glyphId, variation) ?: return SkRect.MakeEmpty()
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

    fun glyphPath(glyphId: Int, size: Float, scaleX: Float, skewX: Float, variation: OpenTypeVariationPosition): SkPath? {
        val outline = glyphOutline(glyphId, variation) ?: return null
        if (outline.contours.isEmpty()) return null
        val builder = SkPathBuilder().setFillType(SkPathFillType.kWinding)
        for (contour in outline.contours) {
            emitContour(builder, contour, size, scaleX, skewX)
        }
        val out = builder.detach()
        return if (out.isEmpty()) null else out
    }

    private fun glyphOutline(glyphId: Int, variation: OpenTypeVariationPosition): GlyphOutline? =
        if (variation.isDefault || gvar == null) {
            pathCache.getOrPut(glyphId) { readGlyph(glyphId, depth = 0, variation = OpenTypeVariationPosition.Default) }
        } else {
            readGlyph(glyphId, depth = 0, variation = variation)
        }

    private fun readGlyph(glyphId: Int, depth: Int, variation: OpenTypeVariationPosition): GlyphOutline? {
        if (glyphId < 0 || glyphId >= numGlyphs || depth > 8) return null
        val glyf = tables["glyf"] ?: return null
        val start = glyphOffsets[glyphId]
        val end = glyphOffsets.getOrElse(glyphId + 1) { start }
        if (start == end) return GlyphOutline(emptyList())
        if (start < 0 || end < start || !fits(glyf.offset, end, bytes.size) || end > glyf.length) return null
        val p = glyf.offset + start
        if (!fits(p, 10, bytes.size)) return null
        val numberOfContours = i16(p).toInt()
        if (numberOfContours >= 0) return readSimpleGlyph(glyphId, p, numberOfContours, variation)
        return readCompositeGlyph(p, depth, variation)
    }

    private fun readSimpleGlyph(
        glyphId: Int,
        p: Int,
        numberOfContours: Int,
        variation: OpenTypeVariationPosition,
    ): GlyphOutline? {
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
        val xs = FloatArray(pointCount)
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
            xs[j] = x.toFloat()
        }
        val ys = FloatArray(pointCount)
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
            ys[j] = y.toFloat()
        }
        if (!variation.isDefault && gvar != null) {
            gvar.simpleGlyphDeltas(glyphId, pointCount, variation.normalizedCoordinates)?.let { deltas ->
                for (pointIndex in 0 until pointCount) {
                    xs[pointIndex] += deltas.x[pointIndex]
                    ys[pointIndex] += deltas.y[pointIndex]
                }
            }
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

    private fun readCompositeGlyph(p: Int, depth: Int, variation: OpenTypeVariationPosition): GlyphOutline? {
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
            val child = readGlyph(componentGlyph, depth + 1, variation)
            child?.contours?.forEach { contour ->
                contours.add(contour.map { pt ->
                    val x = a * pt.x + b * pt.y + dx
                    val y = c * pt.x + d * pt.y + dy
                    TtPoint(x.toInt().toFloat(), y.toInt().toFloat(), pt.onCurve)
                })
            }
        } while ((flags and MORE_COMPONENTS) != 0)
        return GlyphOutline(contours)
    }

    private fun transformX(x: Float, y: Float, size: Float, scaleX: Float, skewX: Float): Float {
        val s = scale(size)
        val sy = -y * s
        return x * s * scaleX + skewX * sy
    }

    private fun transformY(y: Float, size: Float): Float = -y * scale(size)

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
            val fontStyle = parseFontStyle(reader, tables["OS/2"], tables["head"], tables["post"], names)
            val sxHeight = os2?.takeIf { it.length >= 88 }?.let { reader.i16(it.offset + 86)?.toInt() ?: return null } ?: unitsPerEm / 2
            val sCapHeight = os2?.takeIf { it.length >= 90 }?.let { reader.i16(it.offset + 88)?.toInt() ?: return null } ?: (unitsPerEm * 7 / 10)
            val post = tables["post"]
            val underlinePosition = post?.takeIf { it.length >= 12 }?.let { reader.i16(it.offset + 8)?.toInt() ?: return null } ?: -(unitsPerEm / 9)
            val underlineThickness = post?.takeIf { it.length >= 12 }?.let { reader.i16(it.offset + 10)?.toInt() ?: return null } ?: max(1, unitsPerEm / 14)
            val strikeoutThickness = os2?.takeIf { it.length >= 30 }?.let { reader.i16(it.offset + 26)?.toInt() ?: return null } ?: max(1, unitsPerEm / 14)
            val strikeoutPosition = os2?.takeIf { it.length >= 30 }?.let { reader.i16(it.offset + 28)?.toInt() ?: return null } ?: unitsPerEm * 3 / 10
            val kern = parseKernTable(bytes, tables["kern"])
            val gpos = if (kern == null && "kern" !in tables) parseGposPairTable(bytes, tables["GPOS"], numGlyphs) else null
            val variationAxes = parseFvarAxes(bytes, tables["fvar"]).orEmpty()
            val color = parseColorFont(bytes, tables["COLR"], tables["CPAL"])
            val svg = parseSvgTable(bytes, tables["SVG "])
            val bitmap = parseBitmapFont(bytes, tables["CBDT"], tables["CBLC"], tables["sbix"], numGlyphs)
            val gvar = parseGvarTable(bytes, tables["gvar"], variationAxes.size, numGlyphs)

            return ParsedTrueTypeFont(
                bytes, tables, unitsPerEm, indexToLocFormat, numGlyphs, numHMetrics,
                ascent, descent, lineGap, maxAdvanceWidth, xMin, yMin, xMax, yMax,
                avg, sxHeight, sCapHeight, underlinePosition, underlineThickness,
                strikeoutPosition, strikeoutThickness, familyName, names?.postScriptName, localizedNames,
                fontStyle.style, fontStyle.hasMetadata, variationAxes, cmap, advances, bearings, offsets, kern, gpos, color, svg, bitmap, gvar,
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
            val styleCandidates = ArrayList<NameRecord>()
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
                            2, 17 -> styleCandidates.add(record)
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
            return OpenTypeNames(familyName, chooseName(postScriptCandidates), localized, chooseName(styleCandidates))
        }

        private fun parseFontStyle(
            reader: SfntReader,
            os2: TableRecord?,
            head: TableRecord?,
            post: TableRecord?,
            names: OpenTypeNames?,
        ): ParsedOpenTypeStyle {
            val weight = os2
                ?.takeIf { it.length >= 6 }
                ?.let { reader.u16(it.offset + 4) }
                ?.coerceIn(SkFontStyle.kInvisible_Weight, SkFontStyle.kExtraBlack_Weight)
                ?: SkFontStyle.kNormal_Weight
            val width = os2
                ?.takeIf { it.length >= 8 }
                ?.let { reader.u16(it.offset + 6) }
                ?.coerceIn(SkFontStyle.kUltraCondensed_Width, SkFontStyle.kUltraExpanded_Width)
                ?: SkFontStyle.kNormal_Width
            val fsSelection = os2
                ?.takeIf { it.length >= 64 }
                ?.let { reader.u16(it.offset + 62) }
                ?: 0
            val macStyle = head
                ?.takeIf { it.length >= 46 }
                ?.let { reader.u16(it.offset + 44) }
                ?: 0
            val italicAngle = post
                ?.takeIf { it.length >= 8 }
                ?.let { reader.i16(it.offset + 4)?.toInt() }
                ?: 0
            val styleName = names?.styleName.orEmpty().lowercase()
            val italic =
                (fsSelection and OS2_FS_SELECTION_ITALIC) != 0 ||
                    (macStyle and HEAD_MAC_STYLE_ITALIC) != 0 ||
                    "italic" in styleName
            val oblique =
                (fsSelection and OS2_FS_SELECTION_OBLIQUE) != 0 ||
                    italicAngle != 0 ||
                    "oblique" in styleName
            val bold =
                (fsSelection and OS2_FS_SELECTION_BOLD) != 0 ||
                    (macStyle and HEAD_MAC_STYLE_BOLD) != 0
            val resolvedWeight = if (bold && weight < SkFontStyle.kBold_Weight) SkFontStyle.kBold_Weight else weight
            val slant = when {
                italic -> SkFontStyle.Slant.kItalic_Slant
                oblique -> SkFontStyle.Slant.kOblique_Slant
                else -> SkFontStyle.Slant.kUpright_Slant
            }
            return ParsedOpenTypeStyle(
                SkFontStyle(resolvedWeight, width, slant),
                hasMetadata = os2 != null || head != null || post != null || names?.styleName != null,
            )
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

        private fun parseFvarAxes(bytes: ByteArray, table: TableRecord?): List<SkFontVariation.Axis>? {
            table ?: return null
            if (table.length < 16) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val majorVersion = reader.u16(table.offset) ?: return null
            val minorVersion = reader.u16(table.offset + 2) ?: return null
            if (majorVersion != 1 || minorVersion != 0) return null
            val axesArrayOffset = reader.u16(table.offset + 4) ?: return null
            val axisCount = reader.u16(table.offset + 8) ?: return null
            val axisSize = reader.u16(table.offset + 10) ?: return null
            if (axisSize < 20) return null
            val axesStart = table.offset + axesArrayOffset
            if (!reader.fits(axesStart, axisCount.toLong() * axisSize.toLong())) return null
            return List(axisCount) { index ->
                val off = axesStart + index * axisSize
                SkFontVariation.Axis(
                    tag = openTypeTagToInt(reader.tag(off) ?: return null),
                    min = reader.fixed16Dot16(off + 4) ?: return null,
                    default = reader.fixed16Dot16(off + 8) ?: return null,
                    max = reader.fixed16Dot16(off + 12) ?: return null,
                    flags = reader.u16(off + 16) ?: return null,
                    nameId = reader.u16(off + 18) ?: return null,
                )
            }
        }

        private fun parseGvarTable(
            bytes: ByteArray,
            table: TableRecord?,
            axisCount: Int,
            glyphCount: Int,
        ): GvarTable? {
            table ?: return null
            if (axisCount <= 0 || glyphCount <= 0 || table.length < 20) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val majorVersion = reader.u16(table.offset) ?: return null
            val minorVersion = reader.u16(table.offset + 2) ?: return null
            if (majorVersion != 1 || minorVersion != 0) return null
            val parsedAxisCount = reader.u16(table.offset + 4) ?: return null
            if (parsedAxisCount != axisCount) return null
            val sharedTupleCount = reader.u16(table.offset + 6) ?: return null
            val sharedTupleOffset = reader.u32(table.offset + 8)?.toIntOrNull() ?: return null
            val parsedGlyphCount = reader.u16(table.offset + 12) ?: return null
            if (parsedGlyphCount != glyphCount) return null
            val flags = reader.u16(table.offset + 14) ?: return null
            val glyphDataOffset = reader.u32(table.offset + 16)?.toIntOrNull() ?: return null
            val sharedTupleStart = table.offset + sharedTupleOffset
            if (!reader.fits(sharedTupleStart, sharedTupleCount.toLong() * axisCount.toLong() * 2L)) return null
            val sharedTuples = List(sharedTupleCount) { tupleIndex ->
                FloatArray(axisCount) { axis ->
                    reader.f2Dot14(sharedTupleStart + tupleIndex * axisCount * 2 + axis * 2) ?: return null
                }
            }
            val longOffsets = (flags and GVAR_LONG_OFFSETS) != 0
            val offsetsStart = table.offset + 20
            val offsets = IntArray(glyphCount + 1)
            if (longOffsets) {
                if (!reader.fits(offsetsStart, (glyphCount + 1).toLong() * 4L)) return null
                for (i in 0..glyphCount) offsets[i] = reader.u32(offsetsStart + i * 4)?.toIntOrNull() ?: return null
            } else {
                if (!reader.fits(offsetsStart, (glyphCount + 1).toLong() * 2L)) return null
                for (i in 0..glyphCount) offsets[i] = (reader.u16(offsetsStart + i * 2) ?: return null) * 2
            }
            val glyphDataStart = table.offset + glyphDataOffset
            if (!reader.fits(glyphDataStart, 0)) return null
            for (i in 0 until offsets.lastIndex) if (offsets[i] > offsets[i + 1]) return null
            if (offsets.last() < 0 || !reader.fits(glyphDataStart, offsets.last())) return null
            return GvarTable(bytes, tableEnd, axisCount, sharedTuples, offsets, glyphDataStart)
        }

        private fun parseColorFont(
            bytes: ByteArray,
            colrTable: TableRecord?,
            cpalTable: TableRecord?,
        ): OpenTypeColorFont? {
            val palettes = parseCpalTable(bytes, cpalTable) ?: return null
            val colr = parseColrTable(bytes, colrTable) ?: return null
            return OpenTypeColorFont(palettes, colr.layersByGlyph, colr.v1PaintsByGlyph, colr.clipBoxes)
        }

        private fun parseSvgTable(bytes: ByteArray, table: TableRecord?): OpenTypeSvgTable? {
            table ?: return null
            if (table.length < 10) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val version = reader.u16(table.offset) ?: return null
            if (version != 0) return null
            val documentListOffset = reader.u32(table.offset + 2)?.toIntOrNull() ?: return null
            if (documentListOffset == 0) return null
            val documentListStart = table.offset + documentListOffset
            if (!reader.fits(documentListStart, 2)) return null
            val numEntries = reader.u16(documentListStart) ?: return null
            if (numEntries == 0 || numEntries > MAX_SVG_DOCUMENT_RECORDS) return null
            if (!reader.fits(documentListStart + 2, numEntries.toLong() * 12L)) return null
            val documentRecordsEnd = documentListStart + 2 + numEntries * 12
            val records = ArrayList<OpenTypeSvgDocumentRecord>(numEntries)
            var previousEndGlyphId = -1
            repeat(numEntries) { index ->
                val recordOffset = documentListStart + 2 + index * 12
                val startGlyphId = reader.u16(recordOffset) ?: return null
                val endGlyphId = reader.u16(recordOffset + 2) ?: return null
                val svgDocOffset = reader.u32(recordOffset + 4)?.toIntOrNull() ?: return null
                val svgDocLength = reader.u32(recordOffset + 8)?.toIntOrNull() ?: return null
                if (svgDocOffset == 0 || svgDocLength == 0) return null
                if (startGlyphId > endGlyphId) return null
                if (startGlyphId <= previousEndGlyphId) return null
                if ((endGlyphId - startGlyphId + 1) > MAX_SVG_GLYPHS_PER_RECORD) return null
                val documentStart = documentListStart + svgDocOffset
                if (documentStart < documentRecordsEnd || !reader.fits(documentStart, svgDocLength)) return null
                records.add(OpenTypeSvgDocumentRecord(startGlyphId, endGlyphId, documentStart, svgDocLength))
                previousEndGlyphId = endGlyphId
            }
            return OpenTypeSvgTable(bytes, records)
        }

        private fun parseBitmapFont(
            bytes: ByteArray,
            cbdtTable: TableRecord?,
            cblcTable: TableRecord?,
            sbixTable: TableRecord?,
            numGlyphs: Int,
        ): OpenTypeBitmapFont? {
            val cbdt = parseCbdtCblcTables(bytes, cbdtTable, cblcTable, numGlyphs)
            val sbix = parseSbixTable(bytes, sbixTable, numGlyphs)
            if (cbdt.isEmpty() && sbix.isEmpty()) return null
            return OpenTypeBitmapFont(cbdt + sbix)
        }

        private fun parseCbdtCblcTables(
            bytes: ByteArray,
            cbdtTable: TableRecord?,
            cblcTable: TableRecord?,
            numGlyphs: Int,
        ): Map<Int, OpenTypeBitmapGlyph> {
            cbdtTable ?: return emptyMap()
            cblcTable ?: return emptyMap()
            if (cbdtTable.length < 4 || cblcTable.length < 8) return emptyMap()
            val cbdtEnd = cbdtTable.offset + cbdtTable.length
            val cblcEnd = cblcTable.offset + cblcTable.length
            val cbdtReader = SfntReader(bytes, cbdtEnd)
            val cblcReader = SfntReader(bytes, cblcEnd)
            if (cbdtReader.u16(cbdtTable.offset) != 3 || cblcReader.u16(cblcTable.offset) != 3) return emptyMap()
            if (cbdtReader.u16(cbdtTable.offset + 2) !in 0..99 || cblcReader.u16(cblcTable.offset + 2) !in 0..99) return emptyMap()
            val numSizes = cblcReader.u32(cblcTable.offset + 4)?.toIntOrNull() ?: return emptyMap()
            if (numSizes == 0 || numSizes > MAX_BITMAP_STRIKES) return emptyMap()
            if (!cblcReader.fits(cblcTable.offset + 8, numSizes.toLong() * CBLC_BITMAP_SIZE_TABLE_SIZE)) return emptyMap()

            val out = LinkedHashMap<Int, OpenTypeBitmapGlyph>()
            repeat(numSizes) { sizeIndex ->
                val sizeOffset = cblcTable.offset + 8 + sizeIndex * CBLC_BITMAP_SIZE_TABLE_SIZE
                val subtableArrayOffset = cblcReader.u32(sizeOffset)?.toIntOrNull() ?: return@repeat
                val numberOfSubtables = cblcReader.u32(sizeOffset + 8)?.toIntOrNull() ?: return@repeat
                val startGlyph = cblcReader.u16(sizeOffset + 40) ?: return@repeat
                val endGlyph = cblcReader.u16(sizeOffset + 42) ?: return@repeat
                val ppemX = cblcReader.u8(sizeOffset + 44) ?: return@repeat
                val ppemY = cblcReader.u8(sizeOffset + 45) ?: return@repeat
                val bitDepth = cblcReader.u8(sizeOffset + 46) ?: return@repeat
                if (startGlyph > endGlyph || endGlyph >= numGlyphs) return@repeat
                if (numberOfSubtables <= 0 || numberOfSubtables > MAX_BITMAP_SUBTABLES) return@repeat
                val arrayStart = cblcTable.offset + subtableArrayOffset
                if (!cblcReader.fits(arrayStart, numberOfSubtables.toLong() * 8L)) return@repeat
                repeat(numberOfSubtables) { index ->
                    val entry = arrayStart + index * 8
                    val firstGlyph = cblcReader.u16(entry) ?: return@repeat
                    val lastGlyph = cblcReader.u16(entry + 2) ?: return@repeat
                    val subtableOffset = cblcReader.u32(entry + 4)?.toIntOrNull() ?: return@repeat
                    if (firstGlyph > lastGlyph || firstGlyph < startGlyph || lastGlyph > endGlyph) return@repeat
                    val subtableStart = arrayStart + subtableOffset
                    parseCblcIndexSubtable(
                        bytes = bytes,
                        cblcReader = cblcReader,
                        cbdtReader = cbdtReader,
                        cbdtTable = cbdtTable,
                        subtableStart = subtableStart,
                        firstGlyph = firstGlyph,
                        lastGlyph = lastGlyph,
                        ppemX = ppemX,
                        ppemY = ppemY,
                        bitDepth = bitDepth,
                        out = out,
                    )
                }
            }
            return out
        }

        private fun parseCblcIndexSubtable(
            bytes: ByteArray,
            cblcReader: SfntReader,
            cbdtReader: SfntReader,
            cbdtTable: TableRecord,
            subtableStart: Int,
            firstGlyph: Int,
            lastGlyph: Int,
            ppemX: Int,
            ppemY: Int,
            bitDepth: Int,
            out: MutableMap<Int, OpenTypeBitmapGlyph>,
        ) {
            if (!cblcReader.fits(subtableStart, 8)) return
            val indexFormat = cblcReader.u16(subtableStart) ?: return
            val imageFormat = cblcReader.u16(subtableStart + 2) ?: return
            if (imageFormat !in CBDT_PNG_IMAGE_FORMATS) return
            val imageDataOffset = cblcReader.u32(subtableStart + 4)?.toIntOrNull() ?: return
            val glyphCount = lastGlyph - firstGlyph + 1
            val offsets: IntArray = when (indexFormat) {
                1 -> {
                    if (!cblcReader.fits(subtableStart + 8, (glyphCount + 1).toLong() * 4L)) return
                    IntArray(glyphCount + 1) { i ->
                        cblcReader.u32(subtableStart + 8 + i * 4)?.toIntOrNull() ?: return
                    }
                }
                3 -> {
                    if (!cblcReader.fits(subtableStart + 8, (glyphCount + 1).toLong() * 2L)) return
                    IntArray(glyphCount + 1) { i ->
                        cblcReader.u16(subtableStart + 8 + i * 2) ?: return
                    }
                }
                else -> return
            }
            for (i in 0 until offsets.lastIndex) if (offsets[i] > offsets[i + 1]) return
            for (i in 0 until glyphCount) {
                val payloadLength = offsets[i + 1] - offsets[i]
                if (payloadLength <= 0 || payloadLength > MAX_BITMAP_PAYLOAD_BYTES) continue
                val payloadOffset = cbdtTable.offset + imageDataOffset + offsets[i]
                if (!cbdtReader.fits(payloadOffset, payloadLength)) return
                val imageHeaderLength = when (imageFormat) {
                    17 -> 5
                    18 -> 8
                    19 -> 0
                    else -> return
                }
                val pngOffset = payloadOffset + imageHeaderLength
                val pngLength = payloadLength - imageHeaderLength
                if (pngLength <= 0 || !cbdtReader.fits(pngOffset, pngLength) || !isPngPayload(bytes, pngOffset, pngLength)) continue
                val glyphId = firstGlyph + i
                out.putIfAbsent(
                    glyphId,
                    OpenTypeBitmapGlyph(
                        glyphId = glyphId,
                        source = OpenTypeBitmapGlyphSource.CBDT_CBLC,
                        ppemX = ppemX,
                        ppemY = ppemY,
                        bitDepth = bitDepth,
                        originOffsetX = 0,
                        originOffsetY = 0,
                        imageFormat = "png ",
                        bytes = bytes.copyOfRange(pngOffset, pngOffset + pngLength),
                    ),
                )
            }
        }

        private fun parseSbixTable(
            bytes: ByteArray,
            table: TableRecord?,
            numGlyphs: Int,
        ): Map<Int, OpenTypeBitmapGlyph> {
            table ?: return emptyMap()
            if (table.length < 8) return emptyMap()
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val version = reader.u16(table.offset) ?: return emptyMap()
            if (version != 1) return emptyMap()
            val numStrikes = reader.u32(table.offset + 4)?.toIntOrNull() ?: return emptyMap()
            if (numStrikes == 0 || numStrikes > MAX_BITMAP_STRIKES) return emptyMap()
            if (!reader.fits(table.offset + 8, numStrikes.toLong() * 4L)) return emptyMap()
            val out = LinkedHashMap<Int, OpenTypeBitmapGlyph>()
            repeat(numStrikes) { strikeIndex ->
                val strikeOffset = reader.u32(table.offset + 8 + strikeIndex * 4)?.toIntOrNull() ?: return@repeat
                val strikeStart = table.offset + strikeOffset
                if (!reader.fits(strikeStart, 4L + (numGlyphs + 1).toLong() * 4L)) return@repeat
                val ppem = reader.u16(strikeStart) ?: return@repeat
                reader.u16(strikeStart + 2) ?: return@repeat
                val offsetsStart = strikeStart + 4
                val offsets = IntArray(numGlyphs + 1) { i ->
                    reader.u32(offsetsStart + i * 4)?.toIntOrNull() ?: return@repeat
                }
                for (i in 0 until offsets.lastIndex) if (offsets[i] > offsets[i + 1]) return@repeat
                for (glyphId in 0 until numGlyphs) {
                    val glyphStart = strikeStart + offsets[glyphId]
                    val glyphEnd = strikeStart + offsets[glyphId + 1]
                    val payloadLength = glyphEnd - glyphStart
                    if (payloadLength <= 0) continue
                    if (payloadLength < 8 || payloadLength > MAX_BITMAP_PAYLOAD_BYTES) continue
                    if (!reader.fits(glyphStart, payloadLength)) return@repeat
                    val graphicType = reader.tag(glyphStart + 4) ?: continue
                    if (graphicType != "png ") continue
                    val pngOffset = glyphStart + 8
                    val pngLength = glyphEnd - pngOffset
                    if (!isPngPayload(bytes, pngOffset, pngLength)) continue
                    out.putIfAbsent(
                        glyphId,
                        OpenTypeBitmapGlyph(
                            glyphId = glyphId,
                            source = OpenTypeBitmapGlyphSource.SBIX,
                            ppemX = ppem,
                            ppemY = ppem,
                            bitDepth = 32,
                            originOffsetX = reader.i16(glyphStart)?.toInt() ?: continue,
                            originOffsetY = reader.i16(glyphStart + 2)?.toInt() ?: continue,
                            imageFormat = graphicType,
                            bytes = bytes.copyOfRange(pngOffset, glyphEnd),
                        ),
                    )
                }
            }
            return out
        }

        private fun isPngPayload(bytes: ByteArray, offset: Int, length: Int): Boolean {
            if (length < PNG_SIGNATURE.size || !fits(offset, PNG_SIGNATURE.size, bytes.size)) return false
            for (i in PNG_SIGNATURE.indices) {
                if (bytes[offset + i] != PNG_SIGNATURE[i]) return false
            }
            return true
        }

        private fun parseCpalTable(bytes: ByteArray, table: TableRecord?): List<List<Int>>? {
            table ?: return null
            if (table.length < 12) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val version = reader.u16(table.offset) ?: return null
            if (version != 0) return null
            val numPaletteEntries = reader.u16(table.offset + 2) ?: return null
            val numPalettes = reader.u16(table.offset + 4) ?: return null
            val numColorRecords = reader.u16(table.offset + 6) ?: return null
            val colorRecordsArrayOffset = reader.u32(table.offset + 8)?.toIntOrNull() ?: return null
            if (numPaletteEntries > MAX_COLOR_PALETTE_ENTRIES) return null
            if (numPalettes > MAX_COLOR_PALETTES) return null
            if (numColorRecords > MAX_COLOR_RECORDS) return null
            if (numPalettes.toLong() * numPaletteEntries.toLong() > MAX_EXPANDED_COLOR_RECORDS) return null
            if (!reader.fits(table.offset + 12, numPalettes.toLong() * 2L)) return null
            val colorRecordsStart = table.offset + colorRecordsArrayOffset
            if (!reader.fits(colorRecordsStart, numColorRecords.toLong() * 4L)) return null
            return List(numPalettes) { paletteIndex ->
                val firstColorRecordIndex = reader.u16(table.offset + 12 + paletteIndex * 2) ?: return null
                if (firstColorRecordIndex + numPaletteEntries > numColorRecords) return null
                List(numPaletteEntries) { entryIndex ->
                    val colorOffset = colorRecordsStart + (firstColorRecordIndex + entryIndex) * 4
                    val blue = reader.u8(colorOffset) ?: return null
                    val green = reader.u8(colorOffset + 1) ?: return null
                    val red = reader.u8(colorOffset + 2) ?: return null
                    val alpha = reader.u8(colorOffset + 3) ?: return null
                    ((alpha and 0xFF) shl 24) or
                        ((red and 0xFF) shl 16) or
                        ((green and 0xFF) shl 8) or
                        (blue and 0xFF)
                }
            }
        }

        private fun parseColrTable(bytes: ByteArray, table: TableRecord?): OpenTypeColrTables? {
            table ?: return null
            if (table.length < 14) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val version = reader.u16(table.offset) ?: return null
            return when (version) {
                0 -> OpenTypeColrTables(parseColrV0Table(reader, table, expectedVersion = 0) ?: return null, emptyMap(), emptyList())
                1 -> {
                    if (table.length < 34) return null
                    val layersByGlyph = parseColrV0Table(reader, table, expectedVersion = 1) ?: return null
                    val paintsByGlyph = parseColrV1Paints(reader, table) ?: return null
                    val clipBoxes = parseColrV1ClipBoxes(reader, table) ?: return null
                    OpenTypeColrTables(layersByGlyph, paintsByGlyph, clipBoxes)
                }
                else -> null
            }
        }

        private fun parseColrV0Table(
            reader: SfntReader,
            table: TableRecord,
            expectedVersion: Int,
        ): Map<Int, List<OpenTypeColorLayer>>? {
            val version = reader.u16(table.offset) ?: return null
            if (version != expectedVersion) return null
            val numBaseGlyphRecords = reader.u16(table.offset + 2) ?: return null
            val baseGlyphRecordsOffset = reader.u32(table.offset + 4)?.toIntOrNull() ?: return null
            val layerRecordsOffset = reader.u32(table.offset + 8)?.toIntOrNull() ?: return null
            val numLayerRecords = reader.u16(table.offset + 12) ?: return null
            if (numBaseGlyphRecords > MAX_COLOR_BASE_GLYPHS) return null
            if (numLayerRecords > MAX_COLOR_LAYERS) return null
            val baseGlyphRecordsStart = table.offset + baseGlyphRecordsOffset
            val layerRecordsStart = table.offset + layerRecordsOffset
            if (!reader.fits(baseGlyphRecordsStart, numBaseGlyphRecords.toLong() * 6L)) return null
            if (!reader.fits(layerRecordsStart, numLayerRecords.toLong() * 4L)) return null
            val layersByGlyph = HashMap<Int, List<OpenTypeColorLayer>>()
            var expandedLayerCount = 0L
            repeat(numBaseGlyphRecords) { baseIndex ->
                val baseOffset = baseGlyphRecordsStart + baseIndex * 6
                val glyphId = reader.u16(baseOffset) ?: return null
                val firstLayerIndex = reader.u16(baseOffset + 2) ?: return null
                val numLayers = reader.u16(baseOffset + 4) ?: return null
                if (numLayers > MAX_LAYERS_PER_COLOR_GLYPH) return null
                expandedLayerCount += numLayers.toLong()
                if (expandedLayerCount > MAX_EXPANDED_COLOR_LAYERS) return null
                if (firstLayerIndex + numLayers > numLayerRecords) return null
                val layers = List(numLayers) { layerIndex ->
                    val layerOffset = layerRecordsStart + (firstLayerIndex + layerIndex) * 4
                    OpenTypeColorLayer(
                        glyphId = reader.u16(layerOffset) ?: return null,
                        paletteIndex = reader.u16(layerOffset + 2) ?: return null,
                    )
                }
                layersByGlyph[glyphId] = layers
            }
            return layersByGlyph
        }

        private fun parseColrV1Paints(reader: SfntReader, table: TableRecord): Map<Int, OpenTypeColorPaint>? {
            val baseGlyphListOffset = reader.u32(table.offset + 14)?.toIntOrNull() ?: return null
            if (baseGlyphListOffset == 0) return emptyMap()
            val layerPaintOffsets = parseColrV1LayerPaintOffsets(reader, table) ?: return null
            val baseGlyphListStart = table.offset + baseGlyphListOffset
            if (!reader.fits(baseGlyphListStart, 4)) return null
            val baseGlyphPaintCount = reader.u32(baseGlyphListStart)?.toIntOrNull() ?: return null
            if (baseGlyphPaintCount > MAX_COLOR_BASE_GLYPHS) return null
            if (!reader.fits(baseGlyphListStart + 4, baseGlyphPaintCount.toLong() * 6L)) return null
            val paintsByGlyph = HashMap<Int, OpenTypeColorPaint>()
            repeat(baseGlyphPaintCount) { recordIndex ->
                val recordOffset = baseGlyphListStart + 4 + recordIndex * 6
                val glyphId = reader.u16(recordOffset) ?: return null
                val paintOffset = reader.u32(recordOffset + 2)?.toIntOrNull() ?: return null
                val paint = parseColorPaint(
                    reader = reader,
                    colrStart = table.offset,
                    colrEnd = table.offset + table.length,
                    layerPaintOffsets = layerPaintOffsets,
                    paintOffset = baseGlyphListStart + paintOffset,
                    depth = 0,
                ) ?: return null
                paintsByGlyph[glyphId] = paint
            }
            return paintsByGlyph
        }

        private fun parseColrV1LayerPaintOffsets(reader: SfntReader, table: TableRecord): List<Int>? {
            val layerListOffset = reader.u32(table.offset + 18)?.toIntOrNull() ?: return null
            if (layerListOffset == 0) return emptyList()
            val layerListStart = table.offset + layerListOffset
            if (!reader.fits(layerListStart, 4)) return null
            val layerCount = reader.u32(layerListStart)?.toIntOrNull() ?: return null
            if (layerCount > MAX_COLOR_LAYERS) return null
            if (!reader.fits(layerListStart + 4, layerCount.toLong() * 4L)) return null
            return List(layerCount) { index ->
                val offset = reader.u32(layerListStart + 4 + index * 4)?.toIntOrNull() ?: return null
                val paintOffset = layerListStart + offset
                if (paintOffset < table.offset || paintOffset >= table.offset + table.length) return null
                paintOffset
            }
        }

        private fun parseColrV1ClipBoxes(reader: SfntReader, table: TableRecord): List<OpenTypeClipRange>? {
            val clipListOffset = reader.u32(table.offset + 22)?.toIntOrNull() ?: return null
            if (clipListOffset == 0) return emptyList()
            val clipListStart = table.offset + clipListOffset
            if (!reader.fits(clipListStart, 5)) return null
            if (reader.u8(clipListStart) != 1) return null
            val clipCount = reader.u32(clipListStart + 1)?.toIntOrNull() ?: return null
            if (clipCount > MAX_COLOR_BASE_GLYPHS) return null
            if (!reader.fits(clipListStart + 5, clipCount.toLong() * 7L)) return null
            val ranges = ArrayList<OpenTypeClipRange>(clipCount)
            var previousEnd = -1
            repeat(clipCount) { index ->
                val recordOffset = clipListStart + 5 + index * 7
                val startGlyphId = reader.u16(recordOffset) ?: return null
                val endGlyphId = reader.u16(recordOffset + 2) ?: return null
                if (startGlyphId > endGlyphId || startGlyphId <= previousEnd) return null
                previousEnd = endGlyphId
                val clipBoxOffset = reader.u24(recordOffset + 4) ?: return null
                if (clipBoxOffset == 0) return null
                val clipBoxStart = clipListStart + clipBoxOffset
                if (clipBoxStart < table.offset || clipBoxStart >= table.offset + table.length) return null
                val clipBox = parseColrV1ClipBox(reader, clipBoxStart) ?: return null
                ranges += OpenTypeClipRange(startGlyphId, endGlyphId, clipBox)
            }
            return ranges
        }

        private fun parseColrV1ClipBox(reader: SfntReader, clipBoxStart: Int): OpenTypeClipBox? {
            val format = reader.u8(clipBoxStart) ?: return null
            val minSize = when (format) {
                1 -> 9
                2 -> 13
                else -> return null
            }
            if (!reader.fits(clipBoxStart, minSize)) return null
            val xMin = reader.i16(clipBoxStart + 1)?.toInt() ?: return null
            val yMin = reader.i16(clipBoxStart + 3)?.toInt() ?: return null
            val xMax = reader.i16(clipBoxStart + 5)?.toInt() ?: return null
            val yMax = reader.i16(clipBoxStart + 7)?.toInt() ?: return null
            if (xMin >= xMax || yMin >= yMax) return null
            return OpenTypeClipBox(xMin, yMin, xMax, yMax)
        }

        private fun parseColorPaint(
            reader: SfntReader,
            colrStart: Int,
            colrEnd: Int,
            layerPaintOffsets: List<Int>,
            paintOffset: Int,
            depth: Int,
        ): OpenTypeColorPaint? {
            if (depth > MAX_COLOR_PAINT_DEPTH) return null
            if (paintOffset < colrStart || paintOffset >= colrEnd) return null
            val format = reader.u8(paintOffset) ?: return null
            return when (format) {
                1 -> {
                    if (!reader.fits(paintOffset, 6)) return null
                    val layerCount = reader.u8(paintOffset + 1) ?: return null
                    val firstLayerIndex = reader.u32(paintOffset + 2)?.toIntOrNull() ?: return null
                    if (layerCount == 0 || layerCount > MAX_LAYERS_PER_COLOR_GLYPH) return null
                    if (firstLayerIndex < 0 || firstLayerIndex + layerCount > layerPaintOffsets.size) return null
                    val layers = List(layerCount) { index ->
                        parseColorPaint(
                            reader = reader,
                            colrStart = colrStart,
                            colrEnd = colrEnd,
                            layerPaintOffsets = layerPaintOffsets,
                            paintOffset = layerPaintOffsets[firstLayerIndex + index],
                            depth = depth + 1,
                        ) ?: return null
                    }
                    OpenTypeColorPaint.Layers(layers)
                }
                2 -> {
                    if (!reader.fits(paintOffset, 5)) return null
                    OpenTypeColorPaint.Solid(
                        paletteIndex = reader.u16(paintOffset + 1) ?: return null,
                        alpha = reader.f2Dot14(paintOffset + 3)?.coerceIn(0f, 1f) ?: return null,
                    )
                }
                3 -> {
                    if (!reader.fits(paintOffset, 9)) return null
                    OpenTypeColorPaint.Solid(
                        paletteIndex = reader.u16(paintOffset + 1) ?: return null,
                        alpha = reader.f2Dot14(paintOffset + 3)?.coerceIn(0f, 1f) ?: return null,
                    )
                }
                4 -> {
                    if (!reader.fits(paintOffset, 16)) return null
                    val colorLineOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.LinearGradient(
                        colorLine = parseColorLine(reader, colrStart, colrEnd, colorLineOffset, variableStops = false) ?: return null,
                        x0 = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        y0 = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                        x1 = reader.i16(paintOffset + 8)?.toInt() ?: return null,
                        y1 = reader.i16(paintOffset + 10)?.toInt() ?: return null,
                        x2 = reader.i16(paintOffset + 12)?.toInt() ?: return null,
                        y2 = reader.i16(paintOffset + 14)?.toInt() ?: return null,
                    )
                }
                5 -> {
                    if (!reader.fits(paintOffset, 20)) return null
                    val colorLineOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.LinearGradient(
                        colorLine = parseColorLine(reader, colrStart, colrEnd, colorLineOffset, variableStops = true) ?: return null,
                        x0 = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        y0 = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                        x1 = reader.i16(paintOffset + 8)?.toInt() ?: return null,
                        y1 = reader.i16(paintOffset + 10)?.toInt() ?: return null,
                        x2 = reader.i16(paintOffset + 12)?.toInt() ?: return null,
                        y2 = reader.i16(paintOffset + 14)?.toInt() ?: return null,
                    )
                }
                6 -> {
                    if (!reader.fits(paintOffset, 16)) return null
                    val colorLineOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.RadialGradient(
                        colorLine = parseColorLine(reader, colrStart, colrEnd, colorLineOffset, variableStops = false) ?: return null,
                        x0 = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        y0 = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                        radius0 = reader.u16(paintOffset + 8) ?: return null,
                        x1 = reader.i16(paintOffset + 10)?.toInt() ?: return null,
                        y1 = reader.i16(paintOffset + 12)?.toInt() ?: return null,
                        radius1 = reader.u16(paintOffset + 14) ?: return null,
                    )
                }
                7 -> {
                    if (!reader.fits(paintOffset, 20)) return null
                    val colorLineOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.RadialGradient(
                        colorLine = parseColorLine(reader, colrStart, colrEnd, colorLineOffset, variableStops = true) ?: return null,
                        x0 = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        y0 = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                        radius0 = reader.u16(paintOffset + 8) ?: return null,
                        x1 = reader.i16(paintOffset + 10)?.toInt() ?: return null,
                        y1 = reader.i16(paintOffset + 12)?.toInt() ?: return null,
                        radius1 = reader.u16(paintOffset + 14) ?: return null,
                    )
                }
                8 -> {
                    if (!reader.fits(paintOffset, 12)) return null
                    val colorLineOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.SweepGradient(
                        colorLine = parseColorLine(reader, colrStart, colrEnd, colorLineOffset, variableStops = false) ?: return null,
                        centerX = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        centerY = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                        startAngle = reader.f2Dot14(paintOffset + 8) ?: return null,
                        endAngle = reader.f2Dot14(paintOffset + 10) ?: return null,
                    )
                }
                9 -> {
                    if (!reader.fits(paintOffset, 16)) return null
                    val colorLineOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.SweepGradient(
                        colorLine = parseColorLine(reader, colrStart, colrEnd, colorLineOffset, variableStops = true) ?: return null,
                        centerX = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        centerY = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                        startAngle = reader.f2Dot14(paintOffset + 8) ?: return null,
                        endAngle = reader.f2Dot14(paintOffset + 10) ?: return null,
                    )
                }
                10 -> {
                    if (!reader.fits(paintOffset, 6)) return null
                    val childOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.Glyph(
                        glyphId = reader.u16(paintOffset + 4) ?: return null,
                        paint = parseColorPaint(reader, colrStart, colrEnd, layerPaintOffsets, childOffset, depth + 1) ?: return null,
                    )
                }
                11 -> {
                    if (!reader.fits(paintOffset, 3)) return null
                    OpenTypeColorPaint.ColrGlyph(
                        glyphId = reader.u16(paintOffset + 1) ?: return null,
                    )
                }
                12 -> {
                    if (!reader.fits(paintOffset, 7)) return null
                    val childOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    val transformOffset = childPaintOffset(reader, paintOffset, 4) ?: return null
                    parseColorTransformPaint(reader, colrStart, colrEnd, layerPaintOffsets, childOffset, transformOffset, variableTransform = false, depth = depth)
                }
                13 -> {
                    if (!reader.fits(paintOffset, 7)) return null
                    val childOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    val transformOffset = childPaintOffset(reader, paintOffset, 4) ?: return null
                    parseColorTransformPaint(reader, colrStart, colrEnd, layerPaintOffsets, childOffset, transformOffset, variableTransform = true, depth = depth)
                }
                14 -> {
                    if (!reader.fits(paintOffset, 8)) return null
                    val childOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.Translate(
                        paint = parseColorPaint(reader, colrStart, colrEnd, layerPaintOffsets, childOffset, depth + 1) ?: return null,
                        dx = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        dy = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                    )
                }
                15 -> {
                    if (!reader.fits(paintOffset, 12)) return null
                    val childOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    OpenTypeColorPaint.Translate(
                        paint = parseColorPaint(reader, colrStart, colrEnd, layerPaintOffsets, childOffset, depth + 1) ?: return null,
                        dx = reader.i16(paintOffset + 4)?.toInt() ?: return null,
                        dy = reader.i16(paintOffset + 6)?.toInt() ?: return null,
                    )
                }
                32 -> {
                    if (!reader.fits(paintOffset, 8)) return null
                    val sourceOffset = childPaintOffset(reader, paintOffset, 1) ?: return null
                    val mode = parseCompositeMode(reader.u8(paintOffset + 4) ?: return null)
                    val backdropOffset = childPaintOffset(reader, paintOffset, 5) ?: return null
                    OpenTypeColorPaint.Composite(
                        source = parseColorPaint(reader, colrStart, colrEnd, layerPaintOffsets, sourceOffset, depth + 1) ?: return null,
                        mode = mode,
                        backdrop = parseColorPaint(reader, colrStart, colrEnd, layerPaintOffsets, backdropOffset, depth + 1) ?: return null,
                    )
                }
                else -> null
            }
        }

        private fun parseCompositeMode(mode: Int): SkBlendMode = when (mode) {
            0 -> SkBlendMode.kClear
            1 -> SkBlendMode.kSrc
            2 -> SkBlendMode.kDst
            3 -> SkBlendMode.kSrcOver
            4 -> SkBlendMode.kDstOver
            5 -> SkBlendMode.kSrcIn
            6 -> SkBlendMode.kDstIn
            7 -> SkBlendMode.kSrcOut
            8 -> SkBlendMode.kDstOut
            9 -> SkBlendMode.kSrcATop
            10 -> SkBlendMode.kDstATop
            11 -> SkBlendMode.kXor
            12 -> SkBlendMode.kPlus
            13 -> SkBlendMode.kScreen
            14 -> SkBlendMode.kOverlay
            15 -> SkBlendMode.kDarken
            16 -> SkBlendMode.kLighten
            17 -> SkBlendMode.kColorDodge
            18 -> SkBlendMode.kColorBurn
            19 -> SkBlendMode.kHardLight
            20 -> SkBlendMode.kSoftLight
            21 -> SkBlendMode.kDifference
            22 -> SkBlendMode.kExclusion
            23 -> SkBlendMode.kMultiply
            24 -> SkBlendMode.kHue
            25 -> SkBlendMode.kSaturation
            26 -> SkBlendMode.kColor
            27 -> SkBlendMode.kLuminosity
            else -> SkBlendMode.kClear
        }

        private fun parseColorTransformPaint(
            reader: SfntReader,
            colrStart: Int,
            colrEnd: Int,
            layerPaintOffsets: List<Int>,
            childOffset: Int,
            transformOffset: Int,
            variableTransform: Boolean,
            depth: Int,
        ): OpenTypeColorPaint? {
            val transformSize = if (variableTransform) 28 else 24
            if (transformOffset < colrStart || transformOffset >= colrEnd) return null
            if (!reader.fits(transformOffset, transformSize)) return null
            return OpenTypeColorPaint.Transform(
                paint = parseColorPaint(reader, colrStart, colrEnd, layerPaintOffsets, childOffset, depth + 1) ?: return null,
                xx = reader.fixed16Dot16(transformOffset) ?: return null,
                yx = reader.fixed16Dot16(transformOffset + 4) ?: return null,
                xy = reader.fixed16Dot16(transformOffset + 8) ?: return null,
                yy = reader.fixed16Dot16(transformOffset + 12) ?: return null,
                dx = reader.fixed16Dot16(transformOffset + 16) ?: return null,
                dy = reader.fixed16Dot16(transformOffset + 20) ?: return null,
            )
        }

        private fun parseColorLine(
            reader: SfntReader,
            colrStart: Int,
            colrEnd: Int,
            colorLineOffset: Int,
            variableStops: Boolean,
        ): OpenTypeColorLine? {
            if (colorLineOffset < colrStart || colorLineOffset >= colrEnd) return null
            if (!reader.fits(colorLineOffset, 3)) return null
            val extend = when (reader.u8(colorLineOffset) ?: return null) {
                0 -> OpenTypeColorExtend.PAD
                1 -> OpenTypeColorExtend.REPEAT
                2 -> OpenTypeColorExtend.REFLECT
                else -> return null
            }
            val stopCount = reader.u16(colorLineOffset + 1) ?: return null
            if (stopCount == 0 || stopCount > MAX_COLOR_STOPS) return null
            val stopStride = if (variableStops) 10 else 6
            val stopBytes = stopCount.toLong() * stopStride.toLong()
            if (colorLineOffset.toLong() + 3L + stopBytes > colrEnd.toLong()) return null
            if (!reader.fits(colorLineOffset + 3, stopBytes)) return null
            val stops = ArrayList<OpenTypeColorStop>(stopCount)
            repeat(stopCount) { index ->
                val stopOffset = colorLineOffset + 3 + index * stopStride
                val offset = reader.f2Dot14(stopOffset) ?: return null
                stops += OpenTypeColorStop(
                    offset = offset,
                    paletteIndex = reader.u16(stopOffset + 2) ?: return null,
                    alpha = reader.f2Dot14(stopOffset + 4)?.coerceIn(0f, 1f) ?: return null,
                )
            }
            return OpenTypeColorLine(extend, stops)
        }

        private fun childPaintOffset(reader: SfntReader, parentOffset: Int, fieldOffset: Int): Int? {
            val offset = reader.u24(parentOffset + fieldOffset) ?: return null
            if (offset == 0) return null
            return parentOffset + offset
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

        private fun parseGposPairTable(bytes: ByteArray, table: TableRecord?, numGlyphs: Int): GposPairTable? {
            table ?: return null
            if (table.length < 10) return null
            val tableEnd = table.offset + table.length
            val reader = SfntReader(bytes, tableEnd)
            val majorVersion = reader.u16(table.offset) ?: return null
            val minorVersion = reader.u16(table.offset + 2) ?: return null
            if (majorVersion != 1 || minorVersion !in 0..1) return null
            val scriptListOffset = reader.u16(table.offset + 4) ?: return null
            val featureListOffset = reader.u16(table.offset + 6) ?: return null
            val lookupListOffset = reader.u16(table.offset + 8) ?: return null
            val kernLookupIndices = parseGposKernLookupIndices(
                bytes = bytes,
                tableEnd = tableEnd,
                scriptListStart = table.offset + scriptListOffset,
                featureListStart = table.offset + featureListOffset,
            ) ?: return null
            if (kernLookupIndices.isEmpty()) return null
            val lookupListStart = table.offset + lookupListOffset
            if (!reader.fits(lookupListStart, 2)) return null
            val lookupCount = reader.u16(lookupListStart) ?: return null
            if (!reader.fits(lookupListStart + 2, lookupCount.toLong() * 2L)) return null

            val pairs = HashMap<Int, Int>()
            for (lookupIndex in kernLookupIndices) {
                if (lookupIndex !in 0 until lookupCount) return null
                val lookupOffset = reader.u16(lookupListStart + 2 + lookupIndex * 2) ?: return null
                parseGposPairLookup(bytes, lookupListStart + lookupOffset, tableEnd, numGlyphs, pairs)
                    ?: return null
            }
            return pairs.takeIf { it.isNotEmpty() }?.let { GposPairTable(it) }
        }

        private fun parseGposKernLookupIndices(
            bytes: ByteArray,
            tableEnd: Int,
            scriptListStart: Int,
            featureListStart: Int,
        ): Set<Int>? {
            val reader = SfntReader(bytes, tableEnd)
            val activeFeatureIndices = parseGposActiveFeatureIndices(bytes, scriptListStart, tableEnd)
                ?: return null
            if (activeFeatureIndices.isEmpty()) return emptySet()
            if (!reader.fits(featureListStart, 2)) return null
            val featureCount = reader.u16(featureListStart) ?: return null
            if (!reader.fits(featureListStart + 2, featureCount.toLong() * 6L)) return null
            val lookups = LinkedHashSet<Int>()
            repeat(featureCount) { featureIndex ->
                val record = featureListStart + 2 + featureIndex * 6
                val tag = reader.tag(record) ?: return null
                val featureOffset = reader.u16(record + 4) ?: return null
                if (tag != "kern" || featureIndex !in activeFeatureIndices) return@repeat
                val featureStart = featureListStart + featureOffset
                if (!reader.fits(featureStart, 4)) return null
                val lookupIndexCount = reader.u16(featureStart + 2) ?: return null
                if (!reader.fits(featureStart + 4, lookupIndexCount.toLong() * 2L)) return null
                repeat(lookupIndexCount) {
                    lookups.add(reader.u16(featureStart + 4 + it * 2) ?: return null)
                }
            }
            return lookups
        }

        private fun parseGposActiveFeatureIndices(
            bytes: ByteArray,
            scriptListStart: Int,
            tableEnd: Int,
        ): Set<Int>? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(scriptListStart, 2)) return null
            val scriptCount = reader.u16(scriptListStart) ?: return null
            if (!reader.fits(scriptListStart + 2, scriptCount.toLong() * 6L)) return null
            val features = LinkedHashSet<Int>()
            repeat(scriptCount) { scriptIndex ->
                val record = scriptListStart + 2 + scriptIndex * 6
                val tag = reader.tag(record) ?: return null
                val scriptOffset = reader.u16(record + 4) ?: return null
                if (tag != "DFLT" && tag != "latn") return@repeat
                collectGposScriptFeatureIndices(bytes, scriptListStart + scriptOffset, tableEnd, features)
                    ?: return null
            }
            return features
        }

        private fun collectGposScriptFeatureIndices(
            bytes: ByteArray,
            scriptStart: Int,
            tableEnd: Int,
            out: MutableSet<Int>,
        ): Unit? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(scriptStart, 4)) return null
            val defaultLangSysOffset = reader.u16(scriptStart) ?: return null
            val langSysCount = reader.u16(scriptStart + 2) ?: return null
            if (defaultLangSysOffset != 0) {
                collectGposLangSysFeatureIndices(bytes, scriptStart + defaultLangSysOffset, tableEnd, out)
                    ?: return null
            }
            if (!reader.fits(scriptStart + 4, langSysCount.toLong() * 6L)) return null
            repeat(langSysCount) {
                val record = scriptStart + 4 + it * 6
                val langSysOffset = reader.u16(record + 4) ?: return null
                collectGposLangSysFeatureIndices(bytes, scriptStart + langSysOffset, tableEnd, out)
                    ?: return null
            }
            return Unit
        }

        private fun collectGposLangSysFeatureIndices(
            bytes: ByteArray,
            langSysStart: Int,
            tableEnd: Int,
            out: MutableSet<Int>,
        ): Unit? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(langSysStart, 6)) return null
            val requiredFeatureIndex = reader.u16(langSysStart + 2) ?: return null
            if (requiredFeatureIndex != 0xFFFF) out.add(requiredFeatureIndex)
            val featureIndexCount = reader.u16(langSysStart + 4) ?: return null
            if (!reader.fits(langSysStart + 6, featureIndexCount.toLong() * 2L)) return null
            repeat(featureIndexCount) {
                out.add(reader.u16(langSysStart + 6 + it * 2) ?: return null)
            }
            return Unit
        }

        private fun parseGposPairLookup(
            bytes: ByteArray,
            lookupStart: Int,
            tableEnd: Int,
            numGlyphs: Int,
            pairs: MutableMap<Int, Int>,
        ): Unit? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(lookupStart, 6)) return null
            val lookupType = reader.u16(lookupStart) ?: return null
            val subtableCount = reader.u16(lookupStart + 4) ?: return null
            if (!reader.fits(lookupStart + 6, subtableCount.toLong() * 2L)) return null
            if (lookupType != GPOS_PAIR_ADJUSTMENT_LOOKUP) return Unit
            repeat(subtableCount) { subtableIndex ->
                val subtableOffset = reader.u16(lookupStart + 6 + subtableIndex * 2) ?: return null
                parseGposPairSubtable(bytes, lookupStart + subtableOffset, tableEnd, numGlyphs, pairs)
                    ?: return null
            }
            return Unit
        }

        private fun parseGposPairSubtable(
            bytes: ByteArray,
            subtableStart: Int,
            tableEnd: Int,
            numGlyphs: Int,
            pairs: MutableMap<Int, Int>,
        ): Unit? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(subtableStart, 10)) return null
            val posFormat = reader.u16(subtableStart) ?: return null
            val coverageOffset = reader.u16(subtableStart + 2) ?: return null
            val valueFormat1 = reader.u16(subtableStart + 4) ?: return null
            val valueFormat2 = reader.u16(subtableStart + 6) ?: return null
            val coverage = parseCoverageTable(bytes, subtableStart + coverageOffset, tableEnd)
                ?: return null
            return when (posFormat) {
                1 -> parseGposPairFormat1(bytes, subtableStart, tableEnd, coverage, valueFormat1, valueFormat2, pairs)
                2 -> parseGposPairFormat2(bytes, subtableStart, tableEnd, numGlyphs, coverage, valueFormat1, valueFormat2, pairs)
                else -> Unit
            }
        }

        private fun parseGposPairFormat1(
            bytes: ByteArray,
            subtableStart: Int,
            tableEnd: Int,
            coverage: List<Int>,
            valueFormat1: Int,
            valueFormat2: Int,
            pairs: MutableMap<Int, Int>,
        ): Unit? {
            val reader = SfntReader(bytes, tableEnd)
            val pairSetCount = reader.u16(subtableStart + 8) ?: return null
            if (pairSetCount != coverage.size) return null
            if (!reader.fits(subtableStart + 10, pairSetCount.toLong() * 2L)) return null
            val value1Size = valueRecordSize(valueFormat1)
            val value2Size = valueRecordSize(valueFormat2)
            repeat(pairSetCount) { pairSetIndex ->
                val pairSetOffset = reader.u16(subtableStart + 10 + pairSetIndex * 2) ?: return null
                val pairSetStart = subtableStart + pairSetOffset
                if (!reader.fits(pairSetStart, 2)) return null
                val pairValueCount = reader.u16(pairSetStart) ?: return null
                var p = pairSetStart + 2
                val recordSize = 2 + value1Size + value2Size
                if (!reader.fits(p, pairValueCount.toLong() * recordSize.toLong())) return null
                repeat(pairValueCount) {
                    val rightGlyph = reader.u16(p) ?: return null
                    val xAdvance = readGposXAdvance(reader, p + 2, valueFormat1) ?: return null
                    if (xAdvance != 0) pairs[kernPairKey(coverage[pairSetIndex], rightGlyph)] = xAdvance
                    p += recordSize
                }
            }
            return Unit
        }

        private fun parseGposPairFormat2(
            bytes: ByteArray,
            subtableStart: Int,
            tableEnd: Int,
            numGlyphs: Int,
            coverage: List<Int>,
            valueFormat1: Int,
            valueFormat2: Int,
            pairs: MutableMap<Int, Int>,
        ): Unit? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(subtableStart, 16)) return null
            val classDef1Offset = reader.u16(subtableStart + 8) ?: return null
            val classDef2Offset = reader.u16(subtableStart + 10) ?: return null
            val class1Count = reader.u16(subtableStart + 12) ?: return null
            val class2Count = reader.u16(subtableStart + 14) ?: return null
            val classDef1 = parseClassDefTable(bytes, subtableStart + classDef1Offset, tableEnd)
                ?: return null
            val classDef2 = parseClassDefTable(bytes, subtableStart + classDef2Offset, tableEnd)
                ?: return null
            val value1Size = valueRecordSize(valueFormat1)
            val value2Size = valueRecordSize(valueFormat2)
            val recordSize = value1Size + value2Size
            var p = subtableStart + 16
            if (!reader.fits(p, class1Count.toLong() * class2Count.toLong() * recordSize.toLong())) return null
            for (class1 in 0 until class1Count) {
                for (class2 in 0 until class2Count) {
                    val xAdvance = readGposXAdvance(reader, p, valueFormat1) ?: return null
                    if (xAdvance != 0) {
                        val leftGlyphs = coverage.filter { classDef1.classOf(it) == class1 }
                        val rightGlyphs = classDef2.glyphsForClass(class2, numGlyphs)
                        for (leftGlyph in leftGlyphs) {
                            for (rightGlyph in rightGlyphs) {
                                pairs[kernPairKey(leftGlyph, rightGlyph)] = xAdvance
                            }
                        }
                    }
                    p += recordSize
                }
            }
            return Unit
        }

        private fun parseCoverageTable(bytes: ByteArray, coverageStart: Int, tableEnd: Int): List<Int>? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(coverageStart, 4)) return null
            return when (reader.u16(coverageStart) ?: return null) {
                1 -> {
                    val glyphCount = reader.u16(coverageStart + 2) ?: return null
                    if (!reader.fits(coverageStart + 4, glyphCount.toLong() * 2L)) return null
                    List(glyphCount) { reader.u16(coverageStart + 4 + it * 2) ?: return null }
                }
                2 -> {
                    val rangeCount = reader.u16(coverageStart + 2) ?: return null
                    if (!reader.fits(coverageStart + 4, rangeCount.toLong() * 6L)) return null
                    val glyphs = ArrayList<Int>()
                    var p = coverageStart + 4
                    repeat(rangeCount) {
                        val startGlyph = reader.u16(p) ?: return null
                        val endGlyph = reader.u16(p + 2) ?: return null
                        if (endGlyph < startGlyph) return null
                        for (glyph in startGlyph..endGlyph) glyphs.add(glyph)
                        p += 6
                    }
                    glyphs
                }
                else -> null
            }
        }

        private fun parseClassDefTable(
            bytes: ByteArray,
            classDefStart: Int,
            tableEnd: Int,
        ): GposClassDef? {
            val reader = SfntReader(bytes, tableEnd)
            if (!reader.fits(classDefStart, 4)) return null
            val classes = HashMap<Int, Int>()
            return when (reader.u16(classDefStart) ?: return null) {
                1 -> {
                    val startGlyph = reader.u16(classDefStart + 2) ?: return null
                    val glyphCount = reader.u16(classDefStart + 4) ?: return null
                    if (!reader.fits(classDefStart + 6, glyphCount.toLong() * 2L)) return null
                    repeat(glyphCount) {
                        val klass = reader.u16(classDefStart + 6 + it * 2) ?: return null
                        classes[startGlyph + it] = klass
                    }
                    GposClassDef(classes)
                }
                2 -> {
                    val classRangeCount = reader.u16(classDefStart + 2) ?: return null
                    if (!reader.fits(classDefStart + 4, classRangeCount.toLong() * 6L)) return null
                    var p = classDefStart + 4
                    repeat(classRangeCount) {
                        val startGlyph = reader.u16(p) ?: return null
                        val endGlyph = reader.u16(p + 2) ?: return null
                        val klass = reader.u16(p + 4) ?: return null
                        if (endGlyph < startGlyph) return null
                        for (glyph in startGlyph..endGlyph) classes[glyph] = klass
                        p += 6
                    }
                    GposClassDef(classes)
                }
                else -> null
            }
        }

        private fun valueRecordSize(format: Int): Int =
            Integer.bitCount(format) * 2

        private fun readGposXAdvance(reader: SfntReader, valueStart: Int, format: Int): Int? {
            var off = valueStart
            if ((format and GPOS_X_PLACEMENT) != 0) off += 2
            if ((format and GPOS_Y_PLACEMENT) != 0) off += 2
            val xAdvance = if ((format and GPOS_X_ADVANCE) != 0) {
                reader.i16(off)?.toInt() ?: return null
            } else {
                0
            }
            return xAdvance
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
                        format == 6 && platform == 1 && encoding == 0 -> 20
                        format == 0 && platform == 1 && encoding == 0 -> 10
                        else -> 0
                    }
                    if (score > (best?.first ?: -1)) best = score to abs
                }
                off += 8
            }
            val chosen = best?.second ?: return null
            return when (reader.u16(chosen) ?: return null) {
                0 -> CmapFormat0.parse(bytes, chosen, tableEnd, MacRomanCmapEncoding)
                4 -> CmapFormat4.parse(bytes, chosen, tableEnd)
                6 -> CmapFormat6.parse(bytes, chosen, tableEnd, MacRomanCmapEncoding)
                12 -> CmapFormat12.parse(bytes, chosen, tableEnd)
                else -> null
            }
        }
    }
}

private interface LegacyCmapEncoding {
    fun codeFor(codepoint: Int): Int?
}

private object MacRomanCmapEncoding : LegacyCmapEncoding {
    private val extended = intArrayOf(
        0x00C4, 0x00C5, 0x00C7, 0x00C9, 0x00D1, 0x00D6, 0x00DC, 0x00E1,
        0x00E0, 0x00E2, 0x00E4, 0x00E3, 0x00E5, 0x00E7, 0x00E9, 0x00E8,
        0x00EA, 0x00EB, 0x00ED, 0x00EC, 0x00EE, 0x00EF, 0x00F1, 0x00F3,
        0x00F2, 0x00F4, 0x00F6, 0x00F5, 0x00FA, 0x00F9, 0x00FB, 0x00FC,
        0x2020, 0x00B0, 0x00A2, 0x00A3, 0x00A7, 0x2022, 0x00B6, 0x00DF,
        0x00AE, 0x00A9, 0x2122, 0x00B4, 0x00A8, 0x2260, 0x00C6, 0x00D8,
        0x221E, 0x00B1, 0x2264, 0x2265, 0x00A5, 0x00B5, 0x2202, 0x2211,
        0x220F, 0x03C0, 0x222B, 0x00AA, 0x00BA, 0x03A9, 0x00E6, 0x00F8,
        0x00BF, 0x00A1, 0x00AC, 0x221A, 0x0192, 0x2248, 0x2206, 0x00AB,
        0x00BB, 0x2026, 0x00A0, 0x00C0, 0x00C3, 0x00D5, 0x0152, 0x0153,
        0x2013, 0x2014, 0x201C, 0x201D, 0x2018, 0x2019, 0x00F7, 0x25CA,
        0x00FF, 0x0178, 0x2044, 0x20AC, 0x2039, 0x203A, 0xFB01, 0xFB02,
        0x2021, 0x00B7, 0x201A, 0x201E, 0x2030, 0x00C2, 0x00CA, 0x00C1,
        0x00CB, 0x00C8, 0x00CD, 0x00CE, 0x00CF, 0x00CC, 0x00D3, 0x00D4,
        0xF8FF, 0x00D2, 0x00DA, 0x00DB, 0x00D9, 0x0131, 0x02C6, 0x02DC,
        0x00AF, 0x02D8, 0x02D9, 0x02DA, 0x00B8, 0x02DD, 0x02DB, 0x02C7,
    )

    override fun codeFor(codepoint: Int): Int? {
        if (codepoint in 0..0x7F) return codepoint
        val index = extended.indexOf(codepoint)
        return if (index >= 0) 0x80 + index else null
    }
}

private class CmapFormat0(
    private val glyphs: IntArray,
    private val encoding: LegacyCmapEncoding,
) : Cmap {
    override fun glyphId(cp: Int): Int {
        val code = encoding.codeFor(cp) ?: return 0
        return glyphs.getOrElse(code) { 0 }
    }

    companion object {
        fun parse(bytes: ByteArray, off: Int, limit: Int, encoding: LegacyCmapEncoding): CmapFormat0? {
            val reader = SfntReader(bytes, limit)
            if (!reader.fits(off, 6)) return null
            val length = reader.u16(off + 2) ?: return null
            if (length < 262 || !reader.fits(off, length)) return null
            val glyphs = IntArray(256) { reader.u8(off + 6 + it) ?: return null }
            return CmapFormat0(glyphs, encoding)
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

private class CmapFormat6(
    private val firstCode: Int,
    private val glyphs: IntArray,
    private val encoding: LegacyCmapEncoding,
) : Cmap {
    override fun glyphId(cp: Int): Int {
        val code = encoding.codeFor(cp) ?: return 0
        val index = code - firstCode
        return glyphs.getOrElse(index) { 0 }
    }

    companion object {
        fun parse(bytes: ByteArray, off: Int, limit: Int, encoding: LegacyCmapEncoding): CmapFormat6? {
            val reader = SfntReader(bytes, limit)
            if (!reader.fits(off, 10)) return null
            val length = reader.u16(off + 2) ?: return null
            if (length < 10 || !reader.fits(off, length)) return null
            val firstCode = reader.u16(off + 6) ?: return null
            val entryCount = reader.u16(off + 8) ?: return null
            if (length < 10 + entryCount * 2) return null
            if (firstCode > 0xFF || entryCount > 256 || firstCode + entryCount > 256) return null
            if (!reader.fits(off + 10, entryCount * 2)) return null
            val glyphs = IntArray(entryCount) { reader.u16(off + 10 + it * 2) ?: return null }
            return CmapFormat6(firstCode, glyphs, encoding)
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

    fun u8(offset: Int): Int? {
        if (!fits(offset, 1)) return null
        return bytes[offset].toInt() and 0xFF
    }

    fun i8(offset: Int): Byte? {
        if (!fits(offset, 1)) return null
        return bytes[offset]
    }

    fun u32(offset: Int): Long? {
        if (!fits(offset, 4)) return null
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    fun u24(offset: Int): Int? {
        if (!fits(offset, 3)) return null
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
    }

    fun fixed16Dot16(offset: Int): Float? {
        val major = i16(offset)?.toInt() ?: return null
        val minor = u16(offset + 2) ?: return null
        return major + minor / 65536f
    }

    fun f2Dot14(offset: Int): Float? =
        i16(offset)?.toInt()?.let { it / 16384f }

    fun tag(offset: Int): String? {
        if (!fits(offset, 4)) return null
        return String(
            byteArrayOf(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]),
            Charsets.ISO_8859_1,
        )
    }
}

private data class TableRecord(val offset: Int, val length: Int)
internal data class OpenTypeColorLayer(val glyphId: Int, val paletteIndex: Int)
internal data class OpenTypeColorPath(
    val color: Int?,
    val path: SkPath,
    val alpha: Float = 1f,
    val clipPaths: List<SkPath> = emptyList(),
    val shader: SkShader? = null,
    val blendMode: SkBlendMode? = null,
    val children: List<OpenTypeColorPath> = emptyList(),
)
private data class OpenTypePaletteSelection(
    val index: Int,
    val overrides: Map<Int, Int>,
) {
    fun resolve(palettes: List<List<Int>>): List<Int>? {
        if (index < 0 || index >= palettes.size) return null
        val palette = palettes[index]
        if (overrides.isEmpty()) return palette
        return palette.mapIndexed { entryIndex, color ->
            overrides[entryIndex] ?: color
        }
    }

    companion object {
        val Default = OpenTypePaletteSelection(0, emptyMap())

        fun from(palette: SkFontArguments.Palette): OpenTypePaletteSelection =
            OpenTypePaletteSelection(
                index = palette.index,
                overrides = palette.overrides.associate { it.index to it.color },
            )
    }
}
private data class OpenTypeColorFont(
    val palettes: List<List<Int>>,
    val layersByGlyph: Map<Int, List<OpenTypeColorLayer>>,
    val v1PaintsByGlyph: Map<Int, OpenTypeColorPaint>,
    val clipBoxes: List<OpenTypeClipRange>,
)
private data class OpenTypeColrTables(
    val layersByGlyph: Map<Int, List<OpenTypeColorLayer>>,
    val v1PaintsByGlyph: Map<Int, OpenTypeColorPaint>,
    val clipBoxes: List<OpenTypeClipRange>,
)
private data class OpenTypeClipRange(val startGlyphId: Int, val endGlyphId: Int, val box: OpenTypeClipBox)
private data class OpenTypeClipBox(val xMin: Int, val yMin: Int, val xMax: Int, val yMax: Int)
internal enum class OpenTypeColorExtend {
    PAD,
    REPEAT,
    REFLECT;

    fun toTileMode(): SkTileMode = when (this) {
        PAD -> SkTileMode.kClamp
        REPEAT -> SkTileMode.kRepeat
        REFLECT -> SkTileMode.kMirror
    }
}
internal data class OpenTypeColorStop(val offset: Float, val paletteIndex: Int, val alpha: Float)
internal data class OpenTypeColorLine(val extend: OpenTypeColorExtend, val stops: List<OpenTypeColorStop>)
internal data class OpenTypeSvgDocument(
    val startGlyphId: Int,
    val endGlyphId: Int,
    val bytes: ByteArray,
) {
    val text: String
        get() = String(bytes, Charsets.UTF_8)

    override fun equals(other: Any?): Boolean =
        other is OpenTypeSvgDocument &&
            startGlyphId == other.startGlyphId &&
            endGlyphId == other.endGlyphId &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = startGlyphId
        result = 31 * result + endGlyphId
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
internal enum class OpenTypeBitmapGlyphSource { CBDT_CBLC, SBIX }
internal data class OpenTypeBitmapGlyph(
    val glyphId: Int,
    val source: OpenTypeBitmapGlyphSource,
    val ppemX: Int,
    val ppemY: Int,
    val bitDepth: Int,
    val originOffsetX: Int,
    val originOffsetY: Int,
    val imageFormat: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is OpenTypeBitmapGlyph &&
            glyphId == other.glyphId &&
            source == other.source &&
            ppemX == other.ppemX &&
            ppemY == other.ppemY &&
            bitDepth == other.bitDepth &&
            originOffsetX == other.originOffsetX &&
            originOffsetY == other.originOffsetY &&
            imageFormat == other.imageFormat &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = glyphId
        result = 31 * result + source.hashCode()
        result = 31 * result + ppemX
        result = 31 * result + ppemY
        result = 31 * result + bitDepth
        result = 31 * result + originOffsetX
        result = 31 * result + originOffsetY
        result = 31 * result + imageFormat.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
internal data class OpenTypePositionedBitmapGlyph(
    val glyph: OpenTypeBitmapGlyph,
    val x: Float,
    val y: Float,
)
private class OpenTypeBitmapFont(
    private val glyphs: Map<Int, OpenTypeBitmapGlyph>,
) {
    fun glyph(glyphId: Int): OpenTypeBitmapGlyph? = glyphs[glyphId]
}
private data class OpenTypeSvgDocumentRecord(
    val startGlyphId: Int,
    val endGlyphId: Int,
    val offset: Int,
    val length: Int,
)
private class OpenTypeSvgTable(
    private val bytes: ByteArray,
    private val records: List<OpenTypeSvgDocumentRecord>,
) {
    fun documentForGlyph(glyphId: Int): OpenTypeSvgDocument? {
        val record = records.firstOrNull { glyphId in it.startGlyphId..it.endGlyphId } ?: return null
        return OpenTypeSvgDocument(
            startGlyphId = record.startGlyphId,
            endGlyphId = record.endGlyphId,
            bytes = bytes.copyOfRange(record.offset, record.offset + record.length),
        )
    }
}
internal sealed interface OpenTypeColorPaint {
    data class Layers(val paints: List<OpenTypeColorPaint>) : OpenTypeColorPaint
    data class Solid(val paletteIndex: Int, val alpha: Float) : OpenTypeColorPaint
    data class LinearGradient(
        val colorLine: OpenTypeColorLine,
        val x0: Int,
        val y0: Int,
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
    ) : OpenTypeColorPaint
    data class RadialGradient(
        val colorLine: OpenTypeColorLine,
        val x0: Int,
        val y0: Int,
        val radius0: Int,
        val x1: Int,
        val y1: Int,
        val radius1: Int,
    ) : OpenTypeColorPaint
    data class SweepGradient(
        val colorLine: OpenTypeColorLine,
        val centerX: Int,
        val centerY: Int,
        val startAngle: Float,
        val endAngle: Float,
    ) : OpenTypeColorPaint
    data class Composite(
        val source: OpenTypeColorPaint,
        val mode: SkBlendMode,
        val backdrop: OpenTypeColorPaint,
    ) : OpenTypeColorPaint
    data class Glyph(val glyphId: Int, val paint: OpenTypeColorPaint) : OpenTypeColorPaint
    data class ColrGlyph(val glyphId: Int) : OpenTypeColorPaint
    data class Transform(
        val paint: OpenTypeColorPaint,
        val xx: Float,
        val yx: Float,
        val xy: Float,
        val yy: Float,
        val dx: Float,
        val dy: Float,
    ) : OpenTypeColorPaint
    data class Translate(val paint: OpenTypeColorPaint, val dx: Int, val dy: Int) : OpenTypeColorPaint
}

private data class OpenTypeShaderStops(
    val colors: IntArray,
    val positions: FloatArray,
    val tileMode: SkTileMode,
    val tBias: Float,
    val tScale: Float,
)

private class OpenTypeLinearGradientShader(
    private val p0: SkPoint,
    private val p1: SkPoint,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    private val tBias: Float,
    private val tScale: Float,
) : SkShader() {
    private val xformedColors = IntArray(srcColors.size)
    private val xformedColorsF16 = FloatArray(srcColors.size * 4)
    private var invLenSqDirX = 0f
    private var invLenSqDirY = 0f

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)
        val dx = p1.fX - p0.fX
        val dy = p1.fY - p0.fY
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) {
            invLenSqDirX = 0f
            invLenSqDirY = 0f
        } else {
            val inv = 1f / lenSq
            invLenSqDirX = dx * inv
            invLenSqDirY = dy * inv
        }
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            val color = xformedColors.firstOrNull() ?: 0
            for (i in 0 until count) dst[i] = color
            return
        }
        var lx = inv.sx * (devX + 0.5f) + inv.kx * (devY + 0.5f) + inv.tx
        var ly = inv.ky * (devX + 0.5f) + inv.sy * (devY + 0.5f) + inv.ty
        for (i in 0 until count) {
            val rawT = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
            val t = (rawT - tBias) / tScale
            dst[i] = lookupStop(t, positions, xformedColors, tileMode)
            lx += inv.sx
            ly += inv.ky
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
            var di = 0
            for (i in 0 until count) {
                dst[di] = xformedColorsF16[0]
                dst[di + 1] = xformedColorsF16[1]
                dst[di + 2] = xformedColorsF16[2]
                dst[di + 3] = xformedColorsF16[3]
                di += 4
            }
            return
        }
        var lx = inv.sx * (devX + 0.5f) + inv.kx * (devY + 0.5f) + inv.tx
        var ly = inv.ky * (devX + 0.5f) + inv.sy * (devY + 0.5f) + inv.ty
        var di = 0
        for (i in 0 until count) {
            val rawT = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
            val t = (rawT - tBias) / tScale
            lookupStopF16(t, positions, xformedColorsF16, tileMode, dst, di)
            lx += inv.sx
            ly += inv.ky
            di += 4
        }
    }
}

private class OpenTypeTwoPointRadialGradientShader(
    private val c0: SkPoint,
    private val r0: Float,
    private val c1: SkPoint,
    private val r1: Float,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    private val tBias: Float,
    private val tScale: Float,
) : SkShader() {
    private val xformedColors = IntArray(srcColors.size)
    private val xformedColorsF16 = FloatArray(srcColors.size * 4)
    private val dcx = c1.fX - c0.fX
    private val dcy = c1.fY - c0.fY
    private val dr = r1 - r0

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            val color = xformedColors.firstOrNull() ?: 0
            for (i in 0 until count) dst[i] = color
            return
        }
        var lx = inv.sx * (devX + 0.5f) + inv.kx * (devY + 0.5f) + inv.tx
        var ly = inv.ky * (devX + 0.5f) + inv.sy * (devY + 0.5f) + inv.ty
        for (i in 0 until count) {
            val omega = radialOmega(lx, ly)
            dst[i] = if (omega == null) 0 else lookupStop((omega - tBias) / tScale, positions, xformedColors, tileMode)
            lx += inv.sx
            ly += inv.ky
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
            var di = 0
            for (i in 0 until count) {
                dst[di] = xformedColorsF16[0]
                dst[di + 1] = xformedColorsF16[1]
                dst[di + 2] = xformedColorsF16[2]
                dst[di + 3] = xformedColorsF16[3]
                di += 4
            }
            return
        }
        var lx = inv.sx * (devX + 0.5f) + inv.kx * (devY + 0.5f) + inv.tx
        var ly = inv.ky * (devX + 0.5f) + inv.sy * (devY + 0.5f) + inv.ty
        var di = 0
        for (i in 0 until count) {
            val omega = radialOmega(lx, ly)
            if (omega == null) {
                dst[di] = 0f
                dst[di + 1] = 0f
                dst[di + 2] = 0f
                dst[di + 3] = 0f
            } else {
                lookupStopF16((omega - tBias) / tScale, positions, xformedColorsF16, tileMode, dst, di)
            }
            lx += inv.sx
            ly += inv.ky
            di += 4
        }
    }

    private fun radialOmega(x: Float, y: Float): Float? {
        val fx = x - c0.fX
        val fy = y - c0.fY
        val a = dcx * dcx + dcy * dcy - dr * dr
        val b = -2f * (fx * dcx + fy * dcy + r0 * dr)
        val c = fx * fx + fy * fy - r0 * r0
        val roots = FloatArray(2)
        var count = 0
        if (abs(a) <= 0.000001f) {
            if (abs(b) <= 0.000001f) return null
            roots[count++] = -c / b
        } else {
            val disc = b * b - 4f * a * c
            if (disc < 0f) return null
            val s = sqrt(disc)
            val denom = 2f * a
            roots[count++] = (-b + s) / denom
            roots[count++] = (-b - s) / denom
        }
        var best: Float? = null
        for (i in 0 until count) {
            val omega = roots[i]
            if (!omega.isFinite()) continue
            val radius = r0 + dr * omega
            if (radius <= 0f) continue
            if (best == null || omega > best) best = omega
        }
        return best
    }
}

private class OpenTypeSweepGradientShader(
    private val center: SkPoint,
    private val startAngle: Float,
    private val endAngle: Float,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    private val tBias: Float,
    private val tScale: Float,
) : SkShader() {
    private val xformedColors = IntArray(srcColors.size)
    private val xformedColorsF16 = FloatArray(srcColors.size * 4)
    private val angleSpan = if (startAngle <= endAngle) endAngle - startAngle else startAngle - endAngle

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            val color = xformedColors.firstOrNull() ?: 0
            for (i in 0 until count) dst[i] = color
            return
        }
        var lx = inv.sx * (devX + 0.5f) + inv.kx * (devY + 0.5f) + inv.tx
        var ly = inv.ky * (devX + 0.5f) + inv.sy * (devY + 0.5f) + inv.ty
        for (i in 0 until count) {
            val rawT = sweepT(canvasAngleToDesignAngle(canvasSweepAngle(lx - center.fX, ly - center.fY)))
            dst[i] = lookupStop((rawT - tBias) / tScale, positions, xformedColors, tileMode)
            lx += inv.sx
            ly += inv.ky
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
            var di = 0
            for (i in 0 until count) {
                dst[di] = xformedColorsF16[0]
                dst[di + 1] = xformedColorsF16[1]
                dst[di + 2] = xformedColorsF16[2]
                dst[di + 3] = xformedColorsF16[3]
                di += 4
            }
            return
        }
        var lx = inv.sx * (devX + 0.5f) + inv.kx * (devY + 0.5f) + inv.tx
        var ly = inv.ky * (devX + 0.5f) + inv.sy * (devY + 0.5f) + inv.ty
        var di = 0
        for (i in 0 until count) {
            val rawT = sweepT(canvasAngleToDesignAngle(canvasSweepAngle(lx - center.fX, ly - center.fY)))
            lookupStopF16((rawT - tBias) / tScale, positions, xformedColorsF16, tileMode, dst, di)
            lx += inv.sx
            ly += inv.ky
            di += 4
        }
    }

    private fun sweepT(angle: Float): Float {
        return if (startAngle <= endAngle) {
            (angle - startAngle) / angleSpan
        } else {
            1f - (angle - endAngle) / angleSpan
        }
    }
}

private fun canvasSweepAngle(x: Float, y: Float): Float {
    if (x == 0f && y == 0f) return 0f
    return atan2(y, x) * 180f / PI.toFloat()
}

private fun canvasAngleToDesignAngle(angle: Float): Float {
    val design = -angle
    return when {
        design < 0f -> design + 360f
        design >= 360f -> design - 360f
        else -> design
    }
}
private data class OpenTypeNames(
    val familyName: String?,
    val postScriptName: String?,
    val localizedFamilyNames: List<SkTypeface.LocalizedString>,
    val styleName: String?,
)
private data class ParsedOpenTypeStyle(
    val style: SkFontStyle,
    val hasMetadata: Boolean,
)
private data class KernTable(private val pairs: Map<Int, Int>) {
    fun adjustment(leftGlyph: Int, rightGlyph: Int): Int =
        pairs[kernPairKey(leftGlyph, rightGlyph)] ?: 0
}
private data class GposPairTable(private val pairs: Map<Int, Int>) {
    fun adjustment(leftGlyph: Int, rightGlyph: Int): Int =
        pairs[kernPairKey(leftGlyph, rightGlyph)] ?: 0
}
private data class GposClassDef(private val classes: Map<Int, Int>) {
    fun classOf(glyphId: Int): Int = classes[glyphId] ?: 0
    fun glyphsForClass(klass: Int, numGlyphs: Int): List<Int> =
        if (klass == 0) {
            (0 until numGlyphs).filter { classOf(it) == 0 }
        } else {
            classes.entries.asSequence()
                .filter { it.value == klass }
                .map { it.key }
                .toList()
        }
}
private data class NameRecord(val value: String, val platform: Int, val language: Int)
private data class TtPoint(val x: Float, val y: Float, val onCurve: Boolean)
private data class GlyphOutline(val contours: List<List<TtPoint>>) {
    val points: List<TtPoint> = contours.flatten()
}
private data class OpenTypeVariationPosition(val normalizedCoordinates: FloatArray) {
    val isDefault: Boolean = normalizedCoordinates.isEmpty() || normalizedCoordinates.all { it == 0f }

    companion object {
        val Default = OpenTypeVariationPosition(FloatArray(0))
    }
}
private data class GlyphDeltas(val x: FloatArray, val y: FloatArray)

private class GvarTable(
    private val bytes: ByteArray,
    private val limit: Int,
    private val axisCount: Int,
    private val sharedTuples: List<FloatArray>,
    private val glyphOffsets: IntArray,
    private val glyphDataStart: Int,
) {
    fun simpleGlyphDeltas(glyphId: Int, pointCount: Int, normalizedCoordinates: FloatArray): GlyphDeltas? {
        if (glyphId < 0 || glyphId + 1 >= glyphOffsets.size || pointCount <= 0) return null
        val start = glyphDataStart + glyphOffsets[glyphId]
        val end = glyphDataStart + glyphOffsets[glyphId + 1]
        if (start == end) return null
        val reader = SfntReader(bytes, min(limit, end))
        if (!reader.fits(start, 4)) return null
        val tupleVariationCountField = reader.u16(start) ?: return null
        val tupleVariationCount = tupleVariationCountField and GVAR_TUPLE_COUNT_MASK
        if (tupleVariationCount <= 0) return null
        val offsetToData = reader.u16(start + 2) ?: return null
        val tupleDataStart = start + offsetToData
        if (!reader.fits(tupleDataStart, 0)) return null
        val headers = ArrayList<GvarTupleHeader>(tupleVariationCount)
        var headerOffset = start + 4
        repeat(tupleVariationCount) {
            if (!reader.fits(headerOffset, 4)) return null
            val variationDataSize = reader.u16(headerOffset) ?: return null
            val tupleIndex = reader.u16(headerOffset + 2) ?: return null
            headerOffset += 4
            val peak = when {
                (tupleIndex and GVAR_EMBEDDED_PEAK_TUPLE) != 0 -> {
                    if (!reader.fits(headerOffset, axisCount * 2)) return null
                    FloatArray(axisCount) { axis -> reader.f2Dot14(headerOffset + axis * 2) ?: return null }
                        .also { headerOffset += axisCount * 2 }
                }
                else -> sharedTuples.getOrNull(tupleIndex and GVAR_TUPLE_INDEX_MASK) ?: return null
            }
            val startTuple: FloatArray?
            val endTuple: FloatArray?
            if ((tupleIndex and GVAR_INTERMEDIATE_REGION) != 0) {
                if (!reader.fits(headerOffset, axisCount * 4)) return null
                startTuple = FloatArray(axisCount) { axis -> reader.f2Dot14(headerOffset + axis * 2) ?: return null }
                headerOffset += axisCount * 2
                endTuple = FloatArray(axisCount) { axis -> reader.f2Dot14(headerOffset + axis * 2) ?: return null }
                headerOffset += axisCount * 2
            } else {
                startTuple = null
                endTuple = null
            }
            headers.add(GvarTupleHeader(variationDataSize, tupleIndex, peak, startTuple, endTuple))
        }
        if (tupleDataStart < headerOffset || tupleDataStart > end) return null
        var dataOffset = tupleDataStart
        val sharedPoints: IntArray?
        if ((tupleVariationCountField and GVAR_SHARED_POINT_NUMBERS) != 0) {
            val points = readPackedPoints(reader, dataOffset, pointCount + PHANTOM_POINT_COUNT) ?: return null
            sharedPoints = points.values
            dataOffset = points.nextOffset
        } else {
            sharedPoints = null
        }
        val x = FloatArray(pointCount)
        val y = FloatArray(pointCount)
        for (header in headers) {
            if (!reader.fits(dataOffset, header.variationDataSize)) return null
            val tupleEnd = dataOffset + header.variationDataSize
            var tupleDataOffset = dataOffset
            val points = if ((header.tupleIndex and GVAR_PRIVATE_POINT_NUMBERS) != 0) {
                val packed = readPackedPoints(reader, tupleDataOffset, pointCount + PHANTOM_POINT_COUNT) ?: return null
                tupleDataOffset = packed.nextOffset
                packed.values
            } else {
                sharedPoints
            }
            val targetPoints = points ?: IntArray(pointCount + PHANTOM_POINT_COUNT) { it }
            val xDeltas = readPackedDeltas(reader, tupleDataOffset, targetPoints.size) ?: return null
            tupleDataOffset = xDeltas.nextOffset
            val yDeltas = readPackedDeltas(reader, tupleDataOffset, targetPoints.size) ?: return null
            if (yDeltas.nextOffset > tupleEnd) return null
            val scalar = tupleScalar(normalizedCoordinates, header.peak, header.startTuple, header.endTuple)
            if (scalar != 0f) {
                for (i in targetPoints.indices) {
                    val point = targetPoints[i]
                    if (point in 0 until pointCount) {
                        x[point] += xDeltas.values[i] * scalar
                        y[point] += yDeltas.values[i] * scalar
                    }
                }
            }
            dataOffset = tupleEnd
        }
        return GlyphDeltas(x, y)
    }

    private fun tupleScalar(
        normalizedCoordinates: FloatArray,
        peak: FloatArray,
        startTuple: FloatArray?,
        endTuple: FloatArray?,
    ): Float {
        var scalar = 1f
        for (axis in 0 until axisCount) {
            val coordinate = normalizedCoordinates.getOrElse(axis) { 0f }
            val peakValue = peak[axis]
            if (peakValue == 0f) continue
            val axisScalar = if (startTuple != null && endTuple != null) {
                val start = startTuple[axis]
                val end = endTuple[axis]
                when {
                    coordinate < start || coordinate > end || start > peakValue || peakValue > end -> 0f
                    coordinate == peakValue -> 1f
                    coordinate < peakValue -> (coordinate - start) / (peakValue - start)
                    else -> (end - coordinate) / (end - peakValue)
                }
            } else {
                if (coordinate == 0f || coordinate.sign != peakValue.sign || abs(coordinate) > abs(peakValue)) {
                    0f
                } else {
                    coordinate / peakValue
                }
            }
            scalar *= axisScalar
            if (scalar == 0f) return 0f
        }
        return scalar
    }

    private fun readPackedPoints(reader: SfntReader, offset: Int, maxPointCount: Int): PackedInts? {
        var off = offset
        val first = reader.u8(off++) ?: return null
        if (first == 0) return PackedInts(IntArray(maxPointCount) { it }, off)
        val pointCount = if ((first and 0x80) != 0) {
            val second = reader.u8(off++) ?: return null
            ((first and 0x7F) shl 8) or second
        } else {
            first
        }
        if (pointCount < 0 || pointCount > maxPointCount) return null
        val points = IntArray(pointCount)
        var pointIndex = 0
        var point = 0
        while (pointIndex < pointCount) {
            val control = reader.u8(off++) ?: return null
            val wordDeltas = (control and 0x80) != 0
            val runCount = (control and 0x7F) + 1
            if (pointIndex + runCount > pointCount) return null
            repeat(runCount) {
                val delta = if (wordDeltas) {
                    val value = reader.u16(off) ?: return null
                    off += 2
                    value
                } else {
                    reader.u8(off++) ?: return null
                }
                point += delta
                if (point >= maxPointCount) return null
                points[pointIndex++] = point
            }
        }
        return PackedInts(points, off)
    }

    private fun readPackedDeltas(reader: SfntReader, offset: Int, count: Int): PackedInts? {
        val values = IntArray(count)
        var off = offset
        var index = 0
        while (index < count) {
            val control = reader.u8(off++) ?: return null
            val runCount = (control and 0x3F) + 1
            if (index + runCount > count) return null
            when {
                (control and 0x80) != 0 -> repeat(runCount) {
                    values[index++] = 0
                }
                (control and 0x40) != 0 -> repeat(runCount) {
                    values[index++] = reader.i16(off)?.toInt() ?: return null
                    off += 2
                }
                else -> repeat(runCount) {
                    values[index++] = reader.i8(off)?.toInt() ?: return null
                    off += 1
                }
            }
        }
        return PackedInts(values, off)
    }
}

private data class GvarTupleHeader(
    val variationDataSize: Int,
    val tupleIndex: Int,
    val peak: FloatArray,
    val startTuple: FloatArray?,
    val endTuple: FloatArray?,
)
private data class PackedInts(val values: IntArray, val nextOffset: Int)

private const val COLR_FOREGROUND_PALETTE_INDEX = 0xFFFF
private const val GVAR_LONG_OFFSETS = 0x0001
private const val GVAR_SHARED_POINT_NUMBERS = 0x8000
private const val GVAR_TUPLE_COUNT_MASK = 0x0FFF
private const val GVAR_EMBEDDED_PEAK_TUPLE = 0x8000
private const val GVAR_INTERMEDIATE_REGION = 0x4000
private const val GVAR_PRIVATE_POINT_NUMBERS = 0x2000
private const val GVAR_TUPLE_INDEX_MASK = 0x0FFF
private const val PHANTOM_POINT_COUNT = 4

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
private const val GPOS_PAIR_ADJUSTMENT_LOOKUP = 2
private const val GPOS_X_PLACEMENT = 0x0001
private const val GPOS_Y_PLACEMENT = 0x0002
private const val GPOS_X_ADVANCE = 0x0004
private const val MAX_COLOR_PALETTES = 256
private const val MAX_COLOR_PALETTE_ENTRIES = 4096
private const val MAX_COLOR_RECORDS = 4096
private const val MAX_EXPANDED_COLOR_RECORDS = 65536L
private const val MAX_COLOR_BASE_GLYPHS = 8192
private const val MAX_COLOR_LAYERS = 16384
private const val MAX_LAYERS_PER_COLOR_GLYPH = 256
private const val MAX_EXPANDED_COLOR_LAYERS = 65536L
private const val MAX_COLOR_PAINT_DEPTH = 32
private const val MAX_COLOR_STOPS = 4096
private const val MAX_SVG_DOCUMENT_RECORDS = 8192
private const val MAX_SVG_GLYPHS_PER_RECORD = 4096
private const val MAX_BITMAP_STRIKES = 64
private const val MAX_BITMAP_SUBTABLES = 4096
private const val MAX_BITMAP_PAYLOAD_BYTES = 16 * 1024 * 1024
private const val CBLC_BITMAP_SIZE_TABLE_SIZE = 48
private val CBDT_PNG_IMAGE_FORMATS = setOf(17, 18, 19)
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val OS2_FS_SELECTION_ITALIC = 0x0001
private const val OS2_FS_SELECTION_BOLD = 0x0020
private const val OS2_USE_TYPO_METRICS = 0x0080
private const val OS2_FS_SELECTION_OBLIQUE = 0x0200
private const val HEAD_MAC_STYLE_BOLD = 0x0001
private const val HEAD_MAC_STYLE_ITALIC = 0x0002

/**
 * Maps the pure Kotlin [OpenTypeStyle] to the Skia facade [SkFontStyle].
 */
private fun OpenTypeStyle.toSkFontStyle(): SkFontStyle = SkFontStyle(
    weight = weight,
    width = width,
    slant = when (slant) {
        OpenTypeStyleSlant.UPRIGHT -> SkFontStyle.Slant.kUpright_Slant
        OpenTypeStyleSlant.ITALIC -> SkFontStyle.Slant.kItalic_Slant
        OpenTypeStyleSlant.OBLIQUE -> SkFontStyle.Slant.kOblique_Slant
    },
)

/**
 * Facade parity evidence dump — compares pure Kotlin core facts with legacy
 * [ParsedTrueTypeFont] facts for the same font bytes.
 *
 * @property typefaceId Stable typeface identity from the pure Kotlin core.
 * @property coreFamilyName Family name from [OpenTypeFaceData.names].
 * @property legacyFamilyName Family name from [ParsedTrueTypeFont].
 * @property corePostScriptName PostScript name from [OpenTypeFaceData.names].
 * @property legacyPostScriptName PostScript name from [ParsedTrueTypeFont].
 * @property coreGlyphCount Glyph count from [OpenTypeFaceData.metrics].
 * @property legacyGlyphCount Glyph count from [ParsedTrueTypeFont].
 * @property coreTableTags Sorted table tags from [OpenTypeFaceData.directory].
 * @property legacyTableTags Table tags from [ParsedTrueTypeFont].
 * @property coreAxisCount Variation axis count from [OpenTypeFaceData.variations].
 * @property legacyAxisCount Variation axis count from [ParsedTrueTypeFont].
 * @property coreDiagnosticCount Non-fatal parse diagnostic count.
 */
internal data class OpenTypeFaceParityDump(
    val typefaceId: String,
    val coreFamilyName: String?,
    val legacyFamilyName: String,
    val corePostScriptName: String?,
    val legacyPostScriptName: String?,
    val coreGlyphCount: Int?,
    val legacyGlyphCount: Int,
    val coreTableTags: List<String>,
    val legacyTableTags: List<String>,
    val coreAxisCount: Int,
    val legacyAxisCount: Int,
    val coreDiagnosticCount: Int,
)

private fun openTypeTagToString(tag: Int): String = buildString(4) {
    append(((tag ushr 24) and 0xFF).toChar())
    append(((tag ushr 16) and 0xFF).toChar())
    append(((tag ushr 8) and 0xFF).toChar())
    append((tag and 0xFF).toChar())
}

private fun openTypeTagToInt(tag: String): Int {
    require(tag.length == 4) { "OpenType tag must be 4 characters" }
    return ((tag[0].code and 0xFF) shl 24) or
        ((tag[1].code and 0xFF) shl 16) or
        ((tag[2].code and 0xFF) shl 8) or
        (tag[3].code and 0xFF)
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
