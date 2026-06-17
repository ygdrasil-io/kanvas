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
 * @property softWrap True when optional line breaks may be used during fitting.
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
    public val baseline: PlaceholderBaseline = PlaceholderBaseline.ALPHABETIC,
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
    private val paragraphShapingSegmenter: ParagraphShapingSegmenter = BasicParagraphShapingSegmenter(),
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

        val brokenRanges = lineBreaker.breakLines(paragraph, maxWidth)
        val maxLines = paragraph.paragraphStyle.maxLines
        val visibleRanges = if (maxLines == null) brokenRanges else brokenRanges.take(maxLines)
        val didOverflowHeight = maxLines != null && brokenRanges.size > visibleRanges.size
        val diagnostics = mutableListOf<ParagraphLayoutDiagnostic>()
        if (didOverflowHeight && paragraph.paragraphStyle.ellipsis != null) {
            diagnostics += ParagraphLayoutDiagnostic(
                code = PARAGRAPH_LAYOUT_MAX_LINES_ELLIPSIS_UNSUPPORTED_DIAGNOSTIC_CODE,
                message = "maxLines ellipsis is not implemented by the current paragraph engine.",
                textRange = hiddenTextRange(visibleRanges, brokenRanges),
                severity = "refusal",
            )
        }

        var y = 0f
        var paragraphWidth = 0f
        val lines = visibleRanges.map { textRange ->
            val style = paragraph.primaryStyleFor(textRange)
            val shapingPlan = paragraphShapingSegmenter.segment(paragraph, textRange)
            diagnostics += shapingPlan.diagnostics
            val glyphRuns = mutableListOf<ShapedGlyphRun>()
            shapingPlan.requests.forEach { request ->
                val shapingResult = shapingEngine.shape(request.toShapingRequest(paragraph.text))
                glyphRuns += shapingResult.glyphRuns
                diagnostics += shapingResult.diagnostics.map { diagnostic -> diagnostic.toParagraphLayoutDiagnostic() }
            }
            val lineWidth = glyphRuns.sumOf { it.advanceX.toDouble() }.toFloat() +
                shapingPlan.placeholderRanges.sumOf { placeholderRange ->
                    paragraph.placeholderWidthAt(placeholderRange.first).toDouble()
                }.toFloat()
            val lineHeight = paragraph.paragraphStyle.lineHeight ?: style.fontSize
            val ascent = -style.fontSize * ASCENT_FRACTION
            val descent = style.fontSize * DESCENT_FRACTION
            val metrics = LineMetrics(
                ascent = ascent,
                descent = descent,
                leading = lineHeight - style.fontSize,
                width = lineWidth,
                baseline = y - ascent,
            )
            val direction = if ((glyphRuns.firstOrNull()?.bidiLevel ?: 0) % 2 == 0) 1 else -1
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

            y += lineHeight
            paragraphWidth = maxOf(paragraphWidth, lineWidth)
            LineLayout(
                textRange = textRange,
                glyphRuns = glyphRuns,
                metrics = metrics,
                boxes = boxes,
                segmentRefs = shapingPlan.requests.map { request -> request.textRange.toDumpLabel() },
            )
        }

        return ParagraphLayoutResult(
            paragraph = paragraph,
            lines = lines,
            maxWidth = maxWidth,
            width = paragraphWidth,
            height = y,
            didOverflowHeight = didOverflowHeight,
            didOverflowWidth = lines.any { it.metrics.width > maxWidth },
            diagnostics = diagnostics,
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
 * Deterministic bounded UAX #14 line breaker for paragraph layout.
 *
 * The public surface stays `SimpleLineBreaker` for compatibility, but the
 * implementation now delegates to [Uax14LineBreaker] so paragraph fitting uses
 * pinned Unicode line-break classes instead of whitespace-only heuristics.
 */
public class SimpleLineBreaker : LineBreaker {
    private val delegate: Uax14LineBreaker = Uax14LineBreaker()

    /**
     * Breaks [paragraph] into greedy line ranges constrained by finite, non-negative [maxWidth].
     */
    override fun breakLines(paragraph: Paragraph, maxWidth: Float): List<IntRange> =
        delegate.breakLines(paragraph, maxWidth)
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
    public val glyphRuns: List<ShapedGlyphRun> = emptyList(),
    public val metrics: LineMetrics = LineMetrics(),
    public val boxes: List<TextBox> = emptyList(),
    public val segmentRefs: List<String> = emptyList(),
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
        if (placeholder.baseline != PlaceholderBaseline.ALPHABETIC) {
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

private fun hiddenTextRange(
    visibleRanges: List<IntRange>,
    brokenRanges: List<IntRange>,
): IntRange? {
    val firstHidden = brokenRanges.getOrNull(visibleRanges.size)?.first ?: return null
    val lastHidden = brokenRanges.lastOrNull()?.last ?: return null
    return if (firstHidden <= lastHidden) firstHidden..lastHidden else null
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
        .append(paragraphJsonString(baseline.serializedName))
        .append("}")
}

private fun LineLayout.toDumpJson(index: Int): String = buildString {
    append("{\"index\": ")
        .append(index)
        .append(", \"textRange\": ")
        .append(paragraphJsonString(textRange.toDumpLabel()))
        .append(", \"metrics\": ")
        .append(metrics.toDumpJson())
        .append(", \"boxes\": ")
        .append(boxes.joinToString(prefix = "[", postfix = "]") { box -> box.toDumpJson() })
        .append(", \"segmentRefs\": ")
        .append(segmentRefs.joinToString(prefix = "[", postfix = "]") { segmentRef -> paragraphJsonString(segmentRef) })
        .append(", \"glyphRunCount\": ")
        .append(glyphRuns.size)
        .append("}")
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

private fun Paragraph.placeholderWidthAt(index: Int): Float =
    placeholders.entries.firstOrNull { (range) -> index in range }?.value?.width ?: 0f

private fun Paragraph.estimatedWidth(range: IntRange): Float =
    placeholders.entries.firstOrNull { (placeholderRange) -> placeholderRange.overlaps(range) }?.value?.width
        ?: styleAt(range.first).fontSize

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

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
