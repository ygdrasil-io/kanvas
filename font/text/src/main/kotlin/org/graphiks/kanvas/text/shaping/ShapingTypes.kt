package org.graphiks.kanvas.text.shaping

import org.graphiks.kanvas.font.TypefaceID

/**
 * Describes one text shaping operation for the pure Kotlin text stack.
 *
 * @property text UTF-16 input text owned by the caller.
 * @property textRange Inclusive range inside [text] that must be shaped.
 * @property typefaceId Stable typeface identifier selected by font resolution
 * when the caller wants to shape against a specific face.
 * @property fontSize Text size in logical pixels.
 * @property features OpenType feature choices to apply during shaping.
 * @property locale Optional BCP 47 locale tag used for language-sensitive shaping.
 * @property paragraphDirection Requested paragraph direction, where negative means RTL, positive means LTR, and zero means auto.
 */
public data class ShapingRequest(
    public val text: String,
    public val textRange: IntRange = text.indices,
    public val typefaceId: TypefaceID? = null,
    public val fontSize: Float = 12f,
    public val features: FeatureSet = FeatureSet(),
    public val locale: String? = null,
    public val paragraphDirection: Int = 0,
)

/**
 * Carries the complete output of shaping a [ShapingRequest].
 *
 * @property glyphRuns Ordered glyph runs produced for script, direction, font, or fallback boundaries.
 * @property diagnostics Non-fatal shaping diagnostics gathered while resolving the request.
 */
public data class ShapingResult(
    public val glyphRuns: List<ShapedGlyphRun> = emptyList(),
    public val diagnostics: List<ShapingDiagnostic> = emptyList(),
)

/**
 * Represents one positioned run of glyphs that share shaping state.
 *
 * @property glyphIds Font-specific glyph identifiers in visual order for this run.
 * @property clusters Logical text clusters mapped to the emitted glyphs.
 * @property advanceX Total horizontal advance in logical pixels.
 * @property advanceY Total vertical advance in logical pixels for vertical writing support.
 * @property script ISO 15924 script tag associated with this run.
 * @property bidiLevel Unicode Bidirectional Algorithm embedding level for the run.
 * @property typefaceId Stable typeface identifier used to produce the glyph ids.
 */
public data class ShapedGlyphRun(
    public val glyphIds: List<Int> = emptyList(),
    public val clusters: List<GlyphCluster> = emptyList(),
    public val advanceX: Float = 0f,
    public val advanceY: Float = 0f,
    public val script: String = "Zyyy",
    public val bidiLevel: Int = 0,
    public val typefaceId: TypefaceID? = null,
)

/**
 * Maps one logical text cluster to one or more glyphs in a shaped run.
 *
 * @property textRange Inclusive UTF-16 range covered by the cluster in the original text.
 * @property glyphRange Inclusive range of glyph indices covered by the cluster in its run.
 * @property advanceX Horizontal advance contributed by this cluster.
 * @property offsetX Horizontal placement adjustment for the cluster origin.
 * @property offsetY Vertical placement adjustment for the cluster origin.
 */
public data class GlyphCluster(
    public val textRange: IntRange,
    public val glyphRange: IntRange,
    public val advanceX: Float = 0f,
    public val offsetX: Float = 0f,
    public val offsetY: Float = 0f,
)

/**
 * Holds OpenType feature selections for a shaping request.
 *
 * @property values Map from four-character OpenType feature tags to integer feature values.
 */
public data class FeatureSet(
    public val values: Map<String, Int> = emptyMap(),
)

/**
 * Describes a contiguous text range that shares one Unicode script.
 *
 * @property textRange Inclusive UTF-16 range covered by the script run.
 * @property script ISO 15924 script tag such as `Latn`, `Arab`, or `Zyyy`.
 */
public data class ScriptRun(
    public val textRange: IntRange,
    public val script: String,
)

/**
 * Describes a contiguous text range that shares one resolved bidi level.
 *
 * @property textRange Inclusive UTF-16 range covered by the bidi run.
 * @property level Unicode Bidirectional Algorithm embedding level.
 * @property isRightToLeft True when [level] resolves to right-to-left visual order.
 */
public data class BidiRun(
    public val textRange: IntRange,
    public val level: Int,
    public val isRightToLeft: Boolean,
)

/**
 * Provides Unicode classification data required by segmentation, bidi resolution, and script itemization.
 */
public interface UnicodeData {
    /**
     * Returns the ISO 15924 script tag for [codePoint].
     */
    public fun scriptOf(codePoint: Int): String = TODO("Return Unicode script data for the code point.")

    /**
     * Returns the Unicode bidi class for [codePoint].
     */
    public fun bidiClassOf(codePoint: Int): String = TODO("Return Unicode bidi class data for the code point.")

    /**
     * Returns true when [codePoint] has the Default_Ignorable_Code_Point property.
     */
    public fun isDefaultIgnorable(codePoint: Int): Boolean = TODO("Return Unicode default-ignorable property data.")
}

/**
 * Splits text into shaping-safe ranges such as grapheme, word, and sentence boundaries.
 */
public interface TextSegmenter {
    /**
     * Segments [text] and returns inclusive UTF-16 ranges for the requested segmentation mode.
     */
    public fun segment(text: String): List<IntRange> = TODO("Segment text into Unicode boundary ranges.")
}

/**
 * Resolves logical text into bidi runs using Unicode bidi data and paragraph direction.
 */
public interface BidiResolver {
    /**
     * Resolves bidi runs for [request].
     */
    public fun resolve(request: ShapingRequest): List<BidiRun> = TODO("Resolve Unicode bidi runs for the shaping request.")
}

/**
 * Groups text into script runs before OpenType shaping.
 */
public interface ScriptItemizer {
    /**
     * Itemizes [request] into script runs.
     */
    public fun itemize(request: ShapingRequest): List<ScriptRun> = TODO("Itemize text into Unicode script runs.")
}

/**
 * Coordinates Unicode analysis, OpenType substitution, OpenType positioning, and emoji shaping.
 */
public interface OpenTypeShapingEngine {
    /**
     * Shapes [request] and returns positioned glyph runs.
     */
    public fun shape(request: ShapingRequest): ShapingResult = TODO("Shape text with the pure Kotlin OpenType pipeline.")
}

/**
 * Applies OpenType GSUB glyph substitution lookups.
 */
public class GSUBEngine {
    /**
     * Applies glyph substitutions for [run] with [features].
     */
    public fun substitute(run: ShapedGlyphRun, features: FeatureSet): ShapedGlyphRun =
        TODO("Apply OpenType GSUB substitutions to the glyph run.")
}

/**
 * Applies OpenType GPOS glyph positioning lookups.
 */
public class GPOSEngine {
    /**
     * Applies glyph positioning for [run] with [features].
     */
    public fun position(run: ShapedGlyphRun, features: FeatureSet): ShapedGlyphRun =
        TODO("Apply OpenType GPOS positioning to the glyph run.")
}

/**
 * Stores OpenType GDEF metadata needed by GSUB and GPOS.
 *
 * @property glyphClasses Map from glyph id to OpenType glyph class.
 * @property markAttachmentClasses Map from glyph id to mark attachment class.
 * @property ligatureCaretPositions Map from glyph id to caret x positions for ligature editing.
 */
public data class GDEFData(
    public val glyphClasses: Map<Int, Int> = emptyMap(),
    public val markAttachmentClasses: Map<Int, Int> = emptyMap(),
    public val ligatureCaretPositions: Map<Int, List<Float>> = emptyMap(),
)

/**
 * Detects emoji sequences and maps them to emoji glyph clusters when a font supports them.
 */
public class EmojiSequenceShaper {
    /**
     * Shapes emoji sequences in [request].
     */
    public fun shapeEmoji(request: ShapingRequest): List<GlyphCluster> =
        TODO("Shape Unicode emoji sequences into glyph clusters.")
}

/**
 * Records a non-fatal event observed during shaping.
 *
 * @property code Stable diagnostic code for tooling and tests.
 * @property message Human-readable diagnostic message.
 * @property textRange Optional inclusive UTF-16 range associated with the diagnostic.
 */
public data class ShapingDiagnostic(
    public val code: String,
    public val message: String,
    public val textRange: IntRange? = null,
)
