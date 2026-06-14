package org.graphiks.kanvas.text.paragraph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.OpenTypeShapingEngine
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
)

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
    public val textAlign: String = "start",
    public val textDirection: Int = 0,
    public val maxLines: Int? = null,
    public val ellipsis: String? = null,
    public val lineHeight: Float? = null,
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
    public val typefaceId: TypefaceID? = null,
    public val fontSize: Float = 12f,
    public val colorRgba: Long = 0x000000ff,
    public val locale: String? = null,
    public val features: Map<String, Int> = emptyMap(),
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
    public val alignment: String = "baseline",
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
            val shapingResult = shapingEngine.shape(
                ShapingRequest(
                    text = paragraph.text,
                    textRange = textRange,
                    typefaceId = style.typefaceId,
                    fontSize = style.fontSize,
                    features = FeatureSet(style.features),
                    locale = style.locale,
                    paragraphDirection = paragraph.paragraphStyle.textDirection,
                ),
            )
            val glyphRuns = shapingResult.glyphRuns
            diagnostics += shapingResult.diagnostics.map { diagnostic -> diagnostic.toParagraphLayoutDiagnostic() }
            val lineWidth = glyphRuns.sumOf { it.advanceX.toDouble() }.toFloat()
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
        append("  \"input\": {\n")
        append("    \"text\": ").append(paragraphJsonString(paragraph.text)).append(",\n")
        append("    \"textLength\": ").append(paragraph.text.length).append(",\n")
        append("    \"paragraphStyle\": ").append(paragraph.paragraphStyle.toDumpJson()).append(",\n")
        append("    \"textStyles\": ")
        appendParagraphJsonArray(
            values = paragraph.textStyles.entries.sortedByRange(),
            entryIndent = "      ",
            closingIndent = "    ",
        ) { (range, style) ->
            style.toDumpJson(range)
        }
        append(",\n")
        append("    \"placeholders\": ")
        appendParagraphJsonArray(
            values = paragraph.placeholders.entries.sortedByRange(),
            entryIndent = "      ",
            closingIndent = "    ",
        ) { (range, placeholder) ->
            placeholder.toDumpJson(range)
        }
        append("\n")
        append("  },\n")
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
 * Deterministic whitespace line breaker for early paragraph layout.
 *
 * This breaker is not a Unicode Line Breaking Algorithm (UAX #14)
 * implementation. It treats `\n` as a hard break, prefers soft breaks at ASCII
 * spaces, skips spaces consumed as break separators, and estimates character
 * width from the active [TextStyle.fontSize]. Inline placeholders use their
 * [PlaceholderStyle.width] when the current UTF-16 index is mapped as a
 * placeholder range. It walks UTF-16 text by basic clusters: surrogate pairs
 * are kept intact, combining marks and default-ignorable format/variation
 * characters attach to the preceding code point, and a single oversized cluster
 * is emitted whole. The breaker returns inclusive UTF-16 ranges and omits the
 * newline or separator space that caused a break.
 */
public class SimpleLineBreaker : LineBreaker {
    /**
     * Breaks [paragraph] into greedy line ranges constrained by finite, non-negative [maxWidth].
     */
    override fun breakLines(paragraph: Paragraph, maxWidth: Float): List<IntRange> {
        requireValidMaxWidth(maxWidth)
        val text = paragraph.text
        if (text.isEmpty()) return emptyList()

        val ranges = mutableListOf<IntRange>()
        var lineStart = 0
        while (lineStart < text.length) {
            while (lineStart < text.length && text[lineStart] == ' ') lineStart += 1
            if (lineStart >= text.length) break
            if (text[lineStart] == '\n') {
                lineStart += 1
                continue
            }

            var width = 0f
            var index = lineStart
            var lastSoftBreakEnd = -1
            var emitted = false
            while (index < text.length && !emitted) {
                val cluster = text.clusterRangeAt(index)
                val char = text[index]
                if (char == '\n') {
                    if (index > lineStart) ranges += lineStart until index
                    lineStart = index + 1
                    emitted = true
                    continue
                }

                val nextWidth = width + paragraph.estimatedWidth(cluster)
                if (nextWidth > maxWidth && index > lineStart) {
                    val lineEnd = if (char == ' ') index - 1 else lastSoftBreakEnd.takeIf { it >= lineStart } ?: index - 1
                    ranges += lineStart..lineEnd
                    lineStart = lineEnd + 1
                    while (lineStart < text.length && text[lineStart] == ' ') lineStart += 1
                    emitted = true
                    continue
                }

                width = nextWidth
                if (char == ' ' && index > lineStart) lastSoftBreakEnd = index - 1
                index = cluster.last + 1
            }

            if (!emitted) {
                if (lineStart < text.length) ranges += lineStart until text.length
                lineStart = text.length
            }
        }

        return ranges
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
    public val glyphRuns: List<ShapedGlyphRun> = emptyList(),
    public val metrics: LineMetrics = LineMetrics(),
    public val boxes: List<TextBox> = emptyList(),
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
        .append(paragraphJsonString(textAlign))
        .append(", \"textDirection\": ")
        .append(textDirection)
        .append(", \"maxLines\": ")
        .append(maxLines?.toString() ?: "null")
        .append(", \"ellipsis\": ")
        .append(paragraphJsonNullableString(ellipsis))
        .append(", \"lineHeight\": ")
        .append(paragraphJsonNullableFloat(lineHeight))
        .append("}")
}

private fun TextStyle.toDumpJson(range: IntRange): String = buildString {
    append("{\"range\": ")
        .append(paragraphJsonString(range.toDumpLabel()))
        .append(", \"typefaceId\": ")
        .append(typefaceId?.value?.toString()?.let(::paragraphJsonString) ?: "null")
        .append(", \"fontSize\": ")
        .append(paragraphJsonFloat(fontSize))
        .append(", \"locale\": ")
        .append(paragraphJsonNullableString(locale))
        .append(", \"features\": ")
        .append(features.toDumpJson())
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
        .append(paragraphJsonString(alignment))
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

private fun <V> Set<Map.Entry<IntRange, V>>.sortedByRange(): List<Map.Entry<IntRange, V>> =
    sortedWith(compareBy<Map.Entry<IntRange, V>> { it.key.first }.thenBy { it.key.last })

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
