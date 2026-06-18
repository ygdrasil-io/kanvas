package org.graphiks.kanvas.text.paragraph

import java.security.MessageDigest
import org.graphiks.kanvas.font.FontSlant
import org.graphiks.kanvas.font.FontStyle
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.TypefacePaletteSelection
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.OpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.PinnedUnicodeDataGenerator
import org.graphiks.kanvas.text.shaping.ShapedGlyphRun
import org.graphiks.kanvas.text.shaping.ShapingDiagnostic
import org.graphiks.kanvas.text.shaping.ShapingRequest

/**
 * Incrementally builds immutable paragraph input for layout.
 *
 * @property paragraphStyle Default paragraph-wide style applied to the built paragraph.
 */
public class ParagraphBuilder(
    public val paragraphStyle: ParagraphStyle = ParagraphStyle(),
) {
    private val text = StringBuilder()
    private val textStyles = mutableMapOf<IntRange, TextStyle>()
    private val placeholders = mutableMapOf<IntRange, PlaceholderStyle>()

    /**
     * Appends [text] with [style] to this builder.
     *
     * The builder keeps mutable accumulation state, but every [build] call
     * returns a snapshot that is independent from later appends. Empty strings
     * are ignored and do not create zero-length style ranges.
     */
    public fun append(text: String, style: TextStyle = TextStyle()): ParagraphBuilder {
        if (text.isEmpty()) return this
        val start = this.text.length
        this.text.append(text)
        textStyles[start until this.text.length] = style
        return this
    }

    /**
     * Appends a placeholder span with [style] to this builder.
     *
     * Placeholders are represented in paragraph text by the Unicode object
     * replacement character U+FFFC. The placeholder metrics are stored in a
     * range map keyed to that single UTF-16 code unit so later layout can keep
     * inline object dimensions separate from normal text shaping.
     */
    public fun appendPlaceholder(style: PlaceholderStyle): ParagraphBuilder {
        val start = text.length
        text.append(OBJECT_REPLACEMENT_CHARACTER)
        placeholders[start..start] = style
        return this
    }

    /**
     * Produces an immutable [Paragraph] snapshot from the current builder contents.
     *
     * The returned paragraph owns immutable copies of the accumulated text,
     * style ranges, and placeholder ranges. Mutating this builder after calling
     * [build] never changes previously built paragraphs.
     */
    public fun build(): Paragraph =
        Paragraph(
            text = text.toString(),
            paragraphStyle = paragraphStyle,
            textStyles = textStyles.toMap(),
            placeholders = placeholders.toMap(),
        )
}

/**
 * Immutable logical paragraph before line breaking and layout.
 *
 * @property text UTF-16 paragraph text.
 * @property paragraphStyle Paragraph-wide style and layout policy.
 * @property textStyles Styled text ranges keyed by inclusive UTF-16 ranges.
 * @property placeholders Placeholder ranges and metrics embedded in the text.
 */
public data class Paragraph(
    public val text: String,
    public val paragraphStyle: ParagraphStyle = ParagraphStyle(),
    public val textStyles: Map<IntRange, TextStyle> = emptyMap(),
    public val placeholders: Map<IntRange, PlaceholderStyle> = emptyMap(),
) {
    public val unicodeVersion: String = PinnedUnicodeDataGenerator.PinnedUnicodeVersion
    public val inputHash: String
        get() = paragraphInputPreimageJson().toByteArray(Charsets.UTF_8).sha256Hex()
    public val inputDiagnostics: List<ParagraphLayoutDiagnostic>
        get() = paragraphInputDiagnostics(this)

    /**
     * Serializes the immutable paragraph input snapshot into deterministic JSON.
     */
    public fun dumpInput(): String = buildString {
        append("{\n")
        append("  \"schema\": \"kanvas.paragraph.input.v1\",\n")
        append("  \"unicodeVersion\": ").append(paragraphJsonString(unicodeVersion)).append(",\n")
        append("  \"inputHash\": ").append(paragraphJsonString(inputHash)).append(",\n")
        append("  \"text\": ").append(paragraphJsonString(text)).append(",\n")
        append("  \"textLength\": ").append(text.length).append(",\n")
        append("  \"paragraphStyle\": ").append(paragraphStyle.toDumpJson()).append(",\n")
        append("  \"styleRuns\": ")
        appendParagraphJsonArray(
            values = textStyles.entries.sortedByRange(),
            entryIndent = "    ",
            closingIndent = "  ",
        ) { (range, style) ->
            style.toDumpJson(range)
        }
        append(",\n")
        append("  \"placeholders\": ")
        appendParagraphJsonArray(
            values = placeholders.entries.sortedByRange(),
            entryIndent = "    ",
            closingIndent = "  ",
        ) { (range, placeholder) ->
            placeholder.toDumpJson(range)
        }
        append(",\n")
        append("  \"diagnostics\": ")
        appendParagraphJsonArray(
            values = inputDiagnostics,
            entryIndent = "    ",
            closingIndent = "  ",
        ) { diagnostic ->
            diagnostic.toDumpJson()
        }
        append("\n")
        append("}\n")
    }

    private fun paragraphInputPreimageJson(): String = buildString {
        append("{\n")
        append("  \"schema\": \"kanvas.paragraph.input-preimage.v1\",\n")
        append("  \"unicodeVersion\": ").append(paragraphJsonString(unicodeVersion)).append(",\n")
        append("  \"text\": ").append(paragraphJsonString(text)).append(",\n")
        append("  \"paragraphStyle\": ").append(paragraphStyle.toDumpJson()).append(",\n")
        append("  \"styleRuns\": ")
        appendParagraphJsonArray(
            values = textStyles.entries.sortedByRange(),
            entryIndent = "    ",
            closingIndent = "  ",
        ) { (range, style) ->
            style.toDumpJson(range)
        }
        append(",\n")
        append("  \"placeholders\": ")
        appendParagraphJsonArray(
            values = placeholders.entries.sortedByRange(),
            entryIndent = "    ",
            closingIndent = "  ",
        ) { (range, placeholder) ->
            placeholder.toDumpJson(range)
        }
        append(",\n")
        append("  \"diagnostics\": ")
        appendParagraphJsonArray(
            values = inputDiagnostics,
            entryIndent = "    ",
            closingIndent = "  ",
        ) { diagnostic ->
            diagnostic.toDumpJson()
        }
        append("\n")
        append("}\n")
    }
}

public enum class TextAlign {
    START,
    CENTER,
    END,
    JUSTIFY,
}

public enum class TextDirection(
    public val legacyValue: Int,
) {
    AUTO(0),
    LEFT_TO_RIGHT(1),
    RIGHT_TO_LEFT(-1),
}

public enum class EllipsisPolicy {
    NONE,
    END,
}

public enum class TextHeightBehavior {
    FONT_METRICS,
    STRUT,
}

public enum class FallbackPreference {
    SYSTEM_DEFAULT,
    PREFER_DECLARED_FAMILIES,
    PREFER_EXACT_TYPEFACE,
}

public enum class SyntheticStylePolicy {
    ALLOW,
    DISALLOW,
}

public enum class TextDecorationStyle {
    SOLID,
    DOUBLE,
    DOTTED,
    DASHED,
    WAVY,
}

public data class TextDecorationSpec(
    public val underline: Boolean = false,
    public val overline: Boolean = false,
    public val lineThrough: Boolean = false,
    public val style: TextDecorationStyle = TextDecorationStyle.SOLID,
    public val thicknessMultiplier: Float = 1f,
)

public enum class PlaceholderAlignment {
    BASELINE,
    ABOVE_BASELINE,
    BELOW_BASELINE,
    TOP,
    BOTTOM,
    MIDDLE,
}

public enum class PlaceholderBaseline {
    ALPHABETIC,
    IDEOGRAPHIC,
}

/**
 * Defines paragraph-wide layout behavior.
 *
 * @property textAlign Horizontal alignment policy such as `start`, `center`, or `end`.
 * @property textDirection Paragraph direction, where negative means RTL, positive means LTR, and zero means auto.
 * @property maxLines Optional maximum number of laid-out lines.
 * @property ellipsis Optional ellipsis string used when layout truncates text.
 * @property lineHeight Optional explicit line height in logical pixels.
 */
public data class ParagraphStyle(
    public val textAlign: TextAlign = TextAlign.START,
    public val textDirection: TextDirection = TextDirection.AUTO,
    public val softWrap: Boolean = true,
    public val maxLines: Int? = null,
    public val ellipsis: String? = null,
    public val ellipsisPolicy: EllipsisPolicy = if (ellipsis == null) EllipsisPolicy.NONE else EllipsisPolicy.END,
    public val lineHeight: Float? = null,
    public val textHeightBehavior: TextHeightBehavior = TextHeightBehavior.FONT_METRICS,
    public val defaultLocale: String? = null,
)

/**
 * Stable diagnostic family emitted when a paragraph layout width constraint is non-finite.
 */
public const val PARAGRAPH_LAYOUT_CONSTRAINT_NON_FINITE_DIAGNOSTIC_CODE: String =
    "text.paragraph.constraint-non-finite"

/**
 * Stable diagnostic family emitted when a paragraph layout width constraint is negative.
 */
public const val PARAGRAPH_LAYOUT_CONSTRAINT_NEGATIVE_DIAGNOSTIC_CODE: String =
    "text.paragraph.constraint-negative"

/**
 * Stable diagnostic family emitted when max-line truncation requested ellipsis insertion.
 */
public const val PARAGRAPH_LAYOUT_MAX_LINES_ELLIPSIS_UNSUPPORTED_DIAGNOSTIC_CODE: String =
    "text.paragraph.max-lines-ellipsis-unsupported"

/**
 * Stable diagnostic family emitted when ellipsis would truncate through a visible placeholder span.
 */
public const val PARAGRAPH_PLACEHOLDER_ELLIPSIS_CONFLICT_DIAGNOSTIC_CODE: String =
    "text.paragraph.placeholder-ellipsis-conflict"

/**
 * Stable diagnostic family emitted when max-line truncation leaves no room for ellipsis insertion.
 */
public const val PARAGRAPH_ELLIPSIS_NO_ROOM_DIAGNOSTIC_CODE: String =
    "text.paragraph.ellipsis-no-room"

/**
 * Stable diagnostic family emitted when ellipsis shaping produced no drawable glyph run.
 */
public const val PARAGRAPH_ELLIPSIS_GLYPH_MISSING_DIAGNOSTIC_CODE: String =
    "text.paragraph.ellipsis-glyph-missing"

/**
 * Stable diagnostic family emitted when paragraph maxLines is invalid.
 */
public const val PARAGRAPH_LAYOUT_MAX_LINES_INVALID_DIAGNOSTIC_CODE: String =
    "text.paragraph.max-lines-invalid"

/**
 * Stable diagnostic family emitted when paragraph lineHeight is non-finite.
 */
public const val PARAGRAPH_LAYOUT_LINE_HEIGHT_NON_FINITE_DIAGNOSTIC_CODE: String =
    "text.paragraph.line-height-non-finite"

/**
 * Stable diagnostic family emitted when paragraph input contracts contain invalid numeric or coordinate facts.
 */
public const val PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE: String =
    "text.paragraph.invalid-constraint"

/**
 * Stable diagnostic family emitted when paragraph style or placeholder UTF-16 ranges fall outside the input text.
 */
public const val PARAGRAPH_INPUT_INVALID_STYLE_RANGE_DIAGNOSTIC_CODE: String =
    "text.paragraph.invalid-style-range"

/**
 * Stable diagnostic family emitted when a paragraph policy is captured but not yet supported by the bounded runtime.
 */
public const val PARAGRAPH_INPUT_UNSUPPORTED_POLICY_DIAGNOSTIC_CODE: String =
    "text.paragraph.unsupported-policy"

/**
 * Stable diagnostic family emitted when a requested selection range is outside the paragraph bounds.
 */
public const val PARAGRAPH_INVALID_SELECTION_RANGE_DIAGNOSTIC_CODE: String =
    "text.paragraph.invalid-selection-range"

/**
 * Stable diagnostic family emitted when a hit-test query point is non-finite.
 */
public const val PARAGRAPH_HIT_TEST_POINT_NON_FINITE_DIAGNOSTIC_CODE: String =
    "text.paragraph.hit-test-point-non-finite"

/**
 * Defines style applied to a logical range of text.
 *
 * @property typefaceId Stable typeface identifier requested for this style
 * when font resolution should start from a specific face.
 * @property fontSize Text size in logical pixels.
 * @property colorRgba Packed RGBA color value.
 * @property locale Optional BCP 47 locale tag used for shaping.
 * @property features OpenType feature settings represented as four-character tags to integer values.
 */
public data class TextStyle(
    public val fontFamilies: List<String> = emptyList(),
    public val fallbackPreference: FallbackPreference = FallbackPreference.SYSTEM_DEFAULT,
    public val typefaceId: TypefaceID? = null,
    public val fontSize: Float = 12f,
    public val fontStyle: FontStyle = FontStyle(),
    public val syntheticStylePolicy: SyntheticStylePolicy = SyntheticStylePolicy.ALLOW,
    public val colorRgba: Long = 0x000000ff,
    public val locale: String? = null,
    public val scriptHint: String? = null,
    public val features: Map<String, Int> = emptyMap(),
    public val variationCoordinates: Map<String, Float> = emptyMap(),
    public val palette: TypefacePaletteSelection? = null,
    public val decoration: TextDecorationSpec? = null,
    public val letterSpacing: Float = 0f,
    public val wordSpacing: Float = 0f,
    public val heightMultiplier: Float? = null,
)

/**
 * Defines an inline non-text placeholder embedded in paragraph flow.
 *
 * @property width Placeholder width in logical pixels.
 * @property height Placeholder height in logical pixels.
 * @property baselineOffset Distance from placeholder top to the text baseline.
 * @property alignment Baseline or box alignment policy for the placeholder.
 */
public data class PlaceholderStyle(
    public val width: Float,
    public val height: Float,
    public val baselineOffset: Float = height,
    public val alignment: PlaceholderAlignment = PlaceholderAlignment.BASELINE,
    public val baseline: PlaceholderBaseline? = PlaceholderBaseline.ALPHABETIC,
    public val participatesInLineHeight: Boolean = true,
)

/**
 * Lays out paragraphs into lines, boxes, and hit-test metadata.
 */
public interface ParagraphLayoutEngine {
    /**
     * Lays out [paragraph] within finite, non-negative [maxWidth] logical pixels.
     */
    public fun layout(paragraph: Paragraph, maxWidth: Float): ParagraphLayoutResult
}

/**
 * Minimal paragraph layout engine backed by the pure Kotlin OpenType shaping API.
 *
 * The engine is intentionally small and deterministic. It asks [lineBreaker]
 * for logical line ranges, shapes each resulting range with [shapingEngine],
 * and derives line width from the [ShapedGlyphRun.advanceX] values returned by
 * shaping. It currently uses one primary style per shaped line: the first style
 * range that overlaps the line, or a default [TextStyle] when no style exists.
 * Mixed styles inside a single line are therefore preserved in paragraph input
 * but not split into multiple shaping requests yet.
 *
 * Vertical metrics are deterministic estimates based on the selected
 * [TextStyle.fontSize] unless [ParagraphStyle.lineHeight] supplies an explicit
 * line height. Empty paragraphs produce an empty [ParagraphLayoutResult] and do
 * not call the shaping engine. [ParagraphStyle.maxLines] truncates produced
 * lines and sets [ParagraphLayoutResult.didOverflowHeight]; ellipsis insertion
 * is not implemented by this minimal engine.
 *
 * @param shapingEngine OpenType shaping engine used to shape each laid-out line.
 * @param lineBreaker Line breaker used to split paragraph text into line ranges.
 */
public class BasicParagraphLayoutEngine(
    private val shapingEngine: OpenTypeShapingEngine,
    private val lineBreaker: LineBreaker = SimpleLineBreaker(),
    private val shapingSegmenter: ParagraphShapingSegmenter = DefaultParagraphShapingSegmenter(),
) : ParagraphLayoutEngine {
    /**
     * Lays out [paragraph] into shaped lines within finite, non-negative [maxWidth] logical pixels.
     */
    override fun layout(paragraph: Paragraph, maxWidth: Float): ParagraphLayoutResult {
        maxWidthConstraintDiagnostic(maxWidth)?.let { diagnostic ->
            return ParagraphLayoutResult(
                paragraph = paragraph,
                maxWidth = maxWidth,
                diagnostics = listOf(diagnostic),
                layoutRefused = true,
            )
        }
        paragraphStyleDiagnostic(paragraph.paragraphStyle)?.let { diagnostic ->
            return ParagraphLayoutResult(
                paragraph = paragraph,
                maxWidth = maxWidth,
                diagnostics = listOf(diagnostic),
                layoutRefused = true,
            )
        }
        if (paragraph.inputDiagnostics.isNotEmpty()) {
            return ParagraphLayoutResult(
                paragraph = paragraph,
                maxWidth = maxWidth,
                diagnostics = paragraph.inputDiagnostics,
                layoutRefused = true,
            )
        }
        if (paragraph.text.isEmpty()) {
            return ParagraphLayoutResult(
                paragraph = paragraph,
                maxWidth = maxWidth,
            )
        }

        val lineBreakDiagnostics = mutableListOf<ParagraphLayoutDiagnostic>()
        if (lineBreaker is Uax14LineBreaker) {
            val lineBreakMap = lineBreaker.analyze(paragraph)
            lineBreakDiagnostics += lineBreakMap.diagnostics
            if (lineBreakMap.diagnostics.any { diagnostic -> diagnostic.severity == "refusal" }) {
                return ParagraphLayoutResult(
                    paragraph = paragraph,
                    maxWidth = maxWidth,
                    diagnostics = lineBreakMap.diagnostics.sortedWith(paragraphDiagnosticOrdering()),
                    layoutRefused = true,
                )
            }
        }

        val brokenRanges = lineBreaker.breakLines(paragraph, maxWidth)
        val maxLines = paragraph.paragraphStyle.maxLines
        val visibleRanges = if (maxLines == null) brokenRanges else brokenRanges.take(maxLines)
        val didOverflowHeight = maxLines != null && brokenRanges.size > visibleRanges.size
        val diagnostics = mutableListOf<ParagraphLayoutDiagnostic>()
        diagnostics += lineBreakDiagnostics
        val shapingPlan = shapingSegmenter.segment(paragraph)
        val placeholderIds = paragraph.placeholders.keys
            .sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
            .mapIndexed { index, range ->
            range to "ph-${index.toString().padStart(3, '0')}"
            }
            .toMap()
        diagnostics += shapingPlan.diagnostics
        var y = 0f
        var paragraphWidth = 0f
        val placeholderBoxes = mutableListOf<PlaceholderBox>()
        val lines = visibleRanges.mapIndexed { lineIndex, textRange ->
            val lineRequests = shapingPlan.requests
                .filter { request -> request.textRange.overlaps(textRange) }
                .map { request -> request.intersect(textRange) }
            val glyphRuns = mutableListOf<ShapedGlyphRun>()
            val shapedCoverage = mutableListOf<IntRange>()
            lineRequests.forEach { lineRequest ->
                val shapingResult = shapingEngine.shape(
                    ShapingRequest(
                        text = paragraph.text,
                        textRange = lineRequest.textRange,
                        typefaceId = lineRequest.typefaceId,
                        fontSize = lineRequest.style.fontSize,
                        features = FeatureSet(lineRequest.style.features),
                        locale = lineRequest.style.locale ?: paragraph.paragraphStyle.defaultLocale,
                        paragraphDirection = paragraph.paragraphStyle.textDirection.legacyValue,
                        preferredFamilies = lineRequest.style.fontFamilies,
                    ),
                )
                glyphRuns += shapingResult.glyphRuns
                if (shapingResult.glyphRuns.isNotEmpty()) {
                    shapedCoverage += lineRequest.textRange
                }
                diagnostics += shapingResult.diagnostics.map { diagnostic -> diagnostic.toParagraphLayoutDiagnostic() }
            }
            val placeholderRanges = shapingPlan.placeholderRanges.filter { range -> range.overlaps(textRange) }
            val lineWidth = glyphRuns.sumOf { it.advanceX.toDouble() }.toFloat() +
                placeholderRanges.sumOf { range -> paragraph.placeholderWidth(range).toDouble() }.toFloat() +
                paragraph.unshapedEstimatedWidth(
                    textRange = textRange,
                    coveredRanges = shapedCoverage + placeholderRanges,
                )
            val lineFontSize = lineRequests.maxOfOrNull { request -> request.style.fontSize } ?: paragraph.primaryStyleFor(textRange).fontSize
            val baseAscent = -lineFontSize * ASCENT_FRACTION
            val baseDescent = lineFontSize * DESCENT_FRACTION
            val baseLineExtent = paragraph.paragraphStyle.lineHeight ?: lineFontSize
            val hasParticipatingPlaceholders = placeholderRanges.any { range ->
                paragraph.placeholders.getValue(range).participatesInLineHeight
            }
            var ascent = baseAscent
            var descent = baseDescent
            var lineExtent = baseLineExtent
            if (hasParticipatingPlaceholders) {
                repeat(2) {
                    val baseline = y - ascent
                    val lineBottom = y + lineExtent
                    placeholderRanges.forEach { range ->
                        val placeholder = paragraph.placeholders.getValue(range)
                        if (!placeholder.participatesInLineHeight) return@forEach
                        val bounds = placeholder.resolveVerticalBounds(
                            lineTop = y,
                            lineBottom = lineBottom,
                            baseline = baseline,
                        )
                        ascent = minOf(ascent, bounds.top - baseline)
                        descent = maxOf(descent, bounds.bottom - baseline)
                    }
                    lineExtent = maxOf(baseLineExtent, descent - ascent)
                }
            }
            val lineHeight = lineExtent
            val baseline = y - ascent
            val metrics = LineMetrics(
                ascent = ascent,
                descent = descent,
                leading = lineHeight - (descent - ascent),
                width = lineWidth,
                baseline = baseline,
            )
            val direction = if ((glyphRuns.firstOrNull()?.bidiLevel ?: lineRequests.firstOrNull()?.bidiLevel ?: 0) % 2 == 0) 1 else -1
            val boxes = if (lineWidth == 0f) {
                emptyList()
            } else {
                listOf(
                    TextBox(
                        textRange = textRange,
                        left = 0f,
                        top = y,
                        right = lineWidth,
                        bottom = y + lineHeight,
                        direction = direction,
                    ),
                )
            }
            placeholderBoxes += resolvePlaceholderBoxes(
                paragraph = paragraph,
                textRange = textRange,
                lineIndex = lineIndex,
                lineTop = y,
                lineBottom = y + lineHeight,
                baseline = baseline,
                glyphRuns = glyphRuns,
                placeholderIds = placeholderIds,
            )

            y += lineHeight
            paragraphWidth = maxOf(paragraphWidth, lineWidth)
            LineLayout(
                textRange = textRange,
                segmentIds = lineRequests.map { request -> request.segmentId }.distinct(),
                glyphRuns = glyphRuns,
                metrics = metrics,
                boxes = boxes,
            )
        }
        var resolvedLines = lines
        var resolvedPlaceholderBoxes = placeholderBoxes.toList()
        if (didOverflowHeight && paragraph.paragraphStyle.ellipsis != null) {
            val lastLineIndex = lines.lastIndex
            val ellipsisResolution = resolveEllipsizedLastLine(
                paragraph = paragraph,
                lineIndex = lastLineIndex,
                lastVisibleLine = lines.lastOrNull(),
                paragraphShapingRequests = shapingPlan.requests,
                placeholderBoxes = placeholderBoxes,
                ellipsis = paragraph.paragraphStyle.ellipsis,
                maxWidth = maxWidth,
                shapingEngine = shapingEngine,
            )
            ellipsisResolution.line?.let { ellipsizedLine ->
                resolvedLines = lines.toMutableList().apply { set(lastIndex, ellipsizedLine) }
                resolvedPlaceholderBoxes = placeholderBoxes
                    .filterNot { box -> box.lineIndex == lastLineIndex }
                    .plus(ellipsisResolution.placeholderBoxes)
                paragraphWidth = resolvedLines.maxOfOrNull { line -> line.metrics.width } ?: 0f
            }
            ellipsisResolution.diagnostic?.let { diagnostics += it }
        }

        return ParagraphLayoutResult(
            paragraph = paragraph,
            lines = resolvedLines,
            maxWidth = maxWidth,
            width = paragraphWidth,
            height = y,
            didOverflowHeight = didOverflowHeight,
            didOverflowWidth = resolvedLines.any { it.metrics.width > maxWidth },
            paragraphShapingRequests = shapingPlan.requests,
            placeholderBoxes = resolvedPlaceholderBoxes,
            diagnostics = diagnostics.sortedWith(paragraphDiagnosticOrdering()),
        )
    }
}

/**
 * Complete visual layout result for a paragraph.
 *
 * @property paragraph Source paragraph that was laid out.
 * @property lines Ordered line layouts in visual order.
 * @property width Actual paragraph width in logical pixels.
 * @property height Actual paragraph height in logical pixels.
 * @property didOverflowHeight True when max-lines or vertical constraints clipped content.
 * @property didOverflowWidth True when at least one line exceeded the layout width.
 * @property diagnostics Stable paragraph layout diagnostics and unsupported-behavior refusals.
 * @property layoutRefused True when invalid input prevented any layout attempt.
 */
public data class ParagraphLayoutResult(
    public val paragraph: Paragraph,
    public val lines: List<LineLayout> = emptyList(),
    public val maxWidth: Float? = null,
    public val width: Float = 0f,
    public val height: Float = 0f,
    public val didOverflowHeight: Boolean = false,
    public val didOverflowWidth: Boolean = false,
    public val paragraphShapingRequests: List<ParagraphShapingRequest> = emptyList(),
    public val placeholderBoxes: List<PlaceholderBox> = emptyList(),
    public val diagnostics: List<ParagraphLayoutDiagnostic> = emptyList(),
    public val layoutRefused: Boolean = false,
) {
    /**
     * Serializes the current paragraph layout facts into deterministic JSON for test evidence.
     *
     * This is a conservative current-state dump: it records input ranges,
     * layout metrics, line boxes, overflow flags, and diagnostics. It does not
     * claim rich text splitting, complete bidi, selection, hit testing, or Skia
     * Paragraph parity.
     */
    public fun dump(): String = buildString {
        append("{\n")
        append("  \"schema\": \"kanvas.paragraph.layout.v1\",\n")
        append("  \"input\": ")
        append(paragraph.dumpInput().trimIndent().prependIndent("  ").trimStart())
        append(",\n")
        append("  \"layout\": {\"maxWidth\": ")
            .append(paragraphJsonNullableFloat(maxWidth))
            .append(", \"width\": ")
            .append(paragraphJsonFloat(width))
            .append(", \"height\": ")
            .append(paragraphJsonFloat(height))
            .append(", \"didOverflowWidth\": ")
            .append(didOverflowWidth)
            .append(", \"didOverflowHeight\": ")
            .append(didOverflowHeight)
            .append(", \"layoutRefused\": ")
            .append(layoutRefused)
            .append("},\n")
        append("  \"paragraphShapingRequests\": ")
        appendParagraphJsonArray(
            values = paragraphShapingRequests,
            entryIndent = "    ",
            closingIndent = "  ",
        ) { request -> request.toDumpJson() }
        append(",\n")
        append("  \"placeholderBoxes\": ")
        appendParagraphJsonArray(
            values = placeholderBoxes,
            entryIndent = "    ",
            closingIndent = "  ",
        ) { box -> box.toDumpJson() }
        append(",\n")
        append("  \"lines\": ")
        appendParagraphJsonArray(
            values = lines.withIndex().toList(),
            entryIndent = "    ",
            closingIndent = "  ",
        ) { (index, line) ->
            line.toDumpJson(index)
        }
        append(",\n")
        append("  \"diagnostics\": ")
        appendParagraphJsonArray(
            values = diagnostics,
            entryIndent = "    ",
            closingIndent = "  ",
        ) { diagnostic -> diagnostic.toDumpJson() }
        append("\n")
        append("}\n")
    }

    /**
     * Builds deterministic caret-stop facts from the current paragraph layout.
     */
    public fun hitTestMap(): HitTestMap {
        if (layoutRefused) {
            return HitTestMap(diagnostics = diagnostics)
        }
        val caretStops = mutableListOf<CaretStop>()
        val seenStops = mutableSetOf<String>()
        lines.withIndex().forEach { (lineIndex, line) ->
            val spans = line.visualSpanBoxes(
                paragraph = paragraph,
                lineIndex = lineIndex,
                placeholderBoxes = placeholderBoxes,
            )
            spans.flatMap { span ->
                listOf(
                    CaretStop(
                        position = TextPosition(offset = span.sourceRange.first, affinity = "downstream"),
                        lineIndex = span.lineIndex,
                        x = span.left,
                        top = span.top,
                        bottom = span.bottom,
                        placeholderId = span.placeholderId,
                    ),
                    CaretStop(
                        position = TextPosition(offset = span.sourceRange.last + 1, affinity = "upstream"),
                        lineIndex = span.lineIndex,
                        x = span.right,
                        top = span.top,
                        bottom = span.bottom,
                        placeholderId = span.placeholderId,
                    ),
                )
            }.plus(line.ellipsisCaretStop(lineIndex, spans)?.let(::listOf).orEmpty()).forEach { stop ->
                val key = "${stop.lineIndex}:${stop.position.offset}:${stop.position.affinity}:${stop.x}:${stop.placeholderId}"
                if (seenStops.add(key)) {
                    caretStops += stop
                }
            }
        }
        return HitTestMap(
            caretStops = caretStops.sortedWith(
                compareBy<CaretStop> { it.lineIndex }
                    .thenBy { it.x }
                    .thenBy { it.position.offset }
                    .thenBy { it.position.affinity },
            ),
            diagnostics = diagnostics,
        )
    }

    /**
     * Resolves stable selection boxes for [selection].
     */
    public fun selectionBoxes(selection: SelectionRange): SelectionQueryResult {
        if (layoutRefused) {
            return SelectionQueryResult(
                diagnostics = diagnostics,
                refused = true,
            )
        }
        val validatedRange = validateSelectionRange(selection) ?: return SelectionQueryResult(
            diagnostics = listOf(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INVALID_SELECTION_RANGE_DIAGNOSTIC_CODE,
                    message = "selection range must stay within paragraph bounds and preserve start <= end.",
                    severity = "refusal",
                ),
            ),
            refused = true,
        )
        if (validatedRange.start == validatedRange.endExclusive) {
            return SelectionQueryResult()
        }
        val selectedBoxes = visualSpanBoxes()
            .filter { span -> span.sourceRange.overlapsSelection(validatedRange.start, validatedRange.endExclusive) }
            .map { span ->
                SelectionBox(
                    sourceRange = span.sourceRange,
                    lineIndex = span.lineIndex,
                    left = span.left,
                    top = span.top,
                    right = span.right,
                    bottom = span.bottom,
                    direction = span.direction,
                    placeholderId = span.placeholderId,
                )
            }
            .sortedWith(compareBy<SelectionBox> { it.lineIndex }.thenBy { it.left })
            .mergeAdjacentTextSelectionBoxes()
        return SelectionQueryResult(boxes = selectedBoxes)
    }

    /**
     * Maps a paragraph-space point back to a stable logical text position.
     *
     * Out-of-bounds finite points clamp to the nearest caret stop on the nearest
     * visual line instead of refusing. Non-finite points return a stable refusal.
     */
    public fun hitTest(pointX: Float, pointY: Float): HitTestQueryResult {
        if (!pointX.isFinite() || !pointY.isFinite()) {
            return HitTestQueryResult(
                diagnostics = listOf(
                    ParagraphLayoutDiagnostic(
                        code = PARAGRAPH_HIT_TEST_POINT_NON_FINITE_DIAGNOSTIC_CODE,
                        message = "hit-test points must be finite.",
                        severity = "refusal",
                    ),
                ),
                refused = true,
            )
        }
        if (layoutRefused) {
            return HitTestQueryResult(
                diagnostics = diagnostics,
                refused = true,
            )
        }
        if (lines.isEmpty()) {
            return HitTestQueryResult(
                entry = HitTestEntry(
                    pointX = pointX,
                    pointY = pointY,
                    lineIndex = 0,
                    position = TextPosition(offset = 0),
                    isInsideText = false,
                ),
            )
        }

        val spans = visualSpanBoxes()
        spans.containingSpan(pointX, pointY)?.let { span ->
            return HitTestQueryResult(entry = span.toHitTestEntry(pointX, pointY, isInsideText = true))
        }
        val spansByLine = spans.groupBy { it.lineIndex }
        val lineIndex = lineIndexFor(pointY)
        val lineSpans = spansByLine[lineIndex].orEmpty().sortedBy { it.left }
        if (lineSpans.isEmpty()) {
            val fallbackOffset = lines[lineIndex].textRange.first
            return HitTestQueryResult(
                entry = HitTestEntry(
                    pointX = pointX,
                    pointY = pointY,
                    lineIndex = lineIndex,
                    position = TextPosition(offset = fallbackOffset),
                    isInsideText = false,
                ),
            )
        }

        val firstSpan = lineSpans.first()
        if (pointX <= firstSpan.left) {
            return HitTestQueryResult(entry = firstSpan.toHitTestEntry(pointX, pointY, pointX >= firstSpan.left))
        }

        lineSpans.forEachIndexed { index, span ->
            if (pointX <= span.right) {
                return HitTestQueryResult(entry = span.toHitTestEntry(pointX, pointY, isInsideText = pointX >= span.left))
            }
            val next = lineSpans.getOrNull(index + 1) ?: return@forEachIndexed
            if (pointX < next.left) {
                val gapMidpoint = (span.right + next.left) / 2f
                val chosenSpan = if (pointX < gapMidpoint) span else next
                val chosenPosition = if (chosenSpan === span) {
                    TextPosition(offset = span.sourceRange.last + 1, affinity = "upstream")
                } else {
                    TextPosition(offset = next.sourceRange.first, affinity = "downstream")
                }
                return HitTestQueryResult(
                    entry = HitTestEntry(
                        pointX = pointX,
                        pointY = pointY,
                        lineIndex = chosenSpan.lineIndex,
                        position = chosenPosition,
                        clusterRange = chosenSpan.sourceRange,
                        placeholderId = chosenSpan.placeholderId,
                        isInsideText = false,
                    ),
                )
            }
        }

        lines[lineIndex].ellipsisHitEntry(
            lineIndex = lineIndex,
            lineSpans = lineSpans,
            pointX = pointX,
            pointY = pointY,
        )?.let { entry ->
            return HitTestQueryResult(entry = entry)
        }

        val lastSpan = lineSpans.last()
        return HitTestQueryResult(
            entry = HitTestEntry(
                pointX = pointX,
                pointY = pointY,
                lineIndex = lastSpan.lineIndex,
                position = TextPosition(offset = lastSpan.sourceRange.last + 1, affinity = "upstream"),
                clusterRange = lastSpan.sourceRange,
                placeholderId = lastSpan.placeholderId,
                isInsideText = false,
            ),
        )
    }

    private fun validateSelectionRange(selection: SelectionRange): SelectionBounds? {
        val start = selection.start.offset
        val end = selection.end.offset
        if (start < 0 || end < 0 || start > end || end > paragraph.text.length) return null
        return SelectionBounds(start = start, endExclusive = end)
    }

    private fun lineIndexFor(pointY: Float): Int {
        val lineBounds = lines.mapIndexed { index, line ->
            val bounds = line.verticalBounds()
            IndexedLineBounds(index, bounds.first, bounds.second)
        }
        lineBounds.firstOrNull { bounds -> pointY >= bounds.top && pointY <= bounds.bottom }?.let { bounds ->
            return bounds.lineIndex
        }
        if (pointY < lineBounds.first().top) return lineBounds.first().lineIndex
        if (pointY > lineBounds.last().bottom) return lineBounds.last().lineIndex
        return lineBounds.minByOrNull { bounds ->
            minOf(
                kotlin.math.abs(pointY - bounds.top),
                kotlin.math.abs(pointY - bounds.bottom),
            )
        }?.lineIndex ?: 0
    }

    private fun visualSpanBoxes(): List<VisualSpanBox> =
        lines.withIndex().flatMap { (lineIndex, line) ->
            line.visualSpanBoxes(
                paragraph = paragraph,
                lineIndex = lineIndex,
                placeholderBoxes = placeholderBoxes,
            )
        }
}

/**
 * Stable paragraph layout diagnostic or refusal fact for dumps and validation evidence.
 *
 * @property code Stable machine-readable diagnostic code.
 * @property message Human-readable single-line detail.
 * @property textRange Optional inclusive UTF-16 range associated with the diagnostic.
 * @property severity Stable classification such as `diagnostic` or `refusal`.
 */
public data class ParagraphLayoutDiagnostic(
    public val code: String,
    public val message: String,
    public val textRange: IntRange? = null,
    public val severity: String = "diagnostic",
) {
    init {
        require(code.isStableParagraphDiagnosticToken()) { "code must be a stable one-line diagnostic code." }
        require(severity.isStableParagraphDiagnosticToken()) { "severity must be a stable one-line diagnostic token." }
        require('\n' !in message && '\r' !in message) { "message must be a single line." }
    }
}

/**
 * Computes legal line break ranges for paragraph layout.
 */
public interface LineBreaker {
    /**
     * Breaks [paragraph] into inclusive UTF-16 ranges that can become lines within finite, non-negative [maxWidth].
     */
    public fun breakLines(paragraph: Paragraph, maxWidth: Float): List<IntRange>
}

/**
 * Default paragraph line-break surface backed by the bounded UAX #14 implementation.
 */
public class SimpleLineBreaker : Uax14LineBreaker {
    private val delegate: Uax14LineBreaker = DefaultUax14LineBreaker()

    override fun analyze(paragraph: Paragraph): LineBreakMap = delegate.analyze(paragraph)

    /**
     * Breaks [paragraph] into greedy line ranges constrained by finite, non-negative [maxWidth].
     */
    override fun breakLines(paragraph: Paragraph, maxWidth: Float): List<IntRange> {
        return delegate.breakLines(paragraph, maxWidth)
    }
}

/**
 * Describes one laid-out line in a paragraph.
 *
 * @property textRange Inclusive UTF-16 range covered by the line.
 * @property glyphRuns Shaped glyph runs placed on the line.
 * @property metrics Vertical and horizontal metrics for this line.
 * @property boxes Text boxes available for selection and painting.
 */
public data class LineLayout(
    public val textRange: IntRange,
    public val segmentIds: List<String> = emptyList(),
    public val glyphRuns: List<ShapedGlyphRun> = emptyList(),
    public val metrics: LineMetrics = LineMetrics(),
    public val boxes: List<TextBox> = emptyList(),
    public val isEllipsized: Boolean = false,
    public val truncatedRange: IntRange? = null,
    public val ellipsisGlyphRuns: List<ShapedGlyphRun> = emptyList(),
    public val ellipsisSegmentIds: List<String> = emptyList(),
)

/**
 * Stores measured metrics for a single line.
 *
 * @property ascent Distance from baseline to top, usually negative in font coordinates.
 * @property descent Distance from baseline to bottom.
 * @property leading Extra distance between adjacent lines.
 * @property width Line width in logical pixels.
 * @property baseline Baseline y position in paragraph coordinates.
 */
public data class LineMetrics(
    public val ascent: Float = 0f,
    public val descent: Float = 0f,
    public val leading: Float = 0f,
    public val width: Float = 0f,
    public val baseline: Float = 0f,
)

/**
 * Axis-aligned rectangle associated with a text range.
 *
 * @property textRange Inclusive UTF-16 range covered by the box.
 * @property left Left edge in paragraph coordinates.
 * @property top Top edge in paragraph coordinates.
 * @property right Right edge in paragraph coordinates.
 * @property bottom Bottom edge in paragraph coordinates.
 * @property direction Resolved direction for the covered text.
 */
public data class TextBox(
    public val textRange: IntRange,
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
    public val direction: Int = 1,
)

/**
 * Resolved geometry for one placeholder embedded in paragraph flow.
 *
 * @property placeholderId Stable placeholder identifier within the paragraph.
 * @property sourceRange Inclusive UTF-16 range occupied by the placeholder token.
 * @property lineIndex Zero-based visual line index that owns the placeholder.
 * @property baselineOffset Distance from placeholder top to the line baseline.
 * @property alignment Serialized placeholder alignment applied to the box.
 * @property baseline Serialized baseline family when one is required for alignment.
 * @property participatesInLineHeight True when the placeholder contributes to line ascent/descent.
 */
public data class PlaceholderBox(
    public val placeholderId: String,
    public val sourceRange: IntRange,
    public val lineIndex: Int,
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
    public val baselineOffset: Float,
    public val alignment: String,
    public val baseline: String?,
    public val participatesInLineHeight: Boolean,
)

/**
 * Deterministic selection rectangle derived from laid-out text or placeholders.
 *
 * @property sourceRange Inclusive UTF-16 range covered by this selection box.
 * @property lineIndex Zero-based visual line index.
 * @property placeholderId Placeholder identifier when the box targets an inline placeholder.
 */
public data class SelectionBox(
    public val sourceRange: IntRange,
    public val lineIndex: Int,
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
    public val direction: Int = 1,
    public val placeholderId: String? = null,
)

/**
 * Stable caret-stop location available for hit testing and selection snapping.
 */
public data class CaretStop(
    public val position: TextPosition,
    public val lineIndex: Int,
    public val x: Float,
    public val top: Float,
    public val bottom: Float,
    public val placeholderId: String? = null,
)

/**
 * Stable point-to-text mapping fact built from paragraph layout spans.
 */
public data class HitTestEntry(
    public val pointX: Float,
    public val pointY: Float,
    public val lineIndex: Int,
    public val position: TextPosition,
    public val clusterRange: IntRange? = null,
    public val placeholderId: String? = null,
    public val isInsideText: Boolean,
)

/**
 * Deterministic caret-stop map for a laid-out paragraph.
 */
public data class HitTestMap(
    public val caretStops: List<CaretStop> = emptyList(),
    public val diagnostics: List<ParagraphLayoutDiagnostic> = emptyList(),
)

/**
 * Result of resolving selection boxes for a logical text range.
 */
public data class SelectionQueryResult(
    public val boxes: List<SelectionBox> = emptyList(),
    public val diagnostics: List<ParagraphLayoutDiagnostic> = emptyList(),
    public val refused: Boolean = false,
)

/**
 * Result of hit testing a paragraph coordinate.
 */
public data class HitTestQueryResult(
    public val entry: HitTestEntry? = null,
    public val diagnostics: List<ParagraphLayoutDiagnostic> = emptyList(),
    public val refused: Boolean = false,
)

/**
 * Result of mapping a paragraph coordinate back to a logical text position.
 *
 * @property position Logical text position nearest to the hit point.
 * @property isInsideText True when the hit point is inside a glyph or selection box.
 * @property lineIndex Zero-based visual line index hit by the query.
 */
public data class HitTestResult(
    public val position: TextPosition,
    public val isInsideText: Boolean,
    public val lineIndex: Int,
)

/**
 * Represents a logical selection range in paragraph text.
 *
 * @property start Inclusive start text position.
 * @property end Exclusive end text position.
 * @property isDirectional True when the range preserves drag direction.
 */
public data class SelectionRange(
    public val start: TextPosition,
    public val end: TextPosition,
    public val isDirectional: Boolean = false,
)

/**
 * Represents one insertion point or affinity-adjusted character position.
 *
 * @property offset UTF-16 offset in paragraph text.
 * @property affinity Direction used to disambiguate positions at soft line breaks.
 */
public data class TextPosition(
    public val offset: Int,
    public val affinity: String = "downstream",
)

private const val OBJECT_REPLACEMENT_CHARACTER: Char = '\uFFFC'
private const val ASCENT_FRACTION: Float = 0.8f
private const val DESCENT_FRACTION: Float = 0.2f

private fun maxWidthConstraintDiagnostic(maxWidth: Float): ParagraphLayoutDiagnostic? =
    when {
        !maxWidth.isFinite() -> ParagraphLayoutDiagnostic(
            code = PARAGRAPH_LAYOUT_CONSTRAINT_NON_FINITE_DIAGNOSTIC_CODE,
            message = "maxWidth must be finite.",
            severity = "refusal",
        )
        maxWidth < 0f -> ParagraphLayoutDiagnostic(
            code = PARAGRAPH_LAYOUT_CONSTRAINT_NEGATIVE_DIAGNOSTIC_CODE,
            message = "maxWidth must be non-negative.",
            severity = "refusal",
        )
        else -> null
    }

private fun paragraphStyleDiagnostic(style: ParagraphStyle): ParagraphLayoutDiagnostic? =
    when {
        style.maxLines != null && style.maxLines < 0 -> ParagraphLayoutDiagnostic(
            code = PARAGRAPH_LAYOUT_MAX_LINES_INVALID_DIAGNOSTIC_CODE,
            message = "maxLines must be non-negative when present.",
            severity = "refusal",
        )
        style.lineHeight != null && !style.lineHeight.isFinite() -> ParagraphLayoutDiagnostic(
            code = PARAGRAPH_LAYOUT_LINE_HEIGHT_NON_FINITE_DIAGNOSTIC_CODE,
            message = "lineHeight must be finite when present.",
            severity = "refusal",
        )
        else -> null
    }

private fun paragraphInputDiagnostics(paragraph: Paragraph): List<ParagraphLayoutDiagnostic> = buildList {
    val sortedStyleRanges = paragraph.textStyles.entries.sortedByRange()
    sortedStyleRanges.zipWithNext().forEach { (previous, current) ->
        if (previous.key.overlaps(current.key)) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_STYLE_RANGE_DIAGNOSTIC_CODE,
                    message = "Style ranges ${previous.key.toDumpLabel()} and ${current.key.toDumpLabel()} overlap.",
                    textRange = current.key,
                    severity = "refusal",
                ),
            )
        }
    }
    sortedStyleRanges.forEach { (range, style) ->
        if (!range.isValidUtf16RangeFor(paragraph.text.length)) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_STYLE_RANGE_DIAGNOSTIC_CODE,
                    message = "Style range ${range.toDumpLabel()} falls outside text length ${paragraph.text.length}.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (!style.fontSize.isFinite() || style.fontSize <= 0f) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "fontSize must be finite and greater than zero.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (style.fontFamilies.any { family -> family.isBlank() }) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "fontFamilies must not contain blank family names.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (!style.letterSpacing.isFinite() || !style.wordSpacing.isFinite()) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "letterSpacing and wordSpacing must be finite.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (style.heightMultiplier != null && (!style.heightMultiplier.isFinite() || style.heightMultiplier <= 0f)) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "heightMultiplier must be finite and greater than zero when present.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        style.variationCoordinates.entries.sortedBy { it.key }.forEach { (axisTag, value) ->
            if (!axisTag.isStableVariationAxisTag()) {
                add(
                    ParagraphLayoutDiagnostic(
                        code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                        message = "variation axis tag '$axisTag' must be a four-character printable ASCII OpenType tag.",
                        textRange = range,
                        severity = "refusal",
                    ),
                )
            }
            if (!value.isFinite()) {
                add(
                    ParagraphLayoutDiagnostic(
                        code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                        message = "variation coordinate '$axisTag' must be finite.",
                        textRange = range,
                        severity = "refusal",
                    ),
                )
            }
        }
    }
    val sortedPlaceholderRanges = paragraph.placeholders.entries.sortedByRange()
    sortedPlaceholderRanges.zipWithNext().forEach { (previous, current) ->
        if (previous.key.overlaps(current.key)) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_STYLE_RANGE_DIAGNOSTIC_CODE,
                    message = "Placeholder ranges ${previous.key.toDumpLabel()} and ${current.key.toDumpLabel()} overlap.",
                    textRange = current.key,
                    severity = "refusal",
                ),
            )
        }
    }
    sortedPlaceholderRanges.forEach { (range, placeholder) ->
        if (!range.isValidUtf16RangeFor(paragraph.text.length)) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_STYLE_RANGE_DIAGNOSTIC_CODE,
                    message = "Placeholder range ${range.toDumpLabel()} falls outside text length ${paragraph.text.length}.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (range.first != range.last) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_STYLE_RANGE_DIAGNOSTIC_CODE,
                    message = "Placeholder range ${range.toDumpLabel()} must cover exactly one UTF-16 code unit.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (!placeholder.width.isFinite() || !placeholder.height.isFinite() || !placeholder.baselineOffset.isFinite()) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "placeholder width, height, and baselineOffset must be finite.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (placeholder.width < 0f || placeholder.height < 0f) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "placeholder width and height must be non-negative.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (placeholder.baselineOffset < 0f) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "placeholder baselineOffset must be non-negative.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (placeholder.requiresBaseline() && placeholder.baseline == null) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_INVALID_CONSTRAINT_DIAGNOSTIC_CODE,
                    message = "placeholder alignment '${placeholder.alignment.serializedName}' requires a baseline.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
        if (placeholder.baseline != null && placeholder.baseline != PlaceholderBaseline.ALPHABETIC) {
            add(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_INPUT_UNSUPPORTED_POLICY_DIAGNOSTIC_CODE,
                    message = "placeholder baseline '${placeholder.baseline.serializedName}' is not supported by the bounded paragraph runtime.",
                    textRange = range,
                    severity = "refusal",
                ),
            )
        }
    }
    if (paragraph.paragraphStyle.textHeightBehavior != TextHeightBehavior.FONT_METRICS) {
        add(
            ParagraphLayoutDiagnostic(
                code = PARAGRAPH_INPUT_UNSUPPORTED_POLICY_DIAGNOSTIC_CODE,
                message = "textHeightBehavior '${paragraph.paragraphStyle.textHeightBehavior.serializedName}' is not supported by the bounded paragraph runtime.",
                severity = "refusal",
            ),
        )
    }
}

private fun ShapingDiagnostic.toParagraphLayoutDiagnostic(): ParagraphLayoutDiagnostic =
    ParagraphLayoutDiagnostic(
        code = code,
        message = message,
        textRange = textRange,
        severity = "diagnostic",
    )

private fun resolveEllipsizedLastLine(
    paragraph: Paragraph,
    lineIndex: Int,
    lastVisibleLine: LineLayout?,
    paragraphShapingRequests: List<ParagraphShapingRequest>,
    placeholderBoxes: List<PlaceholderBox>,
    ellipsis: String,
    maxWidth: Float,
    shapingEngine: OpenTypeShapingEngine,
): EllipsisResolution {
    val line = lastVisibleLine ?: return EllipsisResolution(
        diagnostic = ParagraphLayoutDiagnostic(
            code = PARAGRAPH_ELLIPSIS_NO_ROOM_DIAGNOSTIC_CODE,
            message = "ellipsis cannot fit within the remaining maxWidth after maxLines truncation.",
            textRange = paragraph.text.indices.takeIf { paragraph.text.isNotEmpty() },
            severity = "refusal",
        ),
    )
    val spans = line.visualSpanBoxes(
        paragraph = paragraph,
        lineIndex = lineIndex,
        placeholderBoxes = placeholderBoxes,
    ).sortedBy { span -> span.sourceRange.first }
    if (spans.isEmpty()) {
        return EllipsisResolution(
            diagnostic = ParagraphLayoutDiagnostic(
                code = PARAGRAPH_ELLIPSIS_NO_ROOM_DIAGNOSTIC_CODE,
                message = "ellipsis cannot fit within the remaining maxWidth after maxLines truncation.",
                textRange = line.textRange.first..paragraph.text.lastIndex,
                severity = "refusal",
            ),
        )
    }
    var keepCount = spans.size
    var glyphMissingRange: IntRange? = null
    while (keepCount >= 0) {
        val keptSpans = spans.take(keepCount)
        val visibleRange = if (keptSpans.isEmpty()) {
            line.textRange.first..(line.textRange.first - 1)
        } else {
            keptSpans.first().sourceRange.first..keptSpans.last().sourceRange.last
        }
        val truncatedRange = if (keptSpans.isEmpty()) {
            line.textRange.first..paragraph.text.lastIndex
        } else {
            paragraph.truncatedRangeAfter(visibleRange)
        }
        val styleReferenceRange = keptSpans.lastOrNull()?.sourceRange ?: spans.first().sourceRange
        val ellipsisRun = shapeEllipsisRun(
            paragraph = paragraph,
            ellipsis = ellipsis,
            visibleRange = visibleRange,
            styleReferenceRange = styleReferenceRange,
            paragraphShapingRequests = paragraphShapingRequests,
            shapingEngine = shapingEngine,
        ) ?: run {
            if (glyphMissingRange == null) {
                glyphMissingRange = truncatedRange ?: styleReferenceRange
            }
            keepCount--
            continue
        }
        val keptWidth = keptSpans.sumOf { span -> (span.right - span.left).toDouble() }.toFloat()
        val totalWidth = keptWidth + ellipsisRun.width
        val terminalPlaceholder = keptSpans.lastOrNull()?.takeIf { span -> span.placeholderId != null }
        if (totalWidth <= maxWidth) {
            val (ellipsizedLine, retainedPlaceholderBoxes) = line.applyEllipsis(
                paragraph = paragraph,
                lineIndex = lineIndex,
                placeholderBoxes = placeholderBoxes,
                visibleRange = visibleRange,
                keptSpans = keptSpans,
                truncatedRange = truncatedRange,
                ellipsisGlyphRuns = ellipsisRun.glyphRuns,
                ellipsisSegmentIds = List(ellipsisRun.glyphRuns.size) { ellipsisRun.segmentId },
            )
            return EllipsisResolution(
                line = ellipsizedLine,
                placeholderBoxes = retainedPlaceholderBoxes,
            )
        }
        if (terminalPlaceholder != null) {
            return EllipsisResolution(
                diagnostic = ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_PLACEHOLDER_ELLIPSIS_CONFLICT_DIAGNOSTIC_CODE,
                    message = "ellipsis cannot partially truncate a visible line containing a terminal placeholder in the bounded paragraph runtime.",
                    textRange = terminalPlaceholder.sourceRange,
                    severity = "refusal",
                ),
            )
        }
        keepCount--
    }
    if (glyphMissingRange != null) {
        return EllipsisResolution(
            diagnostic = ParagraphLayoutDiagnostic(
                code = PARAGRAPH_ELLIPSIS_GLYPH_MISSING_DIAGNOSTIC_CODE,
                message = "ellipsis shaping produced no glyph run for the active trailing style.",
                textRange = glyphMissingRange,
                severity = "refusal",
            ),
        )
    }
    return EllipsisResolution(
        diagnostic = ParagraphLayoutDiagnostic(
            code = PARAGRAPH_ELLIPSIS_NO_ROOM_DIAGNOSTIC_CODE,
            message = "ellipsis cannot fit within the remaining maxWidth after maxLines truncation.",
            textRange = line.textRange.first..paragraph.text.lastIndex,
            severity = "refusal",
        ),
    )
}

private fun shapeEllipsisRun(
    paragraph: Paragraph,
    ellipsis: String,
    visibleRange: IntRange,
    styleReferenceRange: IntRange,
    paragraphShapingRequests: List<ParagraphShapingRequest>,
    shapingEngine: OpenTypeShapingEngine,
): EllipsisRun? {
    val requestReferenceRange = visibleRange.takeUnless { it.isEmpty() } ?: styleReferenceRange
    val referenceRequest = paragraphShapingRequests
        .filter { request -> request.textRange.overlaps(requestReferenceRange) }
        .maxWithOrNull(
            compareBy<ParagraphShapingRequest> { minOf(it.textRange.last, requestReferenceRange.last) }
                .thenBy { it.textRange.first },
        )
    val referenceStyle = referenceRequest?.style ?: paragraph.primaryStyleFor(styleReferenceRange)
    if (ellipsis.isEmpty()) {
        return EllipsisRun(
            segmentId = referenceRequest?.segmentId ?: "seg-ellipsis",
            glyphRuns = emptyList(),
            width = 0f,
        )
    }
    val shapingResult = shapingEngine.shape(
        ShapingRequest(
            text = ellipsis,
            textRange = ellipsis.indices,
            typefaceId = referenceRequest?.typefaceId,
            fontSize = referenceStyle.fontSize,
            features = FeatureSet(referenceStyle.features),
            locale = referenceStyle.locale ?: paragraph.paragraphStyle.defaultLocale,
            paragraphDirection = paragraph.paragraphStyle.textDirection.legacyValue,
            preferredFamilies = referenceStyle.fontFamilies,
        ),
    )
    return shapingResult.glyphRuns.takeIf { runs -> runs.isNotEmpty() }?.let { glyphRuns ->
        EllipsisRun(
            segmentId = referenceRequest?.segmentId ?: "seg-ellipsis",
            glyphRuns = glyphRuns,
            width = glyphRuns.sumOf { run -> run.advanceX.toDouble() }.toFloat(),
        )
    }
}

private fun requireValidMaxWidth(maxWidth: Float) {
    require(maxWidth.isFinite() && maxWidth >= 0f) { "maxWidth must be finite and non-negative." }
}

private fun ParagraphStyle.toDumpJson(): String = buildString {
    append("{\"textAlign\": ")
        .append(paragraphJsonString(textAlign.serializedName))
        .append(", \"textDirection\": ")
        .append(paragraphJsonString(textDirection.serializedName))
        .append(", \"softWrap\": ")
        .append(softWrap)
        .append(", \"maxLines\": ")
        .append(maxLines?.toString() ?: "null")
        .append(", \"ellipsis\": ")
        .append(paragraphJsonNullableString(ellipsis))
        .append(", \"ellipsisPolicy\": ")
        .append(paragraphJsonString(ellipsisPolicy.serializedName))
        .append(", \"lineHeight\": ")
        .append(paragraphJsonNullableFloat(lineHeight))
        .append(", \"textHeightBehavior\": ")
        .append(paragraphJsonString(textHeightBehavior.serializedName))
        .append(", \"defaultLocale\": ")
        .append(paragraphJsonNullableString(defaultLocale))
        .append("}")
}

private fun TextStyle.toDumpJson(range: IntRange): String = buildString {
    append("{\"range\": ")
        .append(paragraphJsonString(range.toDumpLabel()))
        .append(", \"fontFamilies\": ")
        .append(fontFamilies.toParagraphJsonStringArray())
        .append(", \"fallbackPreference\": ")
        .append(paragraphJsonString(fallbackPreference.serializedName))
        .append(", \"typefaceId\": ")
        .append(typefaceId?.value?.toString()?.let(::paragraphJsonString) ?: "null")
        .append(", \"fontSize\": ")
        .append(paragraphJsonFloat(fontSize))
        .append(", \"fontWeight\": ")
        .append(fontStyle.weight)
        .append(", \"fontWidth\": ")
        .append(fontStyle.width)
        .append(", \"fontSlant\": ")
        .append(paragraphJsonString(fontStyle.slant.serializedName))
        .append(", \"syntheticStylePolicy\": ")
        .append(paragraphJsonString(syntheticStylePolicy.serializedName))
        .append(", \"locale\": ")
        .append(paragraphJsonNullableString(locale))
        .append(", \"scriptHint\": ")
        .append(paragraphJsonNullableString(scriptHint))
        .append(", \"features\": ")
        .append(features.toDumpJson())
        .append(", \"variationCoordinates\": ")
        .append(variationCoordinates.toVariationJson())
        .append(", \"palette\": ")
        .append(palette?.toParagraphJson() ?: "null")
        .append(", \"colorRgba\": ")
        .append(paragraphJsonString(colorRgba.toUInt().toString(16).padStart(8, '0')))
        .append(", \"decoration\": ")
        .append(decoration?.toParagraphJson() ?: "null")
        .append(", \"letterSpacing\": ")
        .append(paragraphJsonFloat(letterSpacing))
        .append(", \"wordSpacing\": ")
        .append(paragraphJsonFloat(wordSpacing))
        .append(", \"heightMultiplier\": ")
        .append(paragraphJsonNullableFloat(heightMultiplier))
        .append("}")
}

private fun PlaceholderStyle.toDumpJson(range: IntRange): String = buildString {
    append("{\"range\": ")
        .append(paragraphJsonString(range.toDumpLabel()))
        .append(", \"width\": ")
        .append(paragraphJsonFloat(width))
        .append(", \"height\": ")
        .append(paragraphJsonFloat(height))
        .append(", \"baselineOffset\": ")
        .append(paragraphJsonFloat(baselineOffset))
        .append(", \"alignment\": ")
        .append(paragraphJsonString(alignment.serializedName))
        .append(", \"baseline\": ")
        .append(baseline?.serializedName?.let(::paragraphJsonString) ?: "null")
        .append(", \"participatesInLineHeight\": ")
        .append(participatesInLineHeight)
        .append("}")
}

private fun LineLayout.toDumpJson(index: Int): String = buildString {
    append("{\"index\": ")
    append(index)
    append(", \"textRange\": ")
    append(paragraphJsonString(textRange.toDumpLabel()))
    append(", \"segmentIds\": ")
    append(segmentIds.joinToString(prefix = "[", postfix = "]") { segmentId -> paragraphJsonString(segmentId) })
    append(", \"metrics\": ")
    append(metrics.toDumpJson())
    append(", \"boxes\": ")
    append(boxes.joinToString(prefix = "[", postfix = "]") { box -> box.toDumpJson() })
    append(", \"glyphRunCount\": ")
    append(glyphRuns.size)
    if (isEllipsized) {
        append(", \"isEllipsized\": true")
            .append(", \"visibleRange\": ")
            .append(paragraphJsonString(textRange.toDumpLabel()))
            .append(", \"truncatedRange\": ")
            .append(truncatedRange?.toDumpLabel()?.let(::paragraphJsonString) ?: "null")
            .append(", \"ellipsisGlyphRuns\": ")
            .append(
                ellipsisGlyphRuns.mapIndexed { runIndex, run ->
                    run.toEllipsisDumpJson(
                        segmentId = ellipsisSegmentIds.getOrElse(runIndex) {
                            ellipsisSegmentIds.lastOrNull() ?: "seg-ellipsis"
                        },
                    )
                }.joinToString(prefix = "[", postfix = "]"),
            )
    }
    append("}")
}

private fun LineMetrics.toDumpJson(): String = buildString {
    append("{\"ascent\": ")
        .append(paragraphJsonFloat(ascent))
        .append(", \"descent\": ")
        .append(paragraphJsonFloat(descent))
        .append(", \"leading\": ")
        .append(paragraphJsonFloat(leading))
        .append(", \"width\": ")
        .append(paragraphJsonFloat(width))
        .append(", \"baseline\": ")
        .append(paragraphJsonFloat(baseline))
        .append("}")
}

private fun TextBox.toDumpJson(): String = buildString {
    append("{\"textRange\": ")
        .append(paragraphJsonString(textRange.toDumpLabel()))
        .append(", \"left\": ")
        .append(paragraphJsonFloat(left))
        .append(", \"top\": ")
        .append(paragraphJsonFloat(top))
        .append(", \"right\": ")
        .append(paragraphJsonFloat(right))
        .append(", \"bottom\": ")
        .append(paragraphJsonFloat(bottom))
        .append(", \"direction\": ")
        .append(direction)
        .append("}")
}

private fun PlaceholderBox.toDumpJson(): String = buildString {
    append("{\"placeholderId\": ")
        .append(paragraphJsonString(placeholderId))
        .append(", \"sourceRange\": ")
        .append(paragraphJsonString(sourceRange.toDumpLabel()))
        .append(", \"lineIndex\": ")
        .append(lineIndex)
        .append(", \"left\": ")
        .append(paragraphJsonFloat(left))
        .append(", \"top\": ")
        .append(paragraphJsonFloat(top))
        .append(", \"right\": ")
        .append(paragraphJsonFloat(right))
        .append(", \"bottom\": ")
        .append(paragraphJsonFloat(bottom))
        .append(", \"baselineOffset\": ")
        .append(paragraphJsonFloat(baselineOffset))
        .append(", \"alignment\": ")
        .append(paragraphJsonString(alignment))
        .append(", \"baseline\": ")
        .append(baseline?.let(::paragraphJsonString) ?: "null")
        .append(", \"participatesInLineHeight\": ")
        .append(participatesInLineHeight)
        .append("}")
}

private fun ShapedGlyphRun.toEllipsisDumpJson(segmentId: String): String = buildString {
    append("{\"segmentId\": ")
        .append(paragraphJsonString(segmentId))
        .append(", \"glyphCount\": ")
        .append(glyphIds.size)
        .append(", \"advanceX\": ")
        .append(paragraphJsonFloat(advanceX))
        .append(", \"fontSize\": ")
        .append(paragraphJsonFloat(fontSize))
        .append(", \"bidiLevel\": ")
        .append(bidiLevel)
        .append("}")
}

private fun ParagraphShapingRequest.toDumpJson(): String = buildString {
    append("{\"segmentId\": ")
        .append(paragraphJsonString(segmentId))
        .append(", \"textRange\": ")
        .append(paragraphJsonString(textRange.toDumpLabel()))
        .append(", \"fontSize\": ")
        .append(paragraphJsonFloat(style.fontSize))
        .append(", \"fontFamilies\": ")
        .append(style.fontFamilies.toParagraphJsonStringArray())
        .append(", \"typefaceId\": ")
        .append(typefaceId?.value?.toString()?.let(::paragraphJsonString) ?: "null")
        .append(", \"locale\": ")
        .append(paragraphJsonNullableString(style.locale))
        .append(", \"script\": ")
        .append(paragraphJsonString(script))
        .append(", \"bidiLevel\": ")
        .append(bidiLevel)
        .append("}")
}

private val TextAlign.serializedName: String
    get() = when (this) {
        TextAlign.START -> "start"
        TextAlign.CENTER -> "center"
        TextAlign.END -> "end"
        TextAlign.JUSTIFY -> "justify"
    }

private val TextDirection.serializedName: String
    get() = when (this) {
        TextDirection.AUTO -> "auto"
        TextDirection.LEFT_TO_RIGHT -> "ltr"
        TextDirection.RIGHT_TO_LEFT -> "rtl"
    }

private val EllipsisPolicy.serializedName: String
    get() = when (this) {
        EllipsisPolicy.NONE -> "none"
        EllipsisPolicy.END -> "end"
    }

private val TextHeightBehavior.serializedName: String
    get() = when (this) {
        TextHeightBehavior.FONT_METRICS -> "font-metrics"
        TextHeightBehavior.STRUT -> "strut"
    }

private val FallbackPreference.serializedName: String
    get() = when (this) {
        FallbackPreference.SYSTEM_DEFAULT -> "system-default"
        FallbackPreference.PREFER_DECLARED_FAMILIES -> "prefer-declared-families"
        FallbackPreference.PREFER_EXACT_TYPEFACE -> "prefer-exact-typeface"
    }

private val FontSlant.serializedName: String
    get() = when (this) {
        FontSlant.UPRIGHT -> "upright"
        FontSlant.ITALIC -> "italic"
        FontSlant.OBLIQUE -> "oblique"
    }

private val SyntheticStylePolicy.serializedName: String
    get() = when (this) {
        SyntheticStylePolicy.ALLOW -> "allow"
        SyntheticStylePolicy.DISALLOW -> "disallow"
    }

private val TextDecorationStyle.serializedName: String
    get() = when (this) {
        TextDecorationStyle.SOLID -> "solid"
        TextDecorationStyle.DOUBLE -> "double"
        TextDecorationStyle.DOTTED -> "dotted"
        TextDecorationStyle.DASHED -> "dashed"
        TextDecorationStyle.WAVY -> "wavy"
    }

private val PlaceholderAlignment.serializedName: String
    get() = when (this) {
        PlaceholderAlignment.BASELINE -> "baseline"
        PlaceholderAlignment.ABOVE_BASELINE -> "above-baseline"
        PlaceholderAlignment.BELOW_BASELINE -> "below-baseline"
        PlaceholderAlignment.TOP -> "top"
        PlaceholderAlignment.BOTTOM -> "bottom"
        PlaceholderAlignment.MIDDLE -> "middle"
    }

private val PlaceholderBaseline.serializedName: String
    get() = when (this) {
        PlaceholderBaseline.ALPHABETIC -> "alphabetic"
        PlaceholderBaseline.IDEOGRAPHIC -> "ideographic"
    }

private fun ParagraphLayoutDiagnostic.toDumpJson(): String = buildString {
    append("{\"code\": ")
        .append(paragraphJsonString(code))
        .append(", \"message\": ")
        .append(paragraphJsonString(message))
        .append(", \"textRange\": ")
        .append(textRange?.toDumpLabel()?.let(::paragraphJsonString) ?: "null")
        .append(", \"severity\": ")
        .append(paragraphJsonString(severity))
        .append("}")
}

private fun Map<String, Int>.toDumpJson(): String =
    entries.sortedBy { it.key }.joinToString(prefix = "[", postfix = "]") { (tag, value) ->
        "{\"tag\": ${paragraphJsonString(tag)}, \"value\": $value}"
    }

private fun List<String>.toParagraphJsonStringArray(): String =
    joinToString(prefix = "[", postfix = "]") { paragraphJsonString(it) }

private fun Map<String, Float>.toVariationJson(): String =
    entries.sortedBy { it.key }.joinToString(prefix = "[", postfix = "]") { (axisTag, value) ->
        "{\"axisTag\": ${paragraphJsonString(axisTag)}, \"value\": ${paragraphJsonFloat(value)}}"
    }

private fun TypefacePaletteSelection.toParagraphJson(): String = buildString {
    append("{\"index\": ")
        .append(index)
        .append(", \"overrides\": ")
        .append(overrides.toParagraphJsonStringArray())
        .append("}")
}

private fun TextDecorationSpec.toParagraphJson(): String = buildString {
    append("{\"underline\": ")
        .append(underline)
        .append(", \"overline\": ")
        .append(overline)
        .append(", \"lineThrough\": ")
        .append(lineThrough)
        .append(", \"style\": ")
        .append(paragraphJsonString(style.serializedName))
        .append(", \"thicknessMultiplier\": ")
        .append(paragraphJsonFloat(thicknessMultiplier))
        .append("}")
}

private fun <V> Set<Map.Entry<IntRange, V>>.sortedByRange(): List<Map.Entry<IntRange, V>> =
    sortedWith(compareBy<Map.Entry<IntRange, V>> { it.key.first }.thenBy { it.key.last })

private fun paragraphDiagnosticOrdering(): Comparator<ParagraphLayoutDiagnostic> =
    compareBy<ParagraphLayoutDiagnostic> { it.textRange?.first ?: Int.MAX_VALUE }
        .thenBy { it.textRange?.last ?: Int.MAX_VALUE }
        .thenBy { it.code }

private fun IntRange.isValidUtf16RangeFor(textLength: Int): Boolean =
    first >= 0 &&
        last >= first &&
        textLength > 0 &&
        last < textLength

private fun String.isStableVariationAxisTag(): Boolean =
    length == 4 && all { character -> character in ' '..'~' }

private fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte ->
        "%02x".format(byte)
    }

private fun <T> StringBuilder.appendParagraphJsonArray(
    values: List<T>,
    entryIndent: String,
    closingIndent: String,
    encode: (T) -> String,
) {
    if (values.isEmpty()) {
        append("[]")
        return
    }
    append("[\n")
    values.forEachIndexed { index, value ->
        append(entryIndent).append(encode(value))
        if (index != values.lastIndex) append(",")
        append("\n")
    }
    append(closingIndent).append("]")
}

private fun paragraphJsonNullableString(value: String?): String =
    value?.let(::paragraphJsonString) ?: "null"

private fun paragraphJsonNullableFloat(value: Float?): String =
    value?.let(::paragraphJsonFloat) ?: "null"

private fun paragraphJsonFloat(value: Float): String =
    if (value.isFinite()) value.toString() else paragraphJsonString(value.toString())

private fun paragraphJsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code in 0x20..0x7E) {
                    append(char)
                } else {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                }
            }
        }
    }
    append('"')
}

private fun IntRange.toDumpLabel(): String = "$first..$last"

private fun ParagraphShapingRequest.intersect(lineRange: IntRange): ParagraphShapingRequest =
    copy(textRange = maxOf(textRange.first, lineRange.first)..minOf(textRange.last, lineRange.last))

private fun String.isStableParagraphDiagnosticToken(): Boolean =
    isNotBlank() && all { char ->
        char in 'a'..'z' || char in '0'..'9' || char == '.' || char == '-' || char == '_'
    }

private fun Paragraph.primaryStyleFor(textRange: IntRange): TextStyle =
    textStyles.entries.firstOrNull { (range) -> range.overlaps(textRange) }?.value ?: TextStyle()

private fun Paragraph.styleAt(index: Int): TextStyle =
    textStyles.entries.firstOrNull { (range) -> index in range }?.value ?: TextStyle()

private fun Paragraph.estimatedWidthAt(index: Int): Float =
    placeholders.entries.firstOrNull { (range) -> index in range }?.value?.width ?: styleAt(index).fontSize

private fun Paragraph.estimatedWidth(range: IntRange): Float =
    placeholders.entries.firstOrNull { (placeholderRange) -> placeholderRange.overlaps(range) }?.value?.width
        ?: styleAt(range.first).fontSize

private fun Paragraph.truncatedRangeAfter(visibleRange: IntRange): IntRange? =
    (visibleRange.last + 1..text.lastIndex).takeIf { visibleRange.last < text.lastIndex }

private fun Paragraph.placeholderWidth(range: IntRange): Float =
    placeholders.entries.firstOrNull { (placeholderRange) -> placeholderRange.overlaps(range) }?.value?.width ?: 0f

private fun PlaceholderStyle.requiresBaseline(): Boolean =
    when (alignment) {
        PlaceholderAlignment.BASELINE,
        PlaceholderAlignment.ABOVE_BASELINE,
        PlaceholderAlignment.BELOW_BASELINE,
        -> true
        PlaceholderAlignment.TOP,
        PlaceholderAlignment.BOTTOM,
        PlaceholderAlignment.MIDDLE,
        -> false
    }

private fun PlaceholderStyle.resolveVerticalBounds(
    lineTop: Float,
    lineBottom: Float,
    baseline: Float,
): PlaceholderBounds =
    when (alignment) {
        PlaceholderAlignment.BASELINE -> PlaceholderBounds(
            top = baseline - baselineOffset,
            bottom = baseline - baselineOffset + height,
        )
        PlaceholderAlignment.ABOVE_BASELINE -> PlaceholderBounds(
            top = baseline - height,
            bottom = baseline,
        )
        PlaceholderAlignment.BELOW_BASELINE -> PlaceholderBounds(
            top = baseline,
            bottom = baseline + height,
        )
        PlaceholderAlignment.TOP -> PlaceholderBounds(
            top = lineTop,
            bottom = lineTop + height,
        )
        PlaceholderAlignment.BOTTOM -> PlaceholderBounds(
            top = lineBottom - height,
            bottom = lineBottom,
        )
        PlaceholderAlignment.MIDDLE -> {
            val center = (lineTop + lineBottom) / 2f
            PlaceholderBounds(
                top = center - (height / 2f),
                bottom = center + (height / 2f),
            )
        }
    }

private fun LineLayout.verticalBounds(): Pair<Float, Float> {
    val lineTop = boxes.firstOrNull()?.top ?: (metrics.baseline + metrics.ascent)
    val lineBottom = boxes.firstOrNull()?.bottom ?: (metrics.baseline + metrics.descent + metrics.leading)
    return lineTop to lineBottom
}

private fun LineLayout.visualSpanBoxes(
    paragraph: Paragraph,
    lineIndex: Int,
    placeholderBoxes: List<PlaceholderBox>,
): List<VisualSpanBox> {
    if (textRange.isEmpty()) return emptyList()
    val bounds = verticalBounds()
    val shapedClusters = glyphRuns
        .flatMap { run -> run.clusters }
        .sortedBy { cluster -> cluster.textRange.first }
    val lineDirection = boxes.firstOrNull()?.direction ?: 1
    val placeholderByRange = placeholderBoxes
        .filter { box -> box.lineIndex == lineIndex }
        .associateBy { box -> box.sourceRange }
    val spans = mutableListOf<VisualSpanBox>()
    var x = 0f
    var index = textRange.first
    while (index <= textRange.last) {
        val shapedCluster = shapedClusters.firstOrNull { cluster ->
            cluster.textRange.first == index && cluster.textRange.overlaps(textRange)
        }
        val clusterRange = shapedCluster?.textRange?.intersect(textRange)
            ?: paragraph.text.clusterRangeAt(index).intersect(textRange)
        val placeholder = placeholderByRange[clusterRange]
        if (placeholder != null) {
            spans += VisualSpanBox(
                sourceRange = placeholder.sourceRange,
                lineIndex = lineIndex,
                left = placeholder.left,
                top = placeholder.top,
                right = placeholder.right,
                bottom = placeholder.bottom,
                direction = lineDirection,
                placeholderId = placeholder.placeholderId,
            )
            x = placeholder.right
        } else {
            val width = shapedCluster?.advanceX ?: paragraph.estimatedWidth(clusterRange)
            spans += VisualSpanBox(
                sourceRange = clusterRange,
                lineIndex = lineIndex,
                left = x,
                top = bounds.first,
                right = x + width,
                bottom = bounds.second,
                direction = lineDirection,
            )
            x += width
        }
        index = clusterRange.last + 1
    }
    return spans
}

private fun LineLayout.applyEllipsis(
    paragraph: Paragraph,
    lineIndex: Int,
    placeholderBoxes: List<PlaceholderBox>,
    visibleRange: IntRange,
    keptSpans: List<VisualSpanBox>,
    truncatedRange: IntRange?,
    ellipsisGlyphRuns: List<ShapedGlyphRun>,
    ellipsisSegmentIds: List<String>,
): Pair<LineLayout, List<PlaceholderBox>> {
    val retainedPlaceholderBoxes = placeholderBoxes.filter { box ->
        box.lineIndex == lineIndex && box.sourceRange.overlaps(visibleRange)
    }
    val retainedGlyphRuns = glyphRuns.mapNotNull { run -> run.trimToVisibleRange(visibleRange) }
    val visibleWidth = keptSpans.sumOf { span -> (span.right - span.left).toDouble() }.toFloat()
    val ellipsisWidth = ellipsisGlyphRuns.sumOf { run -> run.advanceX.toDouble() }.toFloat()
    val direction = boxes.firstOrNull()?.direction ?: 1
    val bounds = verticalBounds()
    return copy(
        textRange = visibleRange,
        glyphRuns = retainedGlyphRuns,
        metrics = metrics.copy(width = visibleWidth + ellipsisWidth),
        boxes = listOf(
            TextBox(
                textRange = visibleRange,
                left = 0f,
                top = bounds.first,
                right = visibleWidth + ellipsisWidth,
                bottom = bounds.second,
                direction = direction,
            ),
        ),
        isEllipsized = true,
        truncatedRange = truncatedRange,
        ellipsisGlyphRuns = ellipsisGlyphRuns,
        ellipsisSegmentIds = ellipsisSegmentIds,
    ) to retainedPlaceholderBoxes
}

private fun LineLayout.ellipsisCaretStop(
    lineIndex: Int,
    lineSpans: List<VisualSpanBox>,
): CaretStop? {
    val bounds = ellipsisBounds(lineSpans) ?: return null
    return CaretStop(
        position = TextPosition(offset = textRange.last + 1, affinity = "upstream"),
        lineIndex = lineIndex,
        x = bounds.second,
        top = verticalBounds().first,
        bottom = verticalBounds().second,
    )
}

private fun LineLayout.ellipsisHitEntry(
    lineIndex: Int,
    lineSpans: List<VisualSpanBox>,
    pointX: Float,
    pointY: Float,
): HitTestEntry? {
    val bounds = ellipsisBounds(lineSpans) ?: return null
    if (pointX < bounds.first || pointX > bounds.second) return null
    return HitTestEntry(
        pointX = pointX,
        pointY = pointY,
        lineIndex = lineIndex,
        position = TextPosition(offset = textRange.last + 1, affinity = "upstream"),
        clusterRange = truncatedRange ?: textRange,
        isInsideText = true,
    )
}

private fun LineLayout.ellipsisBounds(lineSpans: List<VisualSpanBox>): Pair<Float, Float>? {
    if (!isEllipsized) return null
    val ellipsisWidth = ellipsisGlyphRuns.sumOf { run -> run.advanceX.toDouble() }.toFloat()
    if (ellipsisWidth <= 0f) return null
    val visibleRight = lineSpans.maxOfOrNull { span -> span.right } ?: (metrics.width - ellipsisWidth)
    return visibleRight to visibleRight + ellipsisWidth
}

private fun ShapedGlyphRun.trimToVisibleRange(visibleRange: IntRange): ShapedGlyphRun? {
    val retainedClusters = clusters.filter { cluster -> cluster.textRange.overlaps(visibleRange) }
    if (retainedClusters.isEmpty()) return null
    val retainedGlyphIds = mutableListOf<Int>()
    val remappedClusters = retainedClusters.map { cluster ->
        val start = retainedGlyphIds.size
        retainedGlyphIds += glyphIds.subList(cluster.glyphRange.first, cluster.glyphRange.last + 1)
        val end = retainedGlyphIds.lastIndex
        cluster.copy(
            textRange = cluster.textRange.intersect(visibleRange),
            glyphRange = start..end,
        )
    }
    return copy(
        glyphIds = retainedGlyphIds,
        clusters = remappedClusters,
        advanceX = remappedClusters.sumOf { cluster -> cluster.advanceX.toDouble() }.toFloat(),
    )
}

private fun VisualSpanBox.toHitTestEntry(
    pointX: Float,
    pointY: Float,
    isInsideText: Boolean,
): HitTestEntry {
    val midpoint = (left + right) / 2f
    val position = if (pointX < midpoint) {
        TextPosition(offset = sourceRange.first, affinity = "downstream")
    } else {
        TextPosition(offset = sourceRange.last + 1, affinity = "upstream")
    }
    return HitTestEntry(
        pointX = pointX,
        pointY = pointY,
        lineIndex = lineIndex,
        position = position,
        clusterRange = sourceRange,
        placeholderId = placeholderId,
        isInsideText = isInsideText,
    )
}

private fun List<VisualSpanBox>.containingSpan(
    pointX: Float,
    pointY: Float,
): VisualSpanBox? =
    filter { span ->
        pointX >= span.left &&
            pointX <= span.right &&
            pointY >= span.top &&
            pointY <= span.bottom
    }.minWithOrNull(
        compareBy<VisualSpanBox> { (it.right - it.left) * (it.bottom - it.top) }
            .thenBy { it.lineIndex },
    )

private fun Paragraph.unshapedEstimatedWidth(
    textRange: IntRange,
    coveredRanges: List<IntRange>,
): Float {
    var width = 0f
    var index = textRange.first
    while (index <= textRange.last) {
        val cluster = text.clusterRangeAt(index).intersect(textRange)
        if (coveredRanges.none { range -> range.overlaps(cluster) }) {
            width += estimatedWidth(cluster)
        }
        index = cluster.last + 1
    }
    return width
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun IntRange.intersect(other: IntRange): IntRange =
    maxOf(first, other.first)..minOf(last, other.last)

private fun IntRange.overlapsSelection(
    selectionStart: Int,
    selectionEndExclusive: Int,
): Boolean =
    first < selectionEndExclusive && selectionStart <= last

private fun List<SelectionBox>.mergeAdjacentTextSelectionBoxes(): List<SelectionBox> {
    if (isEmpty()) return this
    val merged = mutableListOf<SelectionBox>()
    forEach { box ->
        val previous = merged.lastOrNull()
        if (previous != null &&
            previous.placeholderId == null &&
            box.placeholderId == null &&
            previous.lineIndex == box.lineIndex &&
            previous.direction == box.direction &&
            previous.top == box.top &&
            previous.bottom == box.bottom &&
            previous.right == box.left
        ) {
            merged[merged.lastIndex] = previous.copy(
                sourceRange = previous.sourceRange.first..box.sourceRange.last,
                right = box.right,
            )
        } else {
            merged += box
        }
    }
    return merged
}

private fun resolvePlaceholderBoxes(
    paragraph: Paragraph,
    textRange: IntRange,
    lineIndex: Int,
    lineTop: Float,
    lineBottom: Float,
    baseline: Float,
    glyphRuns: List<ShapedGlyphRun>,
    placeholderIds: Map<IntRange, String>,
): List<PlaceholderBox> {
    if (paragraph.placeholders.isEmpty()) return emptyList()
    val shapedAdvances = glyphRuns
        .flatMap { run -> run.clusters }
        .sortedBy { cluster -> cluster.textRange.first }
    val boxes = mutableListOf<PlaceholderBox>()
    var x = 0f
    var index = textRange.first
    while (index <= textRange.last) {
        val shapedCluster = shapedAdvances.firstOrNull { cluster ->
            cluster.textRange.first == index && cluster.textRange.overlaps(textRange)
        }
        val cluster = shapedCluster?.textRange?.intersect(textRange)
            ?: paragraph.text.clusterRangeAt(index).intersect(textRange)
        val placeholderEntry = paragraph.placeholders.entries.firstOrNull { (range) -> range.overlaps(cluster) }
        if (placeholderEntry != null) {
            val (range, style) = placeholderEntry
            val bounds = style.resolveVerticalBounds(
                lineTop = lineTop,
                lineBottom = lineBottom,
                baseline = baseline,
            )
            boxes += PlaceholderBox(
                placeholderId = placeholderIds.getValue(range),
                sourceRange = range,
                lineIndex = lineIndex,
                left = x,
                top = bounds.top,
                right = x + style.width,
                bottom = bounds.bottom,
                baselineOffset = baseline - bounds.top,
                alignment = style.alignment.serializedName,
                baseline = style.baseline?.serializedName,
                participatesInLineHeight = style.participatesInLineHeight,
            )
            x += style.width
        } else {
            x += shapedCluster?.advanceX ?: paragraph.estimatedWidth(cluster)
        }
        index = cluster.last + 1
    }
    return boxes
}

private data class PlaceholderBounds(
    val top: Float,
    val bottom: Float,
)

private data class EllipsisRun(
    val segmentId: String,
    val glyphRuns: List<ShapedGlyphRun>,
    val width: Float,
)

private data class EllipsisResolution(
    val line: LineLayout? = null,
    val placeholderBoxes: List<PlaceholderBox> = emptyList(),
    val diagnostic: ParagraphLayoutDiagnostic? = null,
)

private data class VisualSpanBox(
    val sourceRange: IntRange,
    val lineIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val direction: Int,
    val placeholderId: String? = null,
)

private data class IndexedLineBounds(
    val lineIndex: Int,
    val top: Float,
    val bottom: Float,
)

private data class SelectionBounds(
    val start: Int,
    val endExclusive: Int,
)

private fun String.clusterRangeAt(index: Int): IntRange {
    val firstCodePointEnd = codePointEndAt(index)
    var clusterEnd = firstCodePointEnd
    var nextIndex = clusterEnd + 1
    while (nextIndex < length) {
        val nextCodePoint = codePointAt(nextIndex)
        if (!nextCodePoint.isCombiningMarkOrDefaultIgnorable()) break
        clusterEnd = codePointEndAt(nextIndex)
        nextIndex = clusterEnd + 1
    }
    return index..clusterEnd
}

private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    return if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        Character.toCodePoint(high, this[index + 1])
    } else {
        high.code
    }
}

private fun String.codePointEndAt(index: Int): Int =
    if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) index + 1 else index

private fun Int.isCombiningMarkOrDefaultIgnorable(): Boolean =
    this in 0x0300..0x036F ||
        this in 0x1AB0..0x1AFF ||
        this in 0x1DC0..0x1DFF ||
        this in 0x20D0..0x20FF ||
        this in 0xFE20..0xFE2F ||
        this == 0x200C ||
        this == 0x200D ||
        this in 0xFE00..0xFE0F ||
        this in 0xE0100..0xE01EF
