package org.graphiks.kanvas.font.scaler

import org.graphiks.kanvas.font.sfnt.OpenTypeFaceData
import org.graphiks.kanvas.font.sfnt.HorizontalGlyphMetric
import org.graphiks.kanvas.font.sfnt.MetricsTables
import org.graphiks.kanvas.font.sfnt.SFNTTableTag
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Scales glyph outlines and metrics from parsed font data into requested design positions.
 */
interface GlyphScaler {
    /**
     * Produces an outline for one glyph at a variation position.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position to apply before scaling.
     * @return Scaled glyph outline.
     */
    fun outline(glyphId: UInt, position: VariationPosition = VariationPosition()): GlyphOutline

    /**
     * Produces metrics for one glyph at a variation position.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position to apply before measuring.
     * @return Scaled glyph metrics.
     */
    fun metrics(glyphId: UInt, position: VariationPosition = VariationPosition()): GlyphMetrics
}

/**
 * Typed outline drawing command shared by future glyph outline parsers.
 */
sealed interface OutlineCommand {
    /**
     * Starts a new contour at a point.
     *
     * @property x Destination x coordinate.
     * @property y Destination y coordinate.
     */
    data class MoveTo(
        val x: Double,
        val y: Double,
    ) : OutlineCommand

    /**
     * Adds a straight line segment to a point.
     *
     * @property x Destination x coordinate.
     * @property y Destination y coordinate.
     */
    data class LineTo(
        val x: Double,
        val y: Double,
    ) : OutlineCommand

    /**
     * Adds a quadratic Bezier segment.
     *
     * @property controlX Control point x coordinate.
     * @property controlY Control point y coordinate.
     * @property x Destination x coordinate.
     * @property y Destination y coordinate.
     */
    data class QuadraticTo(
        val controlX: Double,
        val controlY: Double,
        val x: Double,
        val y: Double,
    ) : OutlineCommand

    /**
     * Adds a cubic Bezier segment.
     *
     * @property controlX1 First control point x coordinate.
     * @property controlY1 First control point y coordinate.
     * @property controlX2 Second control point x coordinate.
     * @property controlY2 Second control point y coordinate.
     * @property x Destination x coordinate.
     * @property y Destination y coordinate.
     */
    data class CubicTo(
        val controlX1: Double,
        val controlY1: Double,
        val controlX2: Double,
        val controlY2: Double,
        val x: Double,
        val y: Double,
    ) : OutlineCommand

    /**
     * Closes the current contour.
     */
    data object Close : OutlineCommand
}

/**
 * Creates a typed move-to outline command.
 *
 * @param x Destination x coordinate.
 * @param y Destination y coordinate.
 * @return Outline command for starting a contour.
 */
fun moveTo(x: Double, y: Double): OutlineCommand = OutlineCommand.MoveTo(x, y)

/**
 * Creates a typed line-to outline command.
 *
 * @param x Destination x coordinate.
 * @param y Destination y coordinate.
 * @return Outline command for adding a straight line.
 */
fun lineTo(x: Double, y: Double): OutlineCommand = OutlineCommand.LineTo(x, y)

/**
 * Creates a typed quadratic curve outline command.
 *
 * @param controlX Control point x coordinate.
 * @param controlY Control point y coordinate.
 * @param x Destination x coordinate.
 * @param y Destination y coordinate.
 * @return Outline command for adding a quadratic Bezier segment.
 */
fun quadraticTo(
    controlX: Double,
    controlY: Double,
    x: Double,
    y: Double,
): OutlineCommand = OutlineCommand.QuadraticTo(controlX, controlY, x, y)

/**
 * Creates a typed cubic curve outline command.
 *
 * @param controlX1 First control point x coordinate.
 * @param controlY1 First control point y coordinate.
 * @param controlX2 Second control point x coordinate.
 * @param controlY2 Second control point y coordinate.
 * @param x Destination x coordinate.
 * @param y Destination y coordinate.
 * @return Outline command for adding a cubic Bezier segment.
 */
fun cubicTo(
    controlX1: Double,
    controlY1: Double,
    controlX2: Double,
    controlY2: Double,
    x: Double,
    y: Double,
): OutlineCommand = OutlineCommand.CubicTo(controlX1, controlY1, controlX2, controlY2, x, y)

/**
 * Creates a typed close-contour outline command.
 *
 * @return Outline command for closing the current contour.
 */
fun close(): OutlineCommand = OutlineCommand.Close

/**
 * Scaled outline geometry for one glyph.
 *
 * @property glyphId Font-specific glyph identifier.
 * @property contours Legacy opaque contour commands retained for source compatibility.
 * @property commands Typed outline commands for new pure Kotlin outline consumers.
 */
data class GlyphOutline(
    val glyphId: UInt,
    val contours: List<String> = emptyList(),
    val commands: List<OutlineCommand> = emptyList(),
)

/**
 * Scaled metrics for a glyph.
 *
 * @property advanceX Horizontal advance in user-independent font units.
 * @property advanceY Vertical advance in user-independent font units.
 * @property bounds Tight glyph bounds when available.
 */
data class GlyphMetrics(
    val advanceX: Double,
    val advanceY: Double,
    val bounds: GlyphBounds,
)

/**
 * Stable scaler diagnostic code families used by evidence dumps.
 */
object FontScalerDiagnosticCodes {
    const val OUTLINE_FORMAT_UNSUPPORTED: String = "font.outline-format-unsupported"
    const val CFF_OPERATOR_UNSUPPORTED: String = "font.cff-operator-unsupported"
    const val VARIATION_DATA_MALFORMED: String = "font.variation-data-malformed"
    const val VARIATION_AXIS_UNSUPPORTED: String = "font.variation-axis-unsupported"
    const val METRICS_VARIATION_UNAVAILABLE: String = "font.metrics-variation-unavailable"
    const val REQUIRED_TABLE_MISSING: String = "font.required-table-missing"
}

/**
 * One stable scaler diagnostic attached to a glyph evidence dump.
 *
 * @property code Stable reason-code family.
 * @property detail Deterministic leaf detail inside the reason-code family.
 * @property operation Operation that produced the diagnostic, such as `outline` or `metrics`.
 * @property glyphId Glyph affected by the diagnostic.
 * @property severity Stable severity label such as `refusal` or `warning`.
 */
data class FontScalerDiagnostic(
    val code: String,
    val detail: String,
    val operation: String,
    val glyphId: UInt,
    val severity: String = "refusal",
) {
    init {
        require(code.isStableToken()) { "font scaler diagnostic code must be a stable token." }
        require(detail.isStableToken()) { "font scaler diagnostic detail must be a stable token." }
        require(operation.isStableToken()) { "font scaler diagnostic operation must be a stable token." }
        require(severity.isStableToken()) { "font scaler diagnostic severity must be a stable token." }
    }

    internal fun toCanonicalJson(): String = buildString {
        append("{")
        append(scalerJsonString("code")).append(": ").append(scalerJsonString(code)).append(", ")
        append(scalerJsonString("detail")).append(": ").append(scalerJsonString(detail)).append(", ")
        append(scalerJsonString("operation")).append(": ").append(scalerJsonString(operation)).append(", ")
        append(scalerJsonString("glyphId")).append(": ").append(glyphId.toString()).append(", ")
        append(scalerJsonString("severity")).append(": ").append(scalerJsonString(severity))
        append("}")
    }
}

/**
 * Unsupported scaler operation carrying stable evidence diagnostics.
 */
class FontScalerRefusalException(
    val diagnostic: FontScalerDiagnostic,
    message: String,
) : UnsupportedOperationException(message)

/**
 * One variation coordinate in deterministic evidence order.
 *
 * @property tag Four-character OpenType axis tag.
 * @property value User-space or normalized coordinate value.
 */
data class VariationCoordinateEvidence(
    val tag: String,
    val value: Double,
) {
    init {
        require(tag.length == 4) { "variation evidence axis tag must contain exactly four characters." }
        require(tag.all { character -> character.code in 0x20..0x7e }) {
            "variation evidence axis tag $tag must contain printable ASCII characters."
        }
        require(value.isFinite()) { "variation evidence axis $tag value must be finite." }
    }

    internal fun toCanonicalJson(): String = buildString {
        append("{")
        append(scalerJsonString("tag")).append(": ").append(scalerJsonString(tag)).append(", ")
        append(scalerJsonString("value")).append(": ").append(value.toCanonicalScalerNumber())
        append("}")
    }
}

/**
 * Bounded `loca` range facts for one TrueType glyph.
 *
 * @property start Inclusive byte offset in the `glyf` table.
 * @property endExclusive Exclusive byte offset in the `glyf` table.
 * @property byteLength Number of bytes in the glyph range.
 * @property isEmpty Whether the `loca` entry points to an empty glyph.
 */
data class TrueTypeLocaRangeEvidence(
    val start: Int,
    val endExclusive: Int,
    val byteLength: Int,
    val isEmpty: Boolean,
) {
    init {
        require(start >= 0) { "loca evidence start must be non-negative." }
        require(endExclusive >= start) { "loca evidence endExclusive must be at least start." }
        require(byteLength == endExclusive - start) { "loca evidence byteLength must match the range." }
        require(isEmpty == (byteLength == 0)) { "loca evidence isEmpty must match byteLength." }
    }

    internal fun toCanonicalJson(): String = buildString {
        append("{")
        append(scalerJsonString("start")).append(": ").append(start).append(", ")
        append(scalerJsonString("endExclusive")).append(": ").append(endExclusive).append(", ")
        append(scalerJsonString("byteLength")).append(": ").append(byteLength).append(", ")
        append(scalerJsonString("isEmpty")).append(": ").append(isEmpty)
        append("}")
    }
}

/**
 * Deterministic current-state evidence for one scaled TrueType `glyf` glyph.
 *
 * The dump is intentionally narrow: it records the current pure Kotlin `glyf` scaler output and
 * stable refusals without claiming complete CFF/CFF2, IUP, phantom metrics, or full variable-font
 * support.
 */
data class ScaledTrueTypeGlyphEvidence(
    val glyphId: UInt,
    val requestedVariationPosition: List<VariationCoordinateEvidence>,
    val normalizedVariationPosition: List<VariationCoordinateEvidence>,
    val outlineCommands: List<String>,
    val outlineCommandDump: String,
    val outlineCommandDumpSha256: String,
    val conservativeBounds: GlyphBounds?,
    val metrics: GlyphMetrics?,
    val locaRange: TrueTypeLocaRangeEvidence,
    val scalerFamily: String = TRUE_TYPE_GLYF_SCALER_FAMILY,
    val route: String = TRUE_TYPE_GLYF_SCALER_ROUTE,
    val diagnostics: List<FontScalerDiagnostic> = emptyList(),
) {
    init {
        require(requestedVariationPosition.isSortedByTag()) {
            "requested variation evidence coordinates must be sorted by axis tag."
        }
        require(normalizedVariationPosition.isSortedByTag()) {
            "normalized variation evidence coordinates must be sorted by axis tag."
        }
        require(outlineCommands.none { line -> line.any { it == '\n' || it == '\r' } }) {
            "outline command evidence lines must be single-line."
        }
        require(outlineCommandDump == outlineCommands.joinToString("\n")) {
            "outline command dump must match outlineCommands."
        }
        require(outlineCommandDumpSha256 == outlineCommandDump.scalerSha256Hex()) {
            "outline command dump SHA-256 must match outlineCommandDump."
        }
        require(scalerFamily.isStableToken()) { "scalerFamily must be a stable token." }
        require(route.isStableToken()) { "route must be a stable token." }
    }

    /**
     * Serializes this evidence with stable field order and no host-dependent facts.
     */
    fun toCanonicalJson(): String = buildString {
        append("{\n")
        append("  ").append(scalerJsonString("glyphId")).append(": ").append(glyphId.toString()).append(",\n")
        append("  ").append(scalerJsonString("scalerFamily")).append(": ")
            .append(scalerJsonString(scalerFamily)).append(",\n")
        append("  ").append(scalerJsonString("route")).append(": ").append(scalerJsonString(route)).append(",\n")
        append("  ").append(scalerJsonString("locaRange")).append(": ")
            .append(locaRange.toCanonicalJson()).append(",\n")
        append("  ").append(scalerJsonString("requestedVariationPosition")).append(": ")
            .append(requestedVariationPosition.toCoordinateJson()).append(",\n")
        append("  ").append(scalerJsonString("normalizedVariationPosition")).append(": ")
            .append(normalizedVariationPosition.toCoordinateJson()).append(",\n")
        append("  ").append(scalerJsonString("outlineCommands")).append(": ")
            .append(outlineCommands.toJsonStringArray()).append(",\n")
        append("  ").append(scalerJsonString("outlineCommandDumpSha256")).append(": ")
            .append(scalerJsonString(outlineCommandDumpSha256)).append(",\n")
        append("  ").append(scalerJsonString("conservativeBounds")).append(": ")
            .append(conservativeBounds?.toCanonicalJson() ?: "null").append(",\n")
        append("  ").append(scalerJsonString("metrics")).append(": ")
            .append(metrics?.toCanonicalJson() ?: "null").append(",\n")
        append("  ").append(scalerJsonString("diagnostics")).append(": ")
            .append(diagnostics.toDiagnosticJson()).append("\n")
        append("}")
    }
}

/**
 * Horizontal TrueType glyph metric decoded from `hmtx`-equivalent data.
 *
 * @property advanceX Horizontal advance in font units before scaler conversion.
 * @property leftSideBearing Horizontal left side bearing in font units before scaler conversion.
 */
data class TrueTypeGlyphHorizontalMetrics(
    val advanceX: Double,
    val leftSideBearing: Double,
) {
    init {
        require(advanceX.isFinite()) { "advanceX must be finite." }
        require(leftSideBearing.isFinite()) { "leftSideBearing must be finite." }
    }
}

/**
 * Axis-aligned glyph bounds.
 *
 * @property left Minimum x coordinate.
 * @property top Minimum y coordinate.
 * @property right Maximum x coordinate.
 * @property bottom Maximum y coordinate.
 */
data class GlyphBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/**
 * Computes conservative bounds for typed outline commands.
 *
 * Bounds include all command endpoints and Bezier control points. This is conservative for curves
 * and intentionally does not solve exact curve extrema.
 *
 * @return Axis-aligned bounds, or null when no command carries coordinates.
 */
fun List<OutlineCommand>.conservativeBounds(): GlyphBounds? {
    var left = Double.POSITIVE_INFINITY
    var top = Double.POSITIVE_INFINITY
    var right = Double.NEGATIVE_INFINITY
    var bottom = Double.NEGATIVE_INFINITY
    var hasPoint = false

    fun include(x: Double, y: Double) {
        left = min(left, x)
        top = min(top, y)
        right = max(right, x)
        bottom = max(bottom, y)
        hasPoint = true
    }

    for (command in this) {
        when (command) {
            is OutlineCommand.MoveTo -> include(command.x, command.y)
            is OutlineCommand.LineTo -> include(command.x, command.y)
            is OutlineCommand.QuadraticTo -> {
                include(command.controlX, command.controlY)
                include(command.x, command.y)
            }
            is OutlineCommand.CubicTo -> {
                include(command.controlX1, command.controlY1)
                include(command.controlX2, command.controlY2)
                include(command.x, command.y)
            }
            OutlineCommand.Close -> Unit
        }
    }

    return if (hasPoint) {
        GlyphBounds(left = left, top = top, right = right, bottom = bottom)
    } else {
        null
    }
}

/**
 * Computes conservative bounds for the typed commands in this outline.
 *
 * @return Axis-aligned bounds, or null when no typed command carries coordinates.
 */
fun GlyphOutline.conservativeBounds(): GlyphBounds? = commands.conservativeBounds()

/**
 * Returns this bounds scaled by independent x and y factors.
 *
 * Negative factors are accepted and bounds are normalized after scaling.
 *
 * @param scaleX Factor applied to x coordinates.
 * @param scaleY Factor applied to y coordinates.
 * @return Scaled axis-aligned bounds.
 */
fun GlyphBounds.scaled(scaleX: Double, scaleY: Double = scaleX): GlyphBounds {
    require(scaleX.isFinite()) { "scaleX must be finite." }
    require(scaleY.isFinite()) { "scaleY must be finite." }

    val scaledLeft = left * scaleX
    val scaledRight = right * scaleX
    val scaledTop = top * scaleY
    val scaledBottom = bottom * scaleY
    return GlyphBounds(
        left = min(scaledLeft, scaledRight),
        top = min(scaledTop, scaledBottom),
        right = max(scaledLeft, scaledRight),
        bottom = max(scaledTop, scaledBottom),
    )
}

/**
 * Converts this bounds from font units to pixels.
 *
 * @param unitsPerEm Number of font units in one em.
 * @param pixelSize Requested pixel size for one em.
 * @return Bounds scaled by `pixelSize / unitsPerEm`.
 */
fun GlyphBounds.fontUnitsToPixels(unitsPerEm: Double, pixelSize: Double): GlyphBounds =
    scaled(fontUnitsToPixelsScale(unitsPerEm, pixelSize))

/**
 * Returns these typed outline commands scaled by independent x and y factors.
 *
 * @param scaleX Factor applied to x coordinates.
 * @param scaleY Factor applied to y coordinates.
 * @return Scaled outline command list.
 */
fun List<OutlineCommand>.scaled(scaleX: Double, scaleY: Double = scaleX): List<OutlineCommand> {
    require(scaleX.isFinite()) { "scaleX must be finite." }
    require(scaleY.isFinite()) { "scaleY must be finite." }

    fun x(value: Double): Double = value * scaleX
    fun y(value: Double): Double = value * scaleY

    return map { command ->
        when (command) {
            is OutlineCommand.MoveTo -> moveTo(x(command.x), y(command.y))
            is OutlineCommand.LineTo -> lineTo(x(command.x), y(command.y))
            is OutlineCommand.QuadraticTo -> quadraticTo(
                controlX = x(command.controlX),
                controlY = y(command.controlY),
                x = x(command.x),
                y = y(command.y),
            )
            is OutlineCommand.CubicTo -> cubicTo(
                controlX1 = x(command.controlX1),
                controlY1 = y(command.controlY1),
                controlX2 = x(command.controlX2),
                controlY2 = y(command.controlY2),
                x = x(command.x),
                y = y(command.y),
            )
            OutlineCommand.Close -> close()
        }
    }
}

/**
 * Converts these typed outline commands from font units to pixels.
 *
 * @param unitsPerEm Number of font units in one em.
 * @param pixelSize Requested pixel size for one em.
 * @return Commands scaled by `pixelSize / unitsPerEm`.
 */
fun List<OutlineCommand>.fontUnitsToPixels(unitsPerEm: Double, pixelSize: Double): List<OutlineCommand> =
    scaled(fontUnitsToPixelsScale(unitsPerEm, pixelSize))

/**
 * Returns this outline with typed commands scaled by independent x and y factors.
 *
 * Legacy opaque contours are preserved unchanged.
 *
 * @param scaleX Factor applied to x coordinates.
 * @param scaleY Factor applied to y coordinates.
 * @return Outline with scaled typed commands.
 */
fun GlyphOutline.scaled(scaleX: Double, scaleY: Double = scaleX): GlyphOutline =
    copy(commands = commands.scaled(scaleX, scaleY))

/**
 * Converts this outline's typed commands from font units to pixels.
 *
 * Legacy opaque contours are preserved unchanged.
 *
 * @param unitsPerEm Number of font units in one em.
 * @param pixelSize Requested pixel size for one em.
 * @return Outline with typed commands scaled by `pixelSize / unitsPerEm`.
 */
fun GlyphOutline.fontUnitsToPixels(unitsPerEm: Double, pixelSize: Double): GlyphOutline =
    scaled(fontUnitsToPixelsScale(unitsPerEm, pixelSize))

/**
 * Returns this outline command transformed by a TrueType composite component matrix.
 *
 * The transform is applied to every coordinate carried by the command. Close commands do not carry
 * coordinates and are returned as close commands unchanged.
 *
 * @param transform Composite affine transform to apply.
 * @return Transformed outline command.
 */
fun OutlineCommand.transformed(transform: TrueTypeCompositeTransform): OutlineCommand =
    when (this) {
        is OutlineCommand.MoveTo -> moveTo(
            transform.transformX(x, y),
            transform.transformY(x, y),
        )
        is OutlineCommand.LineTo -> lineTo(
            transform.transformX(x, y),
            transform.transformY(x, y),
        )
        is OutlineCommand.QuadraticTo -> quadraticTo(
            controlX = transform.transformX(controlX, controlY),
            controlY = transform.transformY(controlX, controlY),
            x = transform.transformX(x, y),
            y = transform.transformY(x, y),
        )
        is OutlineCommand.CubicTo -> cubicTo(
            controlX1 = transform.transformX(controlX1, controlY1),
            controlY1 = transform.transformY(controlX1, controlY1),
            controlX2 = transform.transformX(controlX2, controlY2),
            controlY2 = transform.transformY(controlX2, controlY2),
            x = transform.transformX(x, y),
            y = transform.transformY(x, y),
        )
        OutlineCommand.Close -> close()
    }

/**
 * Returns these outline commands transformed by a TrueType composite component matrix.
 *
 * @param transform Composite affine transform to apply to every coordinate-bearing command.
 * @return Transformed outline command list preserving command order.
 */
fun List<OutlineCommand>.transformed(transform: TrueTypeCompositeTransform): List<OutlineCommand> =
    map { command -> command.transformed(transform) }

private fun TrueTypeCompositeTransform.transformX(x: Double, y: Double): Double = xx * x + xy * y + dx

private fun TrueTypeCompositeTransform.transformY(x: Double, y: Double): Double = yx * x + yy * y + dy

/**
 * Computes the scalar used to convert font units to pixels.
 *
 * @param unitsPerEm Number of font units in one em. Must be positive and finite.
 * @param pixelSize Requested pixel size for one em. Must be finite.
 * @return Conversion factor `pixelSize / unitsPerEm`.
 */
fun fontUnitsToPixelsScale(unitsPerEm: Double, pixelSize: Double): Double {
    require(unitsPerEm.isFinite() && unitsPerEm > 0.0) { "unitsPerEm must be positive and finite." }
    require(pixelSize.isFinite()) { "pixelSize must be finite." }
    return pixelSize / unitsPerEm
}

/**
 * TrueType `loca` table offset encoding.
 */
enum class TrueTypeLocaFormat {
    /**
     * Short `loca` entries store glyph offsets divided by two as unsigned 16-bit values.
     */
    Short,

    /**
     * Long `loca` entries store glyph offsets as unsigned 32-bit values.
     */
    Long,
}

/**
 * Byte range for one glyph description inside the TrueType `glyf` table.
 *
 * @property start Inclusive byte offset in the `glyf` table.
 * @property endExclusive Exclusive byte offset in the `glyf` table.
 */
data class TrueTypeGlyphDataRange(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "glyph data range start must be non-negative." }
        require(endExclusive >= start) { "glyph data range endExclusive must be at least start." }
    }
}

/**
 * Parsed TrueType `loca` table offsets.
 *
 * The table contains `numGlyphs + 1` offsets. The final sentinel offset is needed to bound the
 * last glyph's byte range in the `glyf` table.
 *
 * @property offsets Monotonic glyph offsets in bytes.
 */
data class TrueTypeLocaTable(
    val offsets: List<Int>,
) {
    init {
        require(offsets.isNotEmpty()) { "loca offsets must include the final sentinel offset." }
        offsets.forEachIndexed { index, offset ->
            require(offset >= 0) { "loca offset $index must be non-negative." }
            if (index > 0) {
                require(offset >= offsets[index - 1]) {
                    "loca offsets must be monotonic at index $index: ${offsets[index - 1]} > $offset."
                }
            }
        }
    }

    /**
     * Returns the bounded `glyf` table range for a glyph id.
     *
     * @param glyphId Glyph id to look up.
     * @return Byte range for the glyph description. Empty ranges represent missing glyph data.
     */
    fun rangeForGlyph(glyphId: UInt): TrueTypeGlyphDataRange {
        val index = glyphId.toLong()
        require(index <= Int.MAX_VALUE) { "glyphId $glyphId does not fit Int indexing." }
        require(index >= 0 && index < offsets.lastIndex) {
            "glyphId $glyphId outside loca table glyph count ${offsets.size - 1}."
        }
        val start = offsets[index.toInt()]
        val endExclusive = offsets[index.toInt() + 1]
        return TrueTypeGlyphDataRange(start = start, endExclusive = endExclusive)
    }
}

/**
 * Bounded parser for TrueType `loca` table data.
 */
object TrueTypeLocaTableParser {
    /**
     * Parses a complete `loca` table for an explicit glyph count.
     *
     * @param data Raw `loca` table bytes.
     * @param format Offset encoding from `head.indexToLocFormat`.
     * @param numGlyphs Explicit glyph count from `maxp.numGlyphs`.
     * @return Parsed offset table with `numGlyphs + 1` entries.
     * @throws IllegalArgumentException when the table length, offset order, or offset range is invalid.
     */
    fun parse(
        data: ByteArray,
        format: TrueTypeLocaFormat,
        numGlyphs: Int,
    ): TrueTypeLocaTable {
        require(numGlyphs >= 0) { "numGlyphs must be non-negative." }
        val entrySize = when (format) {
            TrueTypeLocaFormat.Short -> 2
            TrueTypeLocaFormat.Long -> 4
        }
        val entryCount = numGlyphs + 1
        val expectedLength = entryCount * entrySize
        require(data.size == expectedLength) {
            "loca table length ${data.size} does not match expected $expectedLength bytes for " +
                "$numGlyphs glyphs in $format format."
        }

        val offsets = List(entryCount) { index ->
            val byteOffset = index * entrySize
            when (format) {
                TrueTypeLocaFormat.Short -> readUInt16(data, byteOffset) * 2
                TrueTypeLocaFormat.Long -> {
                    val offset = readUInt32(data, byteOffset)
                    require(offset <= Int.MAX_VALUE) {
                        "loca long offset $offset at index $index does not fit Int."
                    }
                    offset.toInt()
                }
            }
        }
        return TrueTypeLocaTable(offsets)
    }
}

/**
 * Shared TrueType `glyf` header fields.
 *
 * @property numberOfContours Non-negative for simple glyphs, negative for composite glyphs.
 * @property xMin Minimum x coordinate in font units.
 * @property yMin Minimum y coordinate in font units.
 * @property xMax Maximum x coordinate in font units.
 * @property yMax Maximum y coordinate in font units.
 */
data class TrueTypeGlyphHeader(
    val numberOfContours: Int,
    val xMin: Int,
    val yMin: Int,
    val xMax: Int,
    val yMax: Int,
)

/**
 * One decoded point from a TrueType simple glyph contour.
 *
 * @property x Absolute x coordinate in font units.
 * @property y Absolute y coordinate in font units.
 * @property onCurve Whether the point lies on the outline curve.
 * @property flags Expanded TrueType point flags with the repeat bit removed.
 */
data class TrueTypeGlyphPoint(
    val x: Int,
    val y: Int,
    val onCurve: Boolean,
    val flags: Int,
)

/**
 * Decoded contour from a TrueType simple glyph.
 *
 * @property endPointIndex Original global endpoint index from `endPtsOfContours`.
 * @property points Points belonging to this contour.
 */
data class TrueTypeGlyphContour(
    val endPointIndex: Int,
    val points: List<TrueTypeGlyphPoint>,
)

/**
 * Decoded arguments from a TrueType composite glyph component record.
 *
 * TrueType component records encode two arguments. When `ARGS_ARE_XY_VALUES` is set, the arguments
 * are signed x/y translation values in font units. When the flag is absent, the arguments are point
 * indices used for point matching. Point matching is preserved explicitly here so outline resolution
 * can fail with a precise unsupported-feature diagnostic instead of silently treating indices as
 * translations.
 */
sealed interface TrueTypeCompositeGlyphArgument {
    /**
     * Signed x/y component translation arguments.
     *
     * @property x X translation in font units.
     * @property y Y translation in font units.
     */
    data class XyValues(
        val x: Int,
        val y: Int,
    ) : TrueTypeCompositeGlyphArgument

    /**
     * Unsupported point-matching component arguments.
     *
     * @property compoundPointIndex Point index in the compound glyph.
     * @property componentPointIndex Point index in the component glyph.
     */
    data class PointMatching(
        val compoundPointIndex: Int,
        val componentPointIndex: Int,
    ) : TrueTypeCompositeGlyphArgument
}

/**
 * Affine transform decoded from one TrueType composite glyph component record.
 *
 * The matrix follows the TrueType component transform equation:
 * `x' = xx * x + xy * y + dx`, `y' = yx * x + yy * y + dy`. The default value is
 * the identity transform. Translation is populated only for `ARGS_ARE_XY_VALUES` components; point
 * matching components retain zero translation until a future point-alignment resolver exists.
 *
 * @property xx X contribution from the source x coordinate.
 * @property xy X contribution from the source y coordinate.
 * @property yx Y contribution from the source x coordinate.
 * @property yy Y contribution from the source y coordinate.
 * @property dx X translation in font units.
 * @property dy Y translation in font units.
 */
data class TrueTypeCompositeTransform(
    val xx: Double = 1.0,
    val xy: Double = 0.0,
    val yx: Double = 0.0,
    val yy: Double = 1.0,
    val dx: Double = 0.0,
    val dy: Double = 0.0,
) {
    init {
        require(xx.isFinite()) { "xx must be finite." }
        require(xy.isFinite()) { "xy must be finite." }
        require(yx.isFinite()) { "yx must be finite." }
        require(yy.isFinite()) { "yy must be finite." }
        require(dx.isFinite()) { "dx must be finite." }
        require(dy.isFinite()) { "dy must be finite." }
    }
}

/**
 * One decoded TrueType composite glyph component record.
 *
 * Component records identify another glyph, supply either translation or point-matching arguments,
 * and optionally carry one of the TrueType F2Dot14 scale encodings: uniform scale, independent x/y
 * scale, or a full two-by-two matrix. Raw flags are preserved so callers can inspect policy bits
 * such as `USE_MY_METRICS`, `OVERLAP_COMPOUND`, or offset-scaling hints without reparsing bytes.
 *
 * @property glyphId Glyph id of the referenced component glyph.
 * @property flags Raw 16-bit component flags.
 * @property arguments Decoded component arguments.
 * @property transform Affine transform derived from scale flags plus x/y translation arguments.
 */
data class TrueTypeCompositeGlyphComponent(
    val glyphId: UInt,
    val flags: Int,
    val arguments: TrueTypeCompositeGlyphArgument,
    val transform: TrueTypeCompositeTransform,
) {
    init {
        require(flags in 0..0xffff) { "composite component flags must be a 16-bit unsigned value." }
    }
}

/**
 * Parsed TrueType `glyf` table entry.
 */
sealed interface TrueTypeGlyph {
    /**
     * Glyph with an empty `loca` range.
     */
    data object Empty : TrueTypeGlyph

    /**
     * Simple TrueType glyph decoded without executing hinting instructions.
     *
     * @property header Shared glyph header.
     * @property endPointsOfContours Original `endPtsOfContours` values.
     * @property instructionLength Number of skipped TrueType instruction bytes.
     * @property contours Decoded contours and points.
     */
    data class Simple(
        val header: TrueTypeGlyphHeader,
        val endPointsOfContours: List<Int>,
        val instructionLength: Int,
        val contours: List<TrueTypeGlyphContour>,
    ) : TrueTypeGlyph

    /**
     * Composite TrueType glyph decoded into component records.
     *
     * Component records are parsed without executing TrueType instructions. The raw bytes following
     * the glyph header are retained for diagnostics and future policy checks; `componentData`
     * equality follows `ByteArray` referential equality rather than content equality.
     *
     * @property header Shared glyph header.
     * @property componentData Raw bytes following the composite glyph header.
     * @property components Parsed component records in draw order.
     * @property instructionLength Number of skipped composite instruction bytes.
     * @property diagnostic Human-readable note about unsupported TrueType VM execution.
     */
    data class Composite(
        val header: TrueTypeGlyphHeader,
        val componentData: ByteArray,
        val components: List<TrueTypeCompositeGlyphComponent> = emptyList(),
        val instructionLength: Int = 0,
        val diagnostic: String = "Composite TrueType glyph instructions are not executed.",
    ) : TrueTypeGlyph
}

/**
 * Bounded parser for TrueType `glyf` table glyph descriptions.
 */
object TrueTypeGlyfTableParser {
    /**
     * Parses one glyph using a `loca`-derived range.
     *
     * @param glyfTable Complete raw `glyf` table bytes.
     * @param loca Parsed `loca` table.
     * @param glyphId Glyph id to parse.
     * @return Parsed glyph model.
     * @throws IllegalArgumentException when the glyph range or glyph data is invalid.
     */
    fun parseGlyph(
        glyfTable: ByteArray,
        loca: TrueTypeLocaTable,
        glyphId: UInt,
    ): TrueTypeGlyph = parseGlyph(
        glyfTable = glyfTable,
        range = loca.rangeForGlyph(glyphId),
        glyphId = glyphId,
    )

    /**
     * Parses one glyph using an explicit bounded range.
     *
     * @param glyfTable Complete raw `glyf` table bytes.
     * @param range Byte range for the glyph description.
     * @param glyphId Glyph id used for diagnostics.
     * @return Parsed glyph model.
     * @throws IllegalArgumentException when the range, header, flags, coordinate data, or composite
     * component records are invalid.
     */
    fun parseGlyph(
        glyfTable: ByteArray,
        range: TrueTypeGlyphDataRange,
        glyphId: UInt,
    ): TrueTypeGlyph {
        require(range.endExclusive <= glyfTable.size) {
            "glyf glyph $glyphId range $range is outside glyf table length ${glyfTable.size}."
        }
        if (range.start == range.endExclusive) {
            return TrueTypeGlyph.Empty
        }

        val reader = GlyfReader(
            data = glyfTable,
            start = range.start,
            endExclusive = range.endExclusive,
            glyphId = glyphId,
        )
        val header = TrueTypeGlyphHeader(
            numberOfContours = reader.readInt16("header"),
            xMin = reader.readInt16("header"),
            yMin = reader.readInt16("header"),
            xMax = reader.readInt16("header"),
            yMax = reader.readInt16("header"),
        )

        return if (header.numberOfContours >= 0) {
            parseSimpleGlyph(reader, header)
        } else {
            val componentData = glyfTable.copyOfRange(reader.offset, range.endExclusive)
            parseCompositeGlyph(
                header = header,
                componentData = componentData,
                reader = reader,
            )
        }
    }

    private fun parseSimpleGlyph(
        reader: GlyfReader,
        header: TrueTypeGlyphHeader,
    ): TrueTypeGlyph.Simple {
        val endPointsOfContours = List(header.numberOfContours) {
            reader.readUInt16("endPtsOfContours")
        }
        endPointsOfContours.forEachIndexed { index, endPoint ->
            if (index > 0) {
                require(endPoint > endPointsOfContours[index - 1]) {
                    "glyf glyph ${reader.glyphId} endPtsOfContours must be monotonic."
                }
            }
        }

        val instructionLength = reader.readUInt16("instructionLength")
        reader.skip(instructionLength, "instructions")

        val pointCount = endPointsOfContours.lastOrNull()?.plus(1) ?: 0
        val flags = reader.readFlags(pointCount)
        val xs = reader.readCoordinates(flags, axis = CoordinateAxis.X)
        val ys = reader.readCoordinates(flags, axis = CoordinateAxis.Y)
        val points = flags.indices.map { index ->
            TrueTypeGlyphPoint(
                x = xs[index],
                y = ys[index],
                onCurve = flags[index] and FLAG_ON_CURVE != 0,
                flags = flags[index],
            )
        }

        var startIndex = 0
        val contours = endPointsOfContours.map { endPoint ->
            val contourPoints = points.subList(startIndex, endPoint + 1)
            startIndex = endPoint + 1
            TrueTypeGlyphContour(
                endPointIndex = endPoint,
                points = contourPoints,
            )
        }

        return TrueTypeGlyph.Simple(
            header = header,
            endPointsOfContours = endPointsOfContours,
            instructionLength = instructionLength,
            contours = contours,
        )
    }

    private fun parseCompositeGlyph(
        header: TrueTypeGlyphHeader,
        componentData: ByteArray,
        reader: GlyfReader,
    ): TrueTypeGlyph.Composite {
        val components = mutableListOf<TrueTypeCompositeGlyphComponent>()
        var flags: Int
        do {
            flags = reader.readUInt16("composite component flags")
            val componentGlyphId = reader.readUInt16("composite component glyph id").toUInt()
            val arguments = reader.readCompositeArguments(flags)
            val transform = reader.readCompositeTransform(flags, arguments)
            components += TrueTypeCompositeGlyphComponent(
                glyphId = componentGlyphId,
                flags = flags,
                arguments = arguments,
                transform = transform,
            )
        } while (flags and COMPOSITE_MORE_COMPONENTS != 0)

        val instructionLength = if (flags and COMPOSITE_WE_HAVE_INSTRUCTIONS != 0) {
            val length = reader.readUInt16("composite instructionLength")
            reader.skip(length, "composite instructions")
            length
        } else {
            0
        }

        return TrueTypeGlyph.Composite(
            header = header,
            componentData = componentData,
            components = components,
            instructionLength = instructionLength,
        )
    }

    private fun GlyfReader.readCompositeArguments(flags: Int): TrueTypeCompositeGlyphArgument {
        val argumentsAreWords = flags and COMPOSITE_ARG_1_AND_2_ARE_WORDS != 0
        val argumentsAreXyValues = flags and COMPOSITE_ARGS_ARE_XY_VALUES != 0
        return when {
            argumentsAreWords && argumentsAreXyValues -> TrueTypeCompositeGlyphArgument.XyValues(
                x = readInt16("composite xy argument"),
                y = readInt16("composite xy argument"),
            )
            argumentsAreWords -> TrueTypeCompositeGlyphArgument.PointMatching(
                compoundPointIndex = readUInt16("composite point argument"),
                componentPointIndex = readUInt16("composite point argument"),
            )
            argumentsAreXyValues -> TrueTypeCompositeGlyphArgument.XyValues(
                x = readInt8("composite xy argument"),
                y = readInt8("composite xy argument"),
            )
            else -> TrueTypeCompositeGlyphArgument.PointMatching(
                compoundPointIndex = readUInt8("composite point argument"),
                componentPointIndex = readUInt8("composite point argument"),
            )
        }
    }

    private fun GlyfReader.readCompositeTransform(
        flags: Int,
        arguments: TrueTypeCompositeGlyphArgument,
    ): TrueTypeCompositeTransform {
        val hasUniformScale = flags and COMPOSITE_WE_HAVE_A_SCALE != 0
        val hasXAndYScale = flags and COMPOSITE_WE_HAVE_AN_X_AND_Y_SCALE != 0
        val hasTwoByTwo = flags and COMPOSITE_WE_HAVE_A_TWO_BY_TWO != 0
        val scaleFlagCount = listOf(hasUniformScale, hasXAndYScale, hasTwoByTwo).count { it }
        require(scaleFlagCount <= 1) {
            "glyf glyph $glyphId composite component has mutually exclusive scale flags set."
        }

        var xx = 1.0
        var xy = 0.0
        var yx = 0.0
        var yy = 1.0
        when {
            hasUniformScale -> {
                val scale = readF2Dot14("composite uniform scale")
                xx = scale
                yy = scale
            }
            hasXAndYScale -> {
                xx = readF2Dot14("composite x scale")
                yy = readF2Dot14("composite y scale")
            }
            hasTwoByTwo -> {
                val xScale = readF2Dot14("composite two-by-two x scale")
                val scale01 = readF2Dot14("composite two-by-two scale01")
                val scale10 = readF2Dot14("composite two-by-two scale10")
                val yScale = readF2Dot14("composite two-by-two y scale")
                xx = xScale
                xy = scale10
                yx = scale01
                yy = yScale
            }
        }

        val dx: Double
        val dy: Double
        when (arguments) {
            is TrueTypeCompositeGlyphArgument.XyValues -> {
                val rawDx = arguments.x.toDouble()
                val rawDy = arguments.y.toDouble()
                val scaledOffset = flags and COMPOSITE_SCALED_COMPONENT_OFFSET != 0
                val unscaledOffset = flags and COMPOSITE_UNSCALED_COMPONENT_OFFSET != 0
                if (scaledOffset && !unscaledOffset) {
                    dx = xx * rawDx + xy * rawDy
                    dy = yx * rawDx + yy * rawDy
                } else {
                    dx = rawDx
                    dy = rawDy
                }
            }
            is TrueTypeCompositeGlyphArgument.PointMatching -> {
                dx = 0.0
                dy = 0.0
            }
        }
        return TrueTypeCompositeTransform(
            xx = xx,
            xy = xy,
            yx = yx,
            yy = yy,
            dx = dx,
            dy = dy,
        )
    }
}

/**
 * Converts a TrueType simple glyph into typed outline commands.
 *
 * Consecutive off-curve points are supported with the TrueType implicit-midpoint rule. Hinting
 * instructions are not executed, so coordinates are the raw design-space glyph coordinates.
 *
 * @param glyphId Glyph id for the returned outline.
 * @return Glyph outline containing typed commands and no legacy opaque contours.
 */
fun TrueTypeGlyph.Simple.toGlyphOutline(glyphId: UInt): GlyphOutline {
    return toGlyphOutline(glyphId = glyphId, variationDeltas = null)
}

private fun TrueTypeGlyph.Simple.toGlyphOutline(
    glyphId: UInt,
    variationDeltas: TrueTypeGlyphVariationDeltas?,
): GlyphOutline {
    val commands = buildList {
        var firstContourPointIndex = 0
        for (contour in contours) {
            addContourCommands(
                points = contour.points,
                firstPointIndex = firstContourPointIndex,
                variationDeltas = variationDeltas,
            )
            firstContourPointIndex += contour.points.size
        }
    }
    return GlyphOutline(glyphId = glyphId, commands = commands)
}

/**
 * Per-point variation deltas decoded from a bounded subset of a TrueType `gvar` glyph record.
 *
 * The arrays contain outline-point deltas only. Phantom point deltas, advance adjustments, and side
 * bearing adjustments are intentionally ignored by [ParsedTrueTypeGlyphScaler] for now.
 */
class TrueTypeGlyphVariationDeltas internal constructor(
    xDeltas: DoubleArray,
    yDeltas: DoubleArray,
) {
    private val xDeltas: DoubleArray = xDeltas.copyOf()
    private val yDeltas: DoubleArray = yDeltas.copyOf()

    init {
        require(xDeltas.size == yDeltas.size) { "gvar x and y delta counts must match." }
    }

    /**
     * Number of outline points covered by these deltas, excluding phantom points.
     */
    val pointCount: Int
        get() = xDeltas.size

    internal fun xDelta(pointIndex: Int): Double = xDeltas.getOrElse(pointIndex) { 0.0 }

    internal fun yDelta(pointIndex: Int): Double = yDeltas.getOrElse(pointIndex) { 0.0 }
}

internal data class TrueTypeGvarSimpleGlyphDeltaResult(
    val deltas: TrueTypeGlyphVariationDeltas? = null,
    val requiresIupInterpolation: Boolean = false,
)

/**
 * Bounded model for the TrueType `gvar` table subset used by the pure Kotlin font scaler.
 *
 * Supported data is deliberately narrow: glyph variation records with embedded peak tuples,
 * all-point shared/private point sets, packed x/y deltas, and optional intermediate regions. Shared
 * peak tuples are parsed and may be referenced, but partial point sets that require IUP
 * interpolation, composite-glyph deltas, phantom point metrics, `avar` remapping, and TrueType
 * instruction interaction are not implemented here. Malformed or unsupported per-glyph records
 * return no deltas instead of escaping with an unchecked bounds failure; malformed table headers and
 * offset directories fail eagerly with clear diagnostics from [parse].
 */
class TrueTypeGvarTable private constructor(
    private val data: ByteArray,
    val axisCount: Int,
    private val sharedTuples: List<DoubleArray>,
    private val glyphOffsets: IntArray,
    private val glyphDataStart: Int,
) {
    /**
     * Number of glyph variation records advertised by this table.
     */
    val glyphCount: Int
        get() = glyphOffsets.size - 1

    /**
     * Decodes scaled deltas for one simple glyph at normalized variation coordinates.
     *
     * @param glyphId Glyph id whose `gvar` record should be read.
     * @param pointCount Number of simple glyph outline points, excluding phantom points.
     * @param normalizedCoordinates Coordinates in the same axis order used to parse this table.
     * @return Outline-point deltas, or null when the glyph has no usable record for this bounded
     * implementation.
     */
    fun simpleGlyphDeltas(
        glyphId: UInt,
        pointCount: Int,
        normalizedCoordinates: List<Double>,
    ): TrueTypeGlyphVariationDeltas? =
        simpleGlyphDeltaResult(
            glyphId = glyphId,
            pointCount = pointCount,
            normalizedCoordinates = normalizedCoordinates,
        ).deltas

    internal fun simpleGlyphDeltaResult(
        glyphId: UInt,
        pointCount: Int,
        normalizedCoordinates: List<Double>,
    ): TrueTypeGvarSimpleGlyphDeltaResult {
        fun unavailable(): TrueTypeGvarSimpleGlyphDeltaResult = TrueTypeGvarSimpleGlyphDeltaResult()

        if (pointCount <= 0) {
            return unavailable()
        }
        val glyphIndex = glyphId.toLong()
        if (glyphIndex < 0 || glyphIndex + 1 >= glyphOffsets.size || glyphIndex > Int.MAX_VALUE) {
            return unavailable()
        }
        val maxPointCount = pointCount.checkedPlusPhantomCount() ?: return unavailable()
        val start = glyphDataStart.checkedPlus(glyphOffsets[glyphIndex.toInt()]) ?: return unavailable()
        val end = glyphDataStart.checkedPlus(glyphOffsets[glyphIndex.toInt() + 1]) ?: return unavailable()
        if (start == end) {
            return unavailable()
        }
        if (!data.fitsGvar(start, 4, end)) {
            return unavailable()
        }

        val tupleVariationCountField = data.readUInt16OrNull(start, end) ?: return unavailable()
        val tupleVariationCount = tupleVariationCountField and GVAR_TUPLE_COUNT_MASK
        if (tupleVariationCount <= 0) {
            return unavailable()
        }
        val offsetToData = data.readUInt16OrNull(start + 2, end) ?: return unavailable()
        val tupleDataStart = start.checkedPlus(offsetToData) ?: return unavailable()
        if (tupleDataStart < start || tupleDataStart > end) {
            return unavailable()
        }

        val headers = ArrayList<TrueTypeGvarTupleHeader>(tupleVariationCount)
        var headerOffset = start + 4
        repeat(tupleVariationCount) {
            if (!data.fitsGvar(headerOffset, 4, end)) {
                return unavailable()
            }
            val variationDataSize = data.readUInt16OrNull(headerOffset, end) ?: return unavailable()
            val tupleIndex = data.readUInt16OrNull(headerOffset + 2, end) ?: return unavailable()
            headerOffset += 4
            val peak = when {
                tupleIndex and GVAR_EMBEDDED_PEAK_TUPLE != 0 -> {
                    if (!data.fitsGvar(headerOffset, axisCount * 2, end)) {
                        return unavailable()
                    }
                    DoubleArray(axisCount) { axis ->
                        data.readF2Dot14OrNull(headerOffset + axis * 2, end) ?: return unavailable()
                    }.also {
                        headerOffset += axisCount * 2
                    }
                }
                else -> sharedTuples.getOrNull(tupleIndex and GVAR_TUPLE_INDEX_MASK) ?: return unavailable()
            }
            val startTuple: DoubleArray?
            val endTuple: DoubleArray?
            if (tupleIndex and GVAR_INTERMEDIATE_REGION != 0) {
                if (!data.fitsGvar(headerOffset, axisCount * 4, end)) {
                    return unavailable()
                }
                startTuple = DoubleArray(axisCount) { axis ->
                    data.readF2Dot14OrNull(headerOffset + axis * 2, end) ?: return unavailable()
                }
                headerOffset += axisCount * 2
                endTuple = DoubleArray(axisCount) { axis ->
                    data.readF2Dot14OrNull(headerOffset + axis * 2, end) ?: return unavailable()
                }
                headerOffset += axisCount * 2
            } else {
                startTuple = null
                endTuple = null
            }
            headers += TrueTypeGvarTupleHeader(
                variationDataSize = variationDataSize,
                tupleIndex = tupleIndex,
                peak = peak,
                startTuple = startTuple,
                endTuple = endTuple,
            )
        }
        if (tupleDataStart < headerOffset || tupleDataStart > end) {
            return unavailable()
        }

        var dataOffset = tupleDataStart
        val sharedPoints = if (tupleVariationCountField and GVAR_SHARED_POINT_NUMBERS != 0) {
            val points = readPackedGvarPoints(
                offset = dataOffset,
                maxPointCount = maxPointCount,
                limit = end,
            ) ?: return unavailable()
            dataOffset = points.nextOffset
            points.values
        } else {
            null
        }

        var requiresIupInterpolation = false
        val xDeltas = DoubleArray(pointCount)
        val yDeltas = DoubleArray(pointCount)
        for (header in headers) {
            val tupleEnd = dataOffset.checkedPlus(header.variationDataSize) ?: return unavailable()
            if (tupleEnd > end) {
                return unavailable()
            }
            var tupleDataOffset = dataOffset
            val privatePoints = if (header.tupleIndex and GVAR_PRIVATE_POINT_NUMBERS != 0) {
                val points = readPackedGvarPoints(
                    offset = tupleDataOffset,
                    maxPointCount = maxPointCount,
                    limit = tupleEnd,
                ) ?: return unavailable()
                tupleDataOffset = points.nextOffset
                points.values
            } else {
                null
            }
            val targetPoints = privatePoints ?: sharedPoints ?: IntArray(maxPointCount) { it }
            if (!targetPoints.isCompleteGvarPointSet(maxPointCount)) {
                requiresIupInterpolation = true
                dataOffset = tupleEnd
                continue
            }
            val tupleXDeltas = readPackedGvarDeltas(
                offset = tupleDataOffset,
                count = targetPoints.size,
                limit = tupleEnd,
            ) ?: return unavailable()
            tupleDataOffset = tupleXDeltas.nextOffset
            val tupleYDeltas = readPackedGvarDeltas(
                offset = tupleDataOffset,
                count = targetPoints.size,
                limit = tupleEnd,
            ) ?: return unavailable()
            if (tupleYDeltas.nextOffset > tupleEnd) {
                return unavailable()
            }

            val scalar = tupleScalar(
                normalizedCoordinates = normalizedCoordinates,
                peak = header.peak,
                startTuple = header.startTuple,
                endTuple = header.endTuple,
            )
            if (scalar != 0.0) {
                for (index in targetPoints.indices) {
                    val point = targetPoints[index]
                    if (point in 0 until pointCount) {
                        xDeltas[point] += tupleXDeltas.values[index] * scalar
                        yDeltas[point] += tupleYDeltas.values[index] * scalar
                    }
                }
            }
            dataOffset = tupleEnd
        }

        return TrueTypeGvarSimpleGlyphDeltaResult(
            deltas = TrueTypeGlyphVariationDeltas(xDeltas = xDeltas, yDeltas = yDeltas),
            requiresIupInterpolation = requiresIupInterpolation,
        )
    }

    private fun readPackedGvarPoints(
        offset: Int,
        maxPointCount: Int,
        limit: Int,
    ): PackedGvarInts? {
        var currentOffset = offset
        val first = data.readUInt8OrNull(currentOffset, limit) ?: return null
        currentOffset += 1
        if (first == 0) {
            return PackedGvarInts(IntArray(maxPointCount) { it }, currentOffset)
        }

        val pointCount = if (first and 0x80 != 0) {
            val second = data.readUInt8OrNull(currentOffset, limit) ?: return null
            currentOffset += 1
            ((first and 0x7f) shl 8) or second
        } else {
            first
        }
        if (pointCount < 0 || pointCount > maxPointCount) {
            return null
        }

        val points = IntArray(pointCount)
        var pointIndex = 0
        var point = 0
        while (pointIndex < pointCount) {
            val control = data.readUInt8OrNull(currentOffset, limit) ?: return null
            currentOffset += 1
            val wordDeltas = control and 0x80 != 0
            val runCount = (control and 0x7f) + 1
            if (pointIndex + runCount > pointCount) {
                return null
            }
            repeat(runCount) {
                val delta = if (wordDeltas) {
                    val value = data.readUInt16OrNull(currentOffset, limit) ?: return null
                    currentOffset += 2
                    value
                } else {
                    val value = data.readUInt8OrNull(currentOffset, limit) ?: return null
                    currentOffset += 1
                    value
                }
                point += delta
                if (point >= maxPointCount) {
                    return null
                }
                points[pointIndex] = point
                pointIndex += 1
            }
        }
        return PackedGvarInts(points, currentOffset)
    }

    private fun IntArray.isCompleteGvarPointSet(maxPointCount: Int): Boolean =
        size == maxPointCount && indices.all { index -> this[index] == index }

    private fun readPackedGvarDeltas(
        offset: Int,
        count: Int,
        limit: Int,
    ): PackedGvarInts? {
        val values = IntArray(count)
        var currentOffset = offset
        var index = 0
        while (index < count) {
            val control = data.readUInt8OrNull(currentOffset, limit) ?: return null
            currentOffset += 1
            val runCount = (control and 0x3f) + 1
            if (index + runCount > count) {
                return null
            }
            when {
                control and 0x80 != 0 -> repeat(runCount) {
                    values[index] = 0
                    index += 1
                }
                control and 0x40 != 0 -> repeat(runCount) {
                    values[index] = data.readInt16OrNull(currentOffset, limit) ?: return null
                    currentOffset += 2
                    index += 1
                }
                else -> repeat(runCount) {
                    values[index] = data.readInt8OrNull(currentOffset, limit) ?: return null
                    currentOffset += 1
                    index += 1
                }
            }
        }
        return PackedGvarInts(values, currentOffset)
    }

    private fun tupleScalar(
        normalizedCoordinates: List<Double>,
        peak: DoubleArray,
        startTuple: DoubleArray?,
        endTuple: DoubleArray?,
    ): Double {
        var scalar = 1.0
        for (axis in 0 until axisCount) {
            val coordinate = normalizedCoordinates.getOrElse(axis) { 0.0 }
            val peakValue = peak[axis]
            if (peakValue == 0.0) {
                continue
            }
            val axisScalar = if (startTuple != null && endTuple != null) {
                val start = startTuple[axis]
                val end = endTuple[axis]
                when {
                    coordinate < start || coordinate > end || start > peakValue || peakValue > end -> 0.0
                    coordinate == peakValue -> 1.0
                    coordinate < peakValue -> (coordinate - start) / (peakValue - start)
                    else -> (end - coordinate) / (end - peakValue)
                }
            } else {
                when {
                    coordinate == 0.0 -> 0.0
                    !sameVariationDirection(coordinate, peakValue) -> 0.0
                    abs(coordinate) > abs(peakValue) -> 0.0
                    else -> coordinate / peakValue
                }
            }
            scalar *= axisScalar
            if (scalar == 0.0) {
                return 0.0
            }
        }
        return scalar
    }

    companion object {
        /**
         * Parses a complete `gvar` table for a known axis and glyph count.
         *
         * @param data Raw `gvar` table bytes beginning at the table header.
         * @param axisCount Number of normalized axes used by tuple coordinates.
         * @param glyphCount Number of glyphs advertised by `maxp.numGlyphs`.
         * @return Parsed `gvar` model.
         * @throws IllegalArgumentException when the table header, shared tuples, offset directory,
         * or glyph data bounds are malformed or unsupported.
         */
        fun parse(
            data: ByteArray,
            axisCount: Int,
            glyphCount: Int,
        ): TrueTypeGvarTable {
            require(axisCount > 0) { "gvar axisCount must be positive." }
            require(glyphCount >= 0) { "gvar glyphCount must be non-negative." }
            val reader = TrueTypeGvarReader(data = data)
            reader.requireAvailable(offset = 0, byteCount = 20, section = "header")

            val majorVersion = reader.readUInt16(offset = 0, section = "header")
            val minorVersion = reader.readUInt16(offset = 2, section = "header")
            require(majorVersion == 1 && minorVersion == 0) {
                "gvar version must be 1.0, found $majorVersion.$minorVersion."
            }
            val parsedAxisCount = reader.readUInt16(offset = 4, section = "header")
            require(parsedAxisCount == axisCount) {
                "gvar axisCount $parsedAxisCount does not match expected $axisCount."
            }
            val sharedTupleCount = reader.readUInt16(offset = 6, section = "header")
            val sharedTupleOffset = reader.readUInt32AsInt(offset = 8, section = "header")
            val parsedGlyphCount = reader.readUInt16(offset = 12, section = "header")
            require(parsedGlyphCount == glyphCount) {
                "gvar glyphCount $parsedGlyphCount does not match expected $glyphCount."
            }
            val flags = reader.readUInt16(offset = 14, section = "header")
            val glyphDataOffset = reader.readUInt32AsInt(offset = 16, section = "header")

            val sharedTupleByteCount = checkedTableByteCount(
                count = sharedTupleCount.toLong(),
                itemSize = axisCount.toLong() * 2L,
                section = "shared tuples",
            )
            val sharedTuples = if (sharedTupleCount == 0) {
                emptyList()
            } else {
                reader.requireAvailable(
                    offset = sharedTupleOffset,
                    byteCount = sharedTupleByteCount,
                    section = "shared tuples",
                )
                List(sharedTupleCount) { tupleIndex ->
                    DoubleArray(axisCount) { axis ->
                        reader.readF2Dot14(
                            offset = sharedTupleOffset + tupleIndex * axisCount * 2 + axis * 2,
                            section = "shared tuples",
                        )
                    }
                }
            }

            val longOffsets = flags and GVAR_LONG_OFFSETS != 0
            val offsetEntrySize = if (longOffsets) 4 else 2
            val offsetByteCount = checkedTableByteCount(
                count = glyphCount.toLong() + 1L,
                itemSize = offsetEntrySize.toLong(),
                section = "glyph variation offsets",
            )
            val offsetsStart = 20
            reader.requireAvailable(
                offset = offsetsStart,
                byteCount = offsetByteCount,
                section = "glyph variation offsets",
            )
            val offsets = IntArray(glyphCount + 1) { index ->
                val offset = offsetsStart + index * offsetEntrySize
                if (longOffsets) {
                    reader.readUInt32AsInt(offset = offset, section = "glyph variation offsets")
                } else {
                    reader.readUInt16(offset = offset, section = "glyph variation offsets") * 2
                }
            }
            for (index in 0 until offsets.lastIndex) {
                require(offsets[index] <= offsets[index + 1]) {
                    "gvar glyph variation offsets must be monotonic at index $index: " +
                        "${offsets[index]} > ${offsets[index + 1]}."
                }
            }

            reader.requireAvailable(offset = glyphDataOffset, byteCount = 0, section = "glyph variation data")
            val finalGlyphDataLength = offsets.last()
            reader.requireAvailable(
                offset = glyphDataOffset,
                byteCount = finalGlyphDataLength,
                section = "glyph variation data",
            )

            return TrueTypeGvarTable(
                data = data,
                axisCount = axisCount,
                sharedTuples = sharedTuples,
                glyphOffsets = offsets,
                glyphDataStart = glyphDataOffset,
            )
        }
    }
}

/**
 * Bridge from parsed SFNT metric data and raw TrueType outline tables to [ParsedTrueTypeGlyphScaler].
 *
 * This factory is intentionally limited to data that has already been parsed or table-sliced by the
 * SFNT layer. It does not depend on the legacy renderer and does not perform any Skia, GPU, or
 * runtime-effect work. Construction fails eagerly when required SFNT fields are missing, when
 * `head.indexToLocFormat` is not one of the two TrueType encodings, when horizontal metrics do not
 * cover every glyph advertised by `maxp.numGlyphs`, or when `loca` offsets point outside `glyf`.
 */
object TrueTypeGlyphScalerFactory {
    /**
     * Creates a parsed TrueType glyph scaler from SFNT metrics plus raw `loca` and `glyf` tables.
     *
     * The SFNT [metrics] object must provide `maxp.numGlyphs`, `head.indexToLocFormat`,
     * `head.unitsPerEm`, and one [HorizontalGlyphMetric] for each glyph id from `0` through
     * `numGlyphs - 1`. The raw [locaTable] is parsed according to `indexToLocFormat`, converted to
     * [TrueTypeLocaFormat], and validated against [glyfTable]. Horizontal SFNT metrics are converted
     * to [TrueTypeGlyphHorizontalMetrics] without synthesizing missing records.
     *
     * Scaling can be supplied either as [scale], which is applied directly, or as [pixelSize], which
     * must be positive and finite and derives `pixelSize / unitsPerEm`. Passing neither leaves font
     * units unscaled. Passing both is rejected so callers do not accidentally double-scale outlines
     * and advances. Direct [scale] accepts any finite value, including negative values for explicit
     * coordinate transformations.
     *
     * @param metrics Parsed SFNT metrics from the font tables.
     * @param locaTable Complete raw TrueType `loca` table bytes.
     * @param glyfTable Complete raw TrueType `glyf` table bytes.
     * @param pixelSize Optional positive pixel size for one em; derives the scaler factor from `unitsPerEm`.
     * @param scale Optional direct scaler factor for callers that already computed one.
     * @return A [ParsedTrueTypeGlyphScaler] backed by the supplied raw `glyf` table and parsed `loca`.
     * @throws IllegalArgumentException when required SFNT fields are missing, metrics are incomplete,
     * table encodings are invalid, scaling inputs are invalid, or `loca` and `glyf` are incoherent.
     */
    fun create(
        metrics: MetricsTables,
        locaTable: ByteArray,
        glyfTable: ByteArray,
        gvarTable: ByteArray? = null,
        normalizedAxisOrder: List<String> = emptyList(),
        pixelSize: Double? = null,
        scale: Double? = null,
    ): ParsedTrueTypeGlyphScaler {
        require(pixelSize == null || scale == null) {
            "pixelSize and scale must not both be provided."
        }
        val numGlyphs = metrics.numGlyphs
            ?: throw IllegalArgumentException("numGlyphs is required to build a TrueType glyph scaler.")
        require(numGlyphs >= 0) { "numGlyphs must be non-negative." }
        val unitsPerEm = metrics.unitsPerEm
            ?: throw IllegalArgumentException("unitsPerEm is required to build a TrueType glyph scaler.")
        require(unitsPerEm > 0) { "unitsPerEm must be positive." }

        val locaFormat = when (val indexToLocFormat = metrics.indexToLocFormat) {
            null -> throw IllegalArgumentException("indexToLocFormat is required to build a TrueType glyph scaler.")
            0 -> TrueTypeLocaFormat.Short
            1 -> TrueTypeLocaFormat.Long
            else -> throw IllegalArgumentException("indexToLocFormat $indexToLocFormat must be 0 or 1.")
        }
        val loca = TrueTypeLocaTableParser.parse(
            data = locaTable,
            format = locaFormat,
            numGlyphs = numGlyphs,
        )
        require(loca.offsets.last() <= glyfTable.size) {
            "loca final offset ${loca.offsets.last()} exceeds glyf table length ${glyfTable.size}."
        }

        val horizontalMetrics = metrics.horizontalMetrics.toTrueTypeHorizontalMetrics(numGlyphs)
        val effectiveScale = when {
            scale != null -> {
                require(scale.isFinite()) { "scale must be finite." }
                scale
            }
            pixelSize != null -> {
                require(pixelSize.isFinite() && pixelSize > 0.0) {
                    "pixelSize must be positive and finite."
                }
                fontUnitsToPixelsScale(unitsPerEm.toDouble(), pixelSize)
            }
            else -> 1.0
        }

        return ParsedTrueTypeGlyphScaler(
            glyfTable = glyfTable,
            loca = loca,
            horizontalMetrics = horizontalMetrics,
            scale = effectiveScale,
            gvar = gvarTable?.let { table ->
                TrueTypeGvarTable.parse(
                    data = table,
                    axisCount = normalizedAxisOrder.size,
                    glyphCount = numGlyphs,
                )
            },
            normalizedAxisOrder = normalizedAxisOrder,
        )
    }
}

private fun List<HorizontalGlyphMetric>.toTrueTypeHorizontalMetrics(
    numGlyphs: Int,
): Map<UInt, TrueTypeGlyphHorizontalMetrics> {
    val byGlyphId = LinkedHashMap<UInt, TrueTypeGlyphHorizontalMetrics>()
    for (metric in this) {
        require(metric.glyphId >= 0) { "horizontal metric glyphId ${metric.glyphId} must be non-negative." }
        require(metric.glyphId < numGlyphs) {
            "horizontal metric glyphId ${metric.glyphId} is outside numGlyphs $numGlyphs."
        }
        val glyphId = metric.glyphId.toUInt()
        require(glyphId !in byGlyphId) { "horizontal metrics contain duplicate glyphId $glyphId." }
        byGlyphId[glyphId] = TrueTypeGlyphHorizontalMetrics(
            advanceX = metric.advanceWidth.toDouble(),
            leftSideBearing = metric.leftSideBearing.toDouble(),
        )
    }
    for (glyphId in 0 until numGlyphs) {
        require(glyphId.toUInt() in byGlyphId) {
            "horizontal metrics missing for glyphId $glyphId."
        }
    }
    return byGlyphId
}

/**
 * Pure Kotlin scaler for already sliced TrueType `glyf`, `loca`, and horizontal metrics data.
 *
 * Simple glyphs are converted to typed outline commands, empty glyphs return empty outlines, and
 * composite glyphs recursively resolve their component outlines through parsed component records.
 * When [gvar] is supplied, [position] values are treated as already-normalized coordinates in
 * [normalizedAxisOrder] and are applied only to simple glyph outline points before outline command
 * conversion. Composite glyph-specific variation records, phantom point metrics, `avar` remapping,
 * TrueType VM instructions, and point-matching component arguments are not implemented; unsupported
 * point matching fails explicitly. Metrics use caller-provided horizontal advances and bounds from
 * the glyph header and currently ignore variation deltas.
 *
 * @param glyfTable Complete raw `glyf` table bytes.
 * @param loca Parsed `loca` table with one sentinel offset.
 * @param horizontalMetrics Horizontal metrics keyed by glyph id.
 * @param scale Uniform factor applied to outlines, advances, and bounds.
 * @param gvar Optional parsed TrueType glyph variation table.
 * @param normalizedAxisOrder Axis tags defining the order of already-normalized [VariationPosition]
 * coordinates consumed by [gvar].
 */
class ParsedTrueTypeGlyphScaler(
    private val glyfTable: ByteArray,
    private val loca: TrueTypeLocaTable,
    private val horizontalMetrics: Map<UInt, TrueTypeGlyphHorizontalMetrics>,
    private val scale: Double = 1.0,
    private val gvar: TrueTypeGvarTable? = null,
    normalizedAxisOrder: List<String> = emptyList(),
) : GlyphScaler {
    private val normalizedAxisOrder: List<String> = normalizedAxisOrder.toList()
    private val normalizedAxisTags: Set<String> = this.normalizedAxisOrder.toSet()

    init {
        require(scale.isFinite()) { "scale must be finite." }
        this.normalizedAxisOrder.forEach { tag ->
            require(tag.length == 4) { "normalized gvar axis tag must contain exactly four characters." }
            require(tag.all { character -> character.code in 0x20..0x7e }) {
                "normalized gvar axis tag $tag must contain printable ASCII characters."
            }
        }
        require(normalizedAxisTags.size == this.normalizedAxisOrder.size) {
            "normalized gvar axis order must not contain duplicate tags."
        }
        if (gvar != null) {
            require(this.normalizedAxisOrder.size == gvar.axisCount) {
                "normalized gvar axis order size ${this.normalizedAxisOrder.size} " +
                    "does not match gvar axisCount ${gvar.axisCount}."
            }
        }
    }

    /**
     * Parses and scales a TrueType glyph outline.
     *
     * Empty glyphs return empty outlines. Simple glyphs emit typed outline commands directly.
     * Composite glyphs recursively resolve component outlines, apply component transforms in font
     * units, and then apply this scaler's final uniform [scale] once to the resolved outline.
     * Composite resolution is capped to prevent cyclic glyph references from overflowing the stack.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Already-normalized variation coordinates keyed by axis tag when [gvar] is supplied.
     * @return Scaled glyph outline for simple, empty, or supported composite glyphs.
     * @throws IllegalArgumentException when the glyph id is outside the `loca` table.
     * @throws UnsupportedOperationException when composite recursion exceeds the depth cap or a
     * composite component uses unsupported point-matching arguments.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        resolveGlyphOutline(
            glyphId = glyphId,
            depth = 0,
            normalizedCoordinates = normalizedCoordinates(position),
        ).scaled(scale)

    /**
     * Returns scaled horizontal metrics and glyph-header bounds for one TrueType glyph.
     *
     * Empty glyphs have zero bounds. Composite glyphs keep their header bounds but do not imply
     * outline support.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variation position. Glyph variation deltas and phantom-point metrics are ignored.
     * @return Scaled glyph metrics.
     * @throws IllegalArgumentException when the glyph id is outside `loca` or has no horizontal metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics {
        val range = loca.rangeForGlyph(glyphId)
        val metric = horizontalMetrics[glyphId]
            ?: throw IllegalArgumentException("horizontal metrics missing for glyphId $glyphId.")
        val glyph = TrueTypeGlyfTableParser.parseGlyph(
            glyfTable = glyfTable,
            range = range,
            glyphId = glyphId,
        )
        val bounds = when (glyph) {
            TrueTypeGlyph.Empty -> GlyphBounds(left = 0.0, top = 0.0, right = 0.0, bottom = 0.0)
            is TrueTypeGlyph.Simple -> glyph.header.toGlyphBounds().scaled(scale)
            is TrueTypeGlyph.Composite -> glyph.header.toGlyphBounds().scaled(scale)
        }
        return GlyphMetrics(
            advanceX = metric.advanceX * scale,
            advanceY = 0.0,
            bounds = bounds,
        )
    }

    /**
     * Produces deterministic current-state evidence for one scaled TrueType glyph.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Already-normalized variation position when [gvar] is present.
     * @return Canonical glyph evidence with outline, metrics, `loca`, variation, route, and
     * diagnostic facts.
     */
    fun scaledGlyphEvidence(
        glyphId: UInt,
        position: VariationPosition = VariationPosition(),
    ): ScaledTrueTypeGlyphEvidence =
        scaledGlyphEvidence(
            glyphId = glyphId,
            position = position,
            requestedPosition = position,
        )

    internal fun scaledGlyphEvidence(
        glyphId: UInt,
        position: VariationPosition,
        requestedPosition: VariationPosition,
        additionalDiagnostics: List<FontScalerDiagnostic> = emptyList(),
        includeNormalizedVariationPosition: Boolean = true,
    ): ScaledTrueTypeGlyphEvidence {
        val diagnostics = additionalDiagnostics.toMutableList()
        val range = loca.rangeForGlyph(glyphId)
        val variationPositionDiagnostic = positionValidationDiagnostic(glyphId, position)
        if (variationPositionDiagnostic != null) {
            diagnostics += variationPositionDiagnostic
        }
        val normalizedCoordinates = if (variationPositionDiagnostic == null) {
            normalizedCoordinates(position)
        } else {
            emptyList()
        }
        val normalizedVariationPosition = if (gvar != null && includeNormalizedVariationPosition) {
            normalizedAxisOrder.zip(normalizedCoordinates)
                .map { (tag, value) -> VariationCoordinateEvidence(tag = tag, value = value) }
                .sortedBy { coordinate -> coordinate.tag }
        } else {
            emptyList()
        }
        if (variationPositionDiagnostic == null) {
            diagnostics += gvarEvidenceDiagnostics(
                glyphId = glyphId,
                normalizedCoordinates = normalizedCoordinates,
            )
        }
        if (gvar == null && requestedPosition.axes.isNotEmpty()) {
            diagnostics += FontScalerDiagnostic(
                code = FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED,
                detail = "truetype.gvar-unavailable",
                operation = "variation",
                glyphId = glyphId,
                severity = "warning",
            )
        }
        if (gvar != null && normalizedCoordinates.any { coordinate -> coordinate != 0.0 }) {
            diagnostics += FontScalerDiagnostic(
                code = FontScalerDiagnosticCodes.METRICS_VARIATION_UNAVAILABLE,
                detail = "truetype.phantom-metrics-unavailable",
                operation = "metrics",
                glyphId = glyphId,
                severity = "warning",
            )
        }

        val outline = runCatching {
            resolveGlyphOutline(
                glyphId = glyphId,
                depth = 0,
                normalizedCoordinates = normalizedCoordinates,
            ).scaled(scale)
        }.getOrElse { error ->
            val diagnostic = error.toFontScalerDiagnosticOrNull(
                glyphId = glyphId,
                operation = "outline",
            )
            if (diagnostic != null) {
                diagnostics += diagnostic
                null
            } else {
                throw error
            }
        }
        val metrics = runCatching { metrics(glyphId = glyphId, position = position) }
            .getOrElse { error ->
                val diagnostic = error.toFontScalerDiagnosticOrNull(
                    glyphId = glyphId,
                    operation = "metrics",
                )
                if (diagnostic != null) {
                    diagnostics += diagnostic
                    null
                } else {
                    throw error
                }
            }
        val outlineCommands = outline?.commands.orEmpty().map { command -> command.toEvidenceLine() }
        val outlineCommandDump = outlineCommands.joinToString("\n")

        return ScaledTrueTypeGlyphEvidence(
            glyphId = glyphId,
            requestedVariationPosition = requestedPosition.toEvidenceCoordinates(),
            normalizedVariationPosition = normalizedVariationPosition,
            outlineCommands = outlineCommands,
            outlineCommandDump = outlineCommandDump,
            outlineCommandDumpSha256 = outlineCommandDump.scalerSha256Hex(),
            conservativeBounds = outline?.conservativeBounds(),
            metrics = metrics,
            locaRange = range.toEvidence(),
            diagnostics = diagnostics.sortedWith(fontScalerDiagnosticOrdering),
        )
    }

    private fun parseGlyph(glyphId: UInt): TrueTypeGlyph =
        TrueTypeGlyfTableParser.parseGlyph(
            glyfTable = glyfTable,
            loca = loca,
            glyphId = glyphId,
        )

    private fun resolveGlyphOutline(
        glyphId: UInt,
        depth: Int,
        normalizedCoordinates: List<Double>,
    ): GlyphOutline {
        if (depth > MAX_COMPOSITE_GLYPH_DEPTH) {
            throw FontScalerRefusalException(
                diagnostic = FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED,
                    detail = "truetype.composite-recursion-depth",
                    operation = "outline",
                    glyphId = glyphId,
                ),
                message = "composite glyph resolution depth cap $MAX_COMPOSITE_GLYPH_DEPTH exceeded at glyphId $glyphId.",
            )
        }
        return when (val glyph = parseGlyph(glyphId)) {
            TrueTypeGlyph.Empty -> GlyphOutline(glyphId = glyphId)
            is TrueTypeGlyph.Simple -> glyph.toGlyphOutline(
                glyphId = glyphId,
                variationDeltas = simpleGlyphVariationDeltas(
                    glyphId = glyphId,
                    glyph = glyph,
                    normalizedCoordinates = normalizedCoordinates,
                ),
            )
            is TrueTypeGlyph.Composite -> resolveCompositeGlyphOutline(
                glyphId = glyphId,
                glyph = glyph,
                depth = depth,
                normalizedCoordinates = normalizedCoordinates,
            )
        }
    }

    private fun resolveCompositeGlyphOutline(
        glyphId: UInt,
        glyph: TrueTypeGlyph.Composite,
        depth: Int,
        normalizedCoordinates: List<Double>,
    ): GlyphOutline {
        val commands = buildList {
            for (component in glyph.components) {
                when (val arguments = component.arguments) {
                    is TrueTypeCompositeGlyphArgument.XyValues -> Unit
                    is TrueTypeCompositeGlyphArgument.PointMatching -> {
                        throw FontScalerRefusalException(
                            diagnostic = FontScalerDiagnostic(
                                code = FontScalerDiagnosticCodes.OUTLINE_FORMAT_UNSUPPORTED,
                                detail = "truetype.composite-point-matching",
                                operation = "outline",
                                glyphId = glyphId,
                            ),
                            message = "composite point-matching arguments are not supported for glyphId $glyphId " +
                                "component glyphId ${component.glyphId}: compound point " +
                                "${arguments.compoundPointIndex}, component point " +
                                "${arguments.componentPointIndex}.",
                        )
                    }
                }
                val childOutline = resolveGlyphOutline(
                    glyphId = component.glyphId,
                    depth = depth + 1,
                    normalizedCoordinates = normalizedCoordinates,
                )
                addAll(childOutline.commands.transformed(component.transform))
            }
        }
        return GlyphOutline(glyphId = glyphId, commands = commands)
    }

    private fun simpleGlyphVariationDeltas(
        glyphId: UInt,
        glyph: TrueTypeGlyph.Simple,
        normalizedCoordinates: List<Double>,
    ): TrueTypeGlyphVariationDeltas? {
        val gvar = gvar ?: return null
        val pointCount = glyph.endPointsOfContours.lastOrNull()?.plus(1) ?: 0
        return gvar.simpleGlyphDeltas(
            glyphId = glyphId,
            pointCount = pointCount,
            normalizedCoordinates = normalizedCoordinates,
        )
    }

    private fun gvarEvidenceDiagnostics(
        glyphId: UInt,
        normalizedCoordinates: List<Double>,
    ): List<FontScalerDiagnostic> {
        val gvar = gvar ?: return emptyList()
        val glyph = parseGlyph(glyphId)
        if (glyph !is TrueTypeGlyph.Simple) {
            return emptyList()
        }
        val pointCount = glyph.endPointsOfContours.lastOrNull()?.plus(1) ?: 0
        val result = gvar.simpleGlyphDeltaResult(
            glyphId = glyphId,
            pointCount = pointCount,
            normalizedCoordinates = normalizedCoordinates,
        )
        return if (result.requiresIupInterpolation) {
            listOf(
                FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.VARIATION_DATA_MALFORMED,
                    detail = "truetype.gvar-iup-unavailable",
                    operation = "variation",
                    glyphId = glyphId,
                    severity = "warning",
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun normalizedCoordinates(position: VariationPosition): List<Double> {
        val gvar = gvar ?: return emptyList()
        position.axes.forEach { (tag, value) ->
            require(tag in normalizedAxisTags) {
                "variation position axis $tag is not declared in normalized gvar axis order."
            }
            require(value.isFinite()) { "variation position axis $tag must be finite." }
        }
        return normalizedAxisOrder.map { tag ->
            position.axes[tag] ?: 0.0
        }.also { coordinates ->
            require(coordinates.size == gvar.axisCount) {
                "normalized coordinate count ${coordinates.size} does not match gvar axisCount ${gvar.axisCount}."
            }
        }
    }

    private fun positionValidationDiagnostic(
        glyphId: UInt,
        position: VariationPosition,
    ): FontScalerDiagnostic? {
        if (gvar == null) {
            return null
        }
        position.axes.toSortedMap().forEach { (tag, value) ->
            if (tag !in normalizedAxisTags) {
                return FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED,
                    detail = "truetype.gvar-axis",
                    operation = "variation",
                    glyphId = glyphId,
                )
            }
            if (!value.isFinite()) {
                return FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.VARIATION_DATA_MALFORMED,
                    detail = "truetype.variation-position-non-finite",
                    operation = "variation",
                    glyphId = glyphId,
                )
            }
        }
        return null
    }
}

private fun TrueTypeGlyphHeader.toGlyphBounds(): GlyphBounds =
    GlyphBounds(
        left = xMin.toDouble(),
        top = yMin.toDouble(),
        right = xMax.toDouble(),
        bottom = yMax.toDouble(),
    )

private const val TRUE_TYPE_GLYF_SCALER_FAMILY = "truetype-glyf"
private const val TRUE_TYPE_GLYF_SCALER_ROUTE = "font.scaler.truetype-glyf"

private val fontScalerDiagnosticOrdering = compareBy<FontScalerDiagnostic>(
    { diagnostic -> diagnostic.operation },
    { diagnostic -> diagnostic.code },
    { diagnostic -> diagnostic.detail },
    { diagnostic -> diagnostic.glyphId.toString() },
    { diagnostic -> diagnostic.severity },
)

private fun VariationPosition.toEvidenceCoordinates(): List<VariationCoordinateEvidence> =
    axes.toSortedMap().mapNotNull { (tag, value) ->
        if (value.isFinite()) {
            VariationCoordinateEvidence(tag = tag, value = value)
        } else {
            null
        }
    }

private fun TrueTypeGlyphDataRange.toEvidence(): TrueTypeLocaRangeEvidence {
    val byteLength = endExclusive - start
    return TrueTypeLocaRangeEvidence(
        start = start,
        endExclusive = endExclusive,
        byteLength = byteLength,
        isEmpty = byteLength == 0,
    )
}

private fun List<VariationCoordinateEvidence>.isSortedByTag(): Boolean =
    zipWithNext().all { (left, right) -> left.tag <= right.tag }

private fun List<VariationCoordinateEvidence>.toCoordinateJson(): String =
    joinToString(prefix = "[", postfix = "]") { coordinate -> coordinate.toCanonicalJson() }

private fun List<String>.toJsonStringArray(): String =
    joinToString(prefix = "[", postfix = "]") { value -> scalerJsonString(value) }

private fun List<FontScalerDiagnostic>.toDiagnosticJson(): String =
    joinToString(prefix = "[", postfix = "]") { diagnostic -> diagnostic.toCanonicalJson() }

private fun GlyphBounds.toCanonicalJson(): String = buildString {
    append("{")
    append(scalerJsonString("left")).append(": ").append(left.toCanonicalScalerNumber()).append(", ")
    append(scalerJsonString("top")).append(": ").append(top.toCanonicalScalerNumber()).append(", ")
    append(scalerJsonString("right")).append(": ").append(right.toCanonicalScalerNumber()).append(", ")
    append(scalerJsonString("bottom")).append(": ").append(bottom.toCanonicalScalerNumber())
    append("}")
}

private fun GlyphMetrics.toCanonicalJson(): String = buildString {
    append("{")
    append(scalerJsonString("advanceX")).append(": ").append(advanceX.toCanonicalScalerNumber()).append(", ")
    append(scalerJsonString("advanceY")).append(": ").append(advanceY.toCanonicalScalerNumber()).append(", ")
    append(scalerJsonString("bounds")).append(": ").append(bounds.toCanonicalJson())
    append("}")
}

private fun OutlineCommand.toEvidenceLine(): String =
    when (this) {
        is OutlineCommand.MoveTo -> "M ${x.toCanonicalScalerNumber()} ${y.toCanonicalScalerNumber()}"
        is OutlineCommand.LineTo -> "L ${x.toCanonicalScalerNumber()} ${y.toCanonicalScalerNumber()}"
        is OutlineCommand.QuadraticTo -> "Q ${controlX.toCanonicalScalerNumber()} " +
            "${controlY.toCanonicalScalerNumber()} ${x.toCanonicalScalerNumber()} ${y.toCanonicalScalerNumber()}"
        is OutlineCommand.CubicTo -> "C ${controlX1.toCanonicalScalerNumber()} " +
            "${controlY1.toCanonicalScalerNumber()} ${controlX2.toCanonicalScalerNumber()} " +
            "${controlY2.toCanonicalScalerNumber()} ${x.toCanonicalScalerNumber()} ${y.toCanonicalScalerNumber()}"
        OutlineCommand.Close -> "Z"
    }

private fun Throwable.toFontScalerDiagnosticOrNull(
    glyphId: UInt,
    operation: String,
): FontScalerDiagnostic? =
    when (this) {
        is FontScalerRefusalException -> diagnostic.copy(operation = operation, glyphId = glyphId)
        else -> null
    }

private fun String.isStableToken(): Boolean =
    isNotEmpty() && all { character ->
        character in 'a'..'z' ||
            character in '0'..'9' ||
            character == '.' ||
            character == '-' ||
            character == '_'
    }

private fun Double.toCanonicalScalerNumber(): String {
    require(isFinite()) { "scaler evidence number must be finite." }
    return toString()
}

private fun scalerJsonString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

private fun String.scalerSha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

private const val FLAG_ON_CURVE = 0x01
private const val FLAG_X_SHORT_VECTOR = 0x02
private const val FLAG_Y_SHORT_VECTOR = 0x04
private const val FLAG_REPEAT = 0x08
private const val FLAG_X_SAME_OR_POSITIVE = 0x10
private const val FLAG_Y_SAME_OR_POSITIVE = 0x20

private const val COMPOSITE_ARG_1_AND_2_ARE_WORDS = 0x0001
private const val COMPOSITE_ARGS_ARE_XY_VALUES = 0x0002
private const val COMPOSITE_WE_HAVE_A_SCALE = 0x0008
private const val COMPOSITE_MORE_COMPONENTS = 0x0020
private const val COMPOSITE_WE_HAVE_AN_X_AND_Y_SCALE = 0x0040
private const val COMPOSITE_WE_HAVE_A_TWO_BY_TWO = 0x0080
private const val COMPOSITE_WE_HAVE_INSTRUCTIONS = 0x0100
private const val COMPOSITE_SCALED_COMPONENT_OFFSET = 0x0800
private const val COMPOSITE_UNSCALED_COMPONENT_OFFSET = 0x1000
private const val MAX_COMPOSITE_GLYPH_DEPTH = 32

private const val GVAR_LONG_OFFSETS = 0x0001
private const val GVAR_SHARED_POINT_NUMBERS = 0x8000
private const val GVAR_TUPLE_COUNT_MASK = 0x0fff
private const val GVAR_EMBEDDED_PEAK_TUPLE = 0x8000
private const val GVAR_INTERMEDIATE_REGION = 0x4000
private const val GVAR_PRIVATE_POINT_NUMBERS = 0x2000
private const val GVAR_TUPLE_INDEX_MASK = 0x0fff
private const val PHANTOM_POINT_COUNT = 4

private enum class CoordinateAxis {
    X,
    Y,
}

private class GlyfReader(
    private val data: ByteArray,
    private val start: Int,
    private val endExclusive: Int,
    val glyphId: UInt,
) {
    var offset: Int = start
        private set

    fun readInt16(section: String): Int {
        requireAvailable(byteCount = 2, section = section)
        val value = readInt16(data, offset)
        offset += 2
        return value
    }

    fun readUInt16(section: String): Int {
        requireAvailable(byteCount = 2, section = section)
        val value = readUInt16(data, offset)
        offset += 2
        return value
    }

    fun readUInt8(section: String): Int {
        requireAvailable(byteCount = 1, section = section)
        return data[offset++].toInt() and 0xff
    }

    fun readInt8(section: String): Int {
        requireAvailable(byteCount = 1, section = section)
        val value = data[offset++].toInt() and 0xff
        return if (value and 0x80 != 0) value - 0x100 else value
    }

    fun skip(byteCount: Int, section: String) {
        requireAvailable(byteCount = byteCount, section = section)
        offset += byteCount
    }

    fun readFlags(pointCount: Int): List<Int> {
        val flags = mutableListOf<Int>()
        while (flags.size < pointCount) {
            val rawFlag = readUInt8("flag data")
            val effectiveFlag = rawFlag and FLAG_REPEAT.inv() and 0xff
            val repeatCount = if (rawFlag and FLAG_REPEAT != 0) {
                readUInt8("flag repeat data")
            } else {
                0
            }
            require(flags.size + repeatCount + 1 <= pointCount) {
                "glyf glyph $glyphId flag repeat exceeds point count $pointCount."
            }
            repeat(repeatCount + 1) {
                flags += effectiveFlag
            }
        }
        return flags
    }

    fun readCoordinates(flags: List<Int>, axis: CoordinateAxis): List<Int> {
        var current = 0
        return flags.map { flag ->
            val isShort: Boolean
            val sameOrPositive: Boolean
            val section: String
            when (axis) {
                CoordinateAxis.X -> {
                    isShort = flag and FLAG_X_SHORT_VECTOR != 0
                    sameOrPositive = flag and FLAG_X_SAME_OR_POSITIVE != 0
                    section = "x coordinate data"
                }
                CoordinateAxis.Y -> {
                    isShort = flag and FLAG_Y_SHORT_VECTOR != 0
                    sameOrPositive = flag and FLAG_Y_SAME_OR_POSITIVE != 0
                    section = "y coordinate data"
                }
            }

            val delta = if (isShort) {
                val magnitude = readUInt8(section)
                if (sameOrPositive) magnitude else -magnitude
            } else if (sameOrPositive) {
                0
            } else {
                readInt16(section)
            }
            current += delta
            current
        }
    }

    private fun requireAvailable(byteCount: Int, section: String) {
        require(byteCount >= 0) { "byteCount must be non-negative." }
        require(offset + byteCount <= endExclusive) {
            "glyf glyph $glyphId $section is truncated: need $byteCount bytes at " +
                "glyph-relative offset ${offset - start}, range length ${endExclusive - start}."
        }
    }
}

private fun MutableList<OutlineCommand>.addContourCommands(
    points: List<TrueTypeGlyphPoint>,
    firstPointIndex: Int,
    variationDeltas: TrueTypeGlyphVariationDeltas?,
) {
    if (points.isEmpty()) {
        return
    }

    val outlinePoints = points.mapIndexed { contourIndex, point ->
        point.toOutlinePoint(
            pointIndex = firstPointIndex + contourIndex,
            variationDeltas = variationDeltas,
        )
    }
    val first = outlinePoints.first()
    val last = outlinePoints.last()
    val startPoint = when {
        first.onCurve -> first
        last.onCurve -> last
        else -> midpoint(last, first)
    }
    add(moveTo(startPoint.x, startPoint.y))

    var currentPoint = startPoint
    var pendingOffCurve: OutlinePoint? = null
    val pointsToVisit = when {
        first.onCurve -> outlinePoints.drop(1)
        last.onCurve -> outlinePoints.dropLast(1)
        else -> outlinePoints
    }

    for (point in pointsToVisit) {
        if (point.onCurve) {
            val control = pendingOffCurve
            if (control != null) {
                add(
                    quadraticTo(
                        controlX = control.x,
                        controlY = control.y,
                        x = point.x,
                        y = point.y,
                    ),
                )
            } else if (!sameCoordinates(currentPoint, point)) {
                add(lineTo(point.x, point.y))
            }
            pendingOffCurve = null
            currentPoint = point
        } else {
            val previousOffCurve = pendingOffCurve
            if (previousOffCurve != null) {
                val implicitPoint = midpoint(previousOffCurve, point)
                add(
                    quadraticTo(
                        controlX = previousOffCurve.x,
                        controlY = previousOffCurve.y,
                        x = implicitPoint.x,
                        y = implicitPoint.y,
                    ),
                )
                currentPoint = implicitPoint
            }
            pendingOffCurve = point
        }
    }

    val control = pendingOffCurve
    if (control != null) {
        add(
            quadraticTo(
                controlX = control.x,
                controlY = control.y,
                x = startPoint.x,
                y = startPoint.y,
            ),
        )
    }
    add(close())
}

private data class OutlinePoint(
    val x: Double,
    val y: Double,
    val onCurve: Boolean,
)

private fun TrueTypeGlyphPoint.toOutlinePoint(
    pointIndex: Int,
    variationDeltas: TrueTypeGlyphVariationDeltas?,
): OutlinePoint = OutlinePoint(
    x = x.toDouble() + (variationDeltas?.xDelta(pointIndex) ?: 0.0),
    y = y.toDouble() + (variationDeltas?.yDelta(pointIndex) ?: 0.0),
    onCurve = onCurve,
)

private fun midpoint(
    a: OutlinePoint,
    b: OutlinePoint,
): OutlinePoint = OutlinePoint(
    x = (a.x + b.x) / 2.0,
    y = (a.y + b.y) / 2.0,
    onCurve = true,
)

private fun sameCoordinates(
    a: OutlinePoint,
    b: OutlinePoint,
): Boolean = a.x == b.x && a.y == b.y

private fun GlyfReader.readF2Dot14(section: String): Double = readInt16(section) / 16384.0

private fun readUInt16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xff) shl 8) or
        (data[offset + 1].toInt() and 0xff)

private fun readInt16(data: ByteArray, offset: Int): Int {
    val value = readUInt16(data, offset)
    return if (value and 0x8000 != 0) value - 0x10000 else value
}

private fun readUInt32(data: ByteArray, offset: Int): Long =
    ((data[offset].toLong() and 0xff) shl 24) or
        ((data[offset + 1].toLong() and 0xff) shl 16) or
        ((data[offset + 2].toLong() and 0xff) shl 8) or
        (data[offset + 3].toLong() and 0xff)

private class TrueTypeGvarReader(
    private val data: ByteArray,
) {
    fun readUInt16(offset: Int, section: String): Int {
        requireAvailable(offset = offset, byteCount = 2, section = section)
        return readUInt16(data, offset)
    }

    fun readUInt32AsInt(offset: Int, section: String): Int {
        requireAvailable(offset = offset, byteCount = 4, section = section)
        val value = readUInt32(data, offset)
        require(value <= Int.MAX_VALUE) { "gvar $section UInt32 value $value does not fit Int." }
        return value.toInt()
    }

    fun readF2Dot14(offset: Int, section: String): Double {
        requireAvailable(offset = offset, byteCount = 2, section = section)
        return readInt16(data, offset) / 16384.0
    }

    fun requireAvailable(offset: Int, byteCount: Int, section: String) {
        require(offset >= 0) { "gvar $section offset must be non-negative." }
        require(byteCount >= 0) { "gvar $section byteCount must be non-negative." }
        require(offset <= data.size && byteCount <= data.size - offset) {
            "gvar $section is truncated: need $byteCount bytes at offset $offset, " +
                "table length ${data.size}."
        }
    }
}

private fun ByteArray.readUInt8OrNull(offset: Int, limit: Int): Int? {
    if (!fitsGvar(offset = offset, byteCount = 1, limit = limit)) {
        return null
    }
    return this[offset].toInt() and 0xff
}

private fun ByteArray.readInt8OrNull(offset: Int, limit: Int): Int? {
    val value = readUInt8OrNull(offset = offset, limit = limit) ?: return null
    return if (value and 0x80 != 0) value - 0x100 else value
}

private fun ByteArray.readUInt16OrNull(offset: Int, limit: Int): Int? {
    if (!fitsGvar(offset = offset, byteCount = 2, limit = limit)) {
        return null
    }
    return readUInt16(this, offset)
}

private fun ByteArray.readInt16OrNull(offset: Int, limit: Int): Int? {
    if (!fitsGvar(offset = offset, byteCount = 2, limit = limit)) {
        return null
    }
    return readInt16(this, offset)
}

private fun ByteArray.readF2Dot14OrNull(offset: Int, limit: Int): Double? {
    if (!fitsGvar(offset = offset, byteCount = 2, limit = limit)) {
        return null
    }
    return readInt16(this, offset) / 16384.0
}

private fun ByteArray.fitsGvar(offset: Int, byteCount: Int, limit: Int): Boolean =
    offset >= 0 &&
        byteCount >= 0 &&
        limit in 0..size &&
        offset <= limit &&
        byteCount <= limit - offset

private fun Int.checkedPlus(other: Int): Int? {
    val sum = toLong() + other.toLong()
    return if (sum in 0..Int.MAX_VALUE.toLong()) sum.toInt() else null
}

private fun Int.checkedPlusPhantomCount(): Int? = checkedPlus(PHANTOM_POINT_COUNT)

private fun checkedTableByteCount(count: Long, itemSize: Long, section: String): Int {
    require(count >= 0) { "gvar $section count must be non-negative." }
    require(itemSize >= 0) { "gvar $section item size must be non-negative." }
    val byteCount = count * itemSize
    require(itemSize == 0L || byteCount / itemSize == count) {
        "gvar $section byte count overflows Int."
    }
    require(byteCount <= Int.MAX_VALUE) { "gvar $section byte count $byteCount does not fit Int." }
    return byteCount.toInt()
}

private fun sameVariationDirection(coordinate: Double, peakValue: Double): Boolean =
    (coordinate < 0.0 && peakValue < 0.0) || (coordinate > 0.0 && peakValue > 0.0)

private data class TrueTypeGvarTupleHeader(
    val variationDataSize: Int,
    val tupleIndex: Int,
    val peak: DoubleArray,
    val startTuple: DoubleArray?,
    val endTuple: DoubleArray?,
)

private data class PackedGvarInts(
    val values: IntArray,
    val nextOffset: Int,
)

/**
 * TrueType `glyf` table scaler.
 *
 * @property face Parsed OpenType face data containing TrueType outline tables.
 */
class TrueTypeGlyfScaler(
    private val face: OpenTypeFaceData,
) : GlyphScaler {
    private val parsedScaler: ParsedTrueTypeGlyphScaler by lazy {
        val gvarTable = face.rawTableBytesOrNull("gvar")
        val normalizedAxisOrder = if (gvarTable != null) {
            require(face.variations.axes.isNotEmpty()) {
                "fvar variation axes are required to apply gvar for ${face.id.value}."
            }
            face.variations.axes.map { axis -> axis.tag.text }
        } else {
            emptyList()
        }

        TrueTypeGlyphScalerFactory.create(
            metrics = face.metrics,
            locaTable = face.requiredRawTableBytes("loca"),
            glyfTable = face.requiredRawTableBytes("glyf"),
            gvarTable = gvarTable,
            normalizedAxisOrder = normalizedAxisOrder,
        )
    }

    private val variationNormalizer: VariationNormalizer? by lazy {
        if (face.rawTables.containsKey(SFNTTableTag("gvar"))) {
            BoundedVariationNormalizer(
                face.variations.axes.map { axis ->
                    VariationAxis(
                        tag = axis.tag.text,
                        minimum = axis.minimum.value,
                        defaultValue = axis.defaultValue.value,
                        maximum = axis.maximum.value,
                    )
                },
            )
        } else {
            null
        }
    }

    /**
     * Produces a TrueType glyph outline.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled TrueType glyph outline.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        parsedScaler.outline(
            glyphId = glyphId,
            position = position.normalizedForGvar(),
        )

    /**
     * Produces TrueType glyph metrics.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled TrueType glyph metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics =
        parsedScaler.metrics(
            glyphId = glyphId,
            position = position.normalizedForGvar(),
        )

    /**
     * Produces deterministic current-state evidence for one TrueType `glyf` glyph.
     *
     * Requested variation coordinates are recorded in user-space coordinates. When `gvar` and
     * `fvar` data are available, normalized coordinates are recorded separately in stable axis order.
     */
    fun scaledGlyphEvidence(
        glyphId: UInt,
        position: VariationPosition = VariationPosition(),
    ): ScaledTrueTypeGlyphEvidence {
        val normalizationDiagnostics = position.normalizationDiagnostics(glyphId)
        val normalizedPosition = if (normalizationDiagnostics.isEmpty()) {
            position.normalizedForGvar()
        } else {
            VariationPosition()
        }
        return parsedScaler.scaledGlyphEvidence(
            glyphId = glyphId,
            position = normalizedPosition,
            requestedPosition = position,
            additionalDiagnostics = normalizationDiagnostics + avarDiagnostics(glyphId),
            includeNormalizedVariationPosition = normalizationDiagnostics.isEmpty(),
        )
    }

    private fun VariationPosition.normalizedForGvar(): VariationPosition {
        val normalizer = variationNormalizer ?: return this
        return VariationPosition(axes = normalizer.normalize(this))
    }

    private fun VariationPosition.normalizationDiagnostics(glyphId: UInt): List<FontScalerDiagnostic> {
        if (variationNormalizer == null) {
            return emptyList()
        }
        val supportedAxisTags = face.variations.axes.map { axis -> axis.tag.text }.toSet()
        return axes.toSortedMap().mapNotNull { (tag, value) ->
            when {
                tag !in supportedAxisTags -> FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED,
                    detail = "truetype.gvar-axis",
                    operation = "variation",
                    glyphId = glyphId,
                )
                !value.isFinite() -> FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.VARIATION_DATA_MALFORMED,
                    detail = "truetype.variation-position-non-finite",
                    operation = "variation",
                    glyphId = glyphId,
                )
                else -> null
            }
        }
    }

    private fun avarDiagnostics(glyphId: UInt): List<FontScalerDiagnostic> =
        if (face.variations.axisSegmentMaps.isNotEmpty()) {
            listOf(
                FontScalerDiagnostic(
                    code = FontScalerDiagnosticCodes.VARIATION_AXIS_UNSUPPORTED,
                    detail = "truetype.avar-unapplied",
                    operation = "variation",
                    glyphId = glyphId,
                    severity = "warning",
                ),
            )
        } else {
            emptyList()
        }
}

private fun OpenTypeFaceData.requiredRawTableBytes(tag: String): ByteArray =
    rawTableBytesOrNull(tag)
        ?: throw IllegalArgumentException(
            "TrueType glyf scaler for ${id.value} requires raw `$tag` table bytes.",
        )

private fun OpenTypeFaceData.rawTableBytesOrNull(tag: String): ByteArray? =
    rawTables[SFNTTableTag(tag)]?.toRawSfntTableBytes(tag)

private fun List<Int>.toRawSfntTableBytes(tag: String): ByteArray =
    mapIndexed { index, value ->
        require(value in 0..0xff) {
            "raw `$tag` table byte at index $index must be in 0..255, found $value."
        }
        value.toByte()
    }.toByteArray()

/**
 * Compact Font Format `CFF ` scaler.
 *
 * @property face Parsed OpenType face data containing CFF outlines.
 */
class CFFScaler(
    private val face: OpenTypeFaceData,
) : GlyphScaler {
    /**
     * Produces a CFF glyph outline.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variation position for synthetic or blended metrics when applicable.
     * @return Scaled CFF glyph outline.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        unsupportedCFFGlyph("CFF", "outline", face, glyphId, position)

    /**
     * Produces CFF glyph metrics.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variation position for synthetic or blended metrics when applicable.
     * @return Scaled CFF glyph metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics =
        unsupportedCFFGlyph("CFF", "metrics", face, glyphId, position)
}

/**
 * Compact Font Format 2 scaler for variable CFF outlines.
 *
 * @property face Parsed OpenType face data containing CFF2 outlines.
 */
class CFF2Scaler(
    private val face: OpenTypeFaceData,
) : GlyphScaler {
    /**
     * Produces a CFF2 glyph outline.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled CFF2 glyph outline.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        unsupportedCFFGlyph("CFF2", "outline", face, glyphId, position)

    /**
     * Produces CFF2 glyph metrics.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled CFF2 glyph metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics =
        unsupportedCFFGlyph("CFF2", "metrics", face, glyphId, position)
}

private fun unsupportedCFFGlyph(
    format: String,
    operation: String,
    face: OpenTypeFaceData,
    glyphId: UInt,
    position: VariationPosition,
): Nothing =
    throw UnsupportedOperationException(
        "$format $operation for ${face.id.value} glyphId $glyphId requires a pure Kotlin " +
            "Type 2 charstring interpreter before CFF/CFF2 glyph scaling can be claimed. " +
            "positionAxes=${position.axes.keys.sorted().joinToString(",")}",
    )

/**
 * Interpreter boundary for CFF and CFF2 charstrings.
 */
interface CFFCharStringInterpreter {
    /**
     * Interprets a raw charstring into an opaque outline command list.
     *
     * @param charString Raw Type 2 or CFF2 charstring bytes.
     * @param position Variation position used by CFF2 blend operators.
     * @return Opaque outline commands until the geometry command model is finalized.
     */
    fun interpret(charString: ByteArray, position: VariationPosition = VariationPosition()): List<String>
}

/**
 * User-space position in a variable font design space.
 *
 * @property axes Axis values keyed by four-character OpenType axis tags.
 */
data class VariationPosition(
    val axes: Map<String, Double> = emptyMap(),
)

/**
 * User-space bounds for one OpenType variable-font design axis.
 *
 * The values correspond to the design coordinates exposed by an `fvar` axis record before glyph
 * variation tuple normalization. [minimum] and [maximum] define the legal request range,
 * [defaultValue] is the coordinate used when callers omit the axis, and normalized coordinates map
 * that default to `0.0`.
 *
 * @property tag Four-character OpenType axis tag such as `wght`, `wdth`, or `opsz`.
 * @property minimum Smallest supported user-space design coordinate for this axis.
 * @property defaultValue User-space design coordinate selected when no position is requested.
 * @property maximum Largest supported user-space design coordinate for this axis.
 * @throws IllegalArgumentException when the tag is not a printable four-character OpenType tag, a
 * coordinate is not finite, or the bounds do not satisfy `minimum <= defaultValue <= maximum`.
 */
data class VariationAxis(
    val tag: String,
    val minimum: Double,
    val defaultValue: Double,
    val maximum: Double,
) {
    init {
        require(tag.length == 4) { "variation axis tag must contain exactly four characters." }
        require(tag.all { character -> character.code in 0x20..0x7e }) {
            "variation axis tag $tag must contain printable ASCII characters."
        }
        require(minimum.isFinite()) { "variation axis $tag minimum must be finite." }
        require(defaultValue.isFinite()) { "variation axis $tag defaultValue must be finite." }
        require(maximum.isFinite()) { "variation axis $tag maximum must be finite." }
        require(minimum <= defaultValue) {
            "variation axis $tag minimum $minimum must be <= defaultValue $defaultValue."
        }
        require(defaultValue <= maximum) {
            "variation axis $tag defaultValue $defaultValue must be <= maximum $maximum."
        }
    }
}

/**
 * Normalizes user-space variation positions into the scalar domain expected by glyph variation tables.
 */
interface VariationNormalizer {
    /**
     * Normalizes a variation position.
     *
     * @param position User-space variation position.
     * @return Normalized axis values keyed by axis tag.
     * @throws IllegalArgumentException when [position] cannot be normalized by this normalizer.
     */
    fun normalize(position: VariationPosition): Map<String, Double>
}

/**
 * Bounded OpenType variation normalizer for a fixed set of design axes.
 *
 * The normalizer clamps each requested user-space coordinate to its declared [VariationAxis] range
 * and converts it to the standard glyph-variation scalar interval: coordinates below the default
 * map linearly to `[-1.0, 0.0]`, coordinates above the default map linearly to `[0.0, 1.0]`, and
 * the default maps to `0.0`. Axes omitted from [VariationPosition] are kept at their declared
 * default and therefore normalize to `0.0`; unknown requested axes are rejected because they do not
 * have axis bounds to clamp against.
 *
 * Returned maps use deterministic ascending axis-tag order, independent of the order supplied by
 * callers or the order of entries inside [VariationPosition.axes].
 *
 * @param axes Axis definitions used to bound and normalize positions.
 * @throws IllegalArgumentException when two axis definitions use the same tag.
 */
class BoundedVariationNormalizer(
    axes: Iterable<VariationAxis>,
) : VariationNormalizer {
    private val axesByTag: List<VariationAxis> = axes.toList()
        .sortedBy { axis -> axis.tag }
        .also { sortedAxes ->
            sortedAxes.zipWithNext().forEach { (previous, current) ->
                require(previous.tag != current.tag) {
                    "variation axes contain duplicate tag ${current.tag}."
                }
            }
        }

    private val axisTags: Set<String> = axesByTag.map { axis -> axis.tag }.toSet()

    /**
     * Normalizes one user-space variation position against this normalizer's declared axes.
     *
     * @param position User-space variation position. Missing declared axes are treated as the axis
     * default; extra axes are rejected.
     * @return Normalized axis values keyed by tag in deterministic ascending tag order.
     * @throws IllegalArgumentException when [position] contains an unknown axis tag or a non-finite
     * coordinate.
     */
    override fun normalize(position: VariationPosition): Map<String, Double> {
        position.axes.forEach { (tag, value) ->
            require(tag in axisTags) {
                "variation position axis $tag is not declared in this normalizer."
            }
            require(value.isFinite()) { "variation position axis $tag must be finite." }
        }

        val normalized = LinkedHashMap<String, Double>(axesByTag.size)
        axesByTag.forEach { axis ->
            val requestedValue = position.axes[axis.tag] ?: axis.defaultValue
            normalized[axis.tag] = axis.normalize(requestedValue)
        }
        return normalized
    }
}

private fun VariationAxis.normalize(value: Double): Double {
    require(value.isFinite()) { "variation position axis $tag must be finite." }

    val clamped = value.coerceIn(minimum, maximum)
    return when {
        clamped < defaultValue -> {
            if (defaultValue == minimum) {
                0.0
            } else {
                ((clamped - defaultValue) / (defaultValue - minimum)).coerceIn(-1.0, 0.0)
            }
        }
        clamped > defaultValue -> {
            if (maximum == defaultValue) {
                0.0
            } else {
                ((clamped - defaultValue) / (maximum - defaultValue)).coerceIn(0.0, 1.0)
            }
        }
        else -> 0.0
    }
}
