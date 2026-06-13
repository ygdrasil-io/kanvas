package org.graphiks.kanvas.text.paragraph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.text.shaping.ShapedGlyphRun

/**
 * Incrementally builds immutable paragraph input for layout.
 *
 * @property paragraphStyle Default paragraph-wide style applied to the built paragraph.
 */
public class ParagraphBuilder(
    public val paragraphStyle: ParagraphStyle = ParagraphStyle(),
) {
    /**
     * Appends [text] with [style] to this builder.
     */
    public fun append(text: String, style: TextStyle = TextStyle()): ParagraphBuilder =
        TODO("Append styled text to the paragraph builder.")

    /**
     * Appends a placeholder span with [style] to this builder.
     */
    public fun appendPlaceholder(style: PlaceholderStyle): ParagraphBuilder =
        TODO("Append a placeholder to the paragraph builder.")

    /**
     * Produces an immutable [Paragraph] snapshot from the current builder contents.
     */
    public fun build(): Paragraph = TODO("Build an immutable paragraph from accumulated content.")
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
     * Lays out [paragraph] within [maxWidth] logical pixels.
     */
    public fun layout(paragraph: Paragraph, maxWidth: Float): ParagraphLayoutResult =
        TODO("Layout the paragraph into shaped lines.")
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
 */
public data class ParagraphLayoutResult(
    public val paragraph: Paragraph,
    public val lines: List<LineLayout> = emptyList(),
    public val width: Float = 0f,
    public val height: Float = 0f,
    public val didOverflowHeight: Boolean = false,
    public val didOverflowWidth: Boolean = false,
)

/**
 * Computes legal line break ranges for paragraph layout.
 */
public interface LineBreaker {
    /**
     * Breaks [paragraph] into inclusive UTF-16 ranges that can become lines within [maxWidth].
     */
    public fun breakLines(paragraph: Paragraph, maxWidth: Float): List<IntRange> =
        TODO("Break paragraph text into line ranges.")
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
