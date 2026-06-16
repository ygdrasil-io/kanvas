package org.graphiks.kanvas.text.shaping

import org.graphiks.kanvas.font.FallbackRequest
import org.graphiks.kanvas.font.FontResolver
import org.graphiks.kanvas.font.ResolvedFontRun
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.sfnt.CMapTable
import org.graphiks.kanvas.font.sfnt.OpenTypeGposPairTable
import org.graphiks.kanvas.font.sfnt.OpenTypeKernTable

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
 * @property preferredFamilies Ordered font family names passed to fallback resolution.
 */
public data class ShapingRequest(
    public val text: String,
    public val textRange: IntRange = text.indices,
    public val typefaceId: TypefaceID? = null,
    public val fontSize: Float = 12f,
    public val features: FeatureSet = FeatureSet(),
    public val locale: String? = null,
    public val paragraphDirection: Int = 0,
    public val preferredFamilies: List<String> = emptyList(),
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
 * @property fontSize Text size in logical pixels used for deterministic advances.
 */
public data class ShapedGlyphRun(
    public val glyphIds: List<Int> = emptyList(),
    public val clusters: List<GlyphCluster> = emptyList(),
    public val advanceX: Float = 0f,
    public val advanceY: Float = 0f,
    public val script: String = "Zyyy",
    public val bidiLevel: Int = 0,
    public val typefaceId: TypefaceID? = null,
    public val fontSize: Float = 12f,
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
    public fun scriptOf(codePoint: Int): String

    /**
     * Returns the Unicode bidi class for [codePoint].
     */
    public fun bidiClassOf(codePoint: Int): String

    /**
     * Returns true when [codePoint] has the Default_Ignorable_Code_Point property.
     */
    public fun isDefaultIgnorable(codePoint: Int): Boolean
}

/**
 * Minimal built-in Unicode data used by the first pure Kotlin shaping primitives.
 *
 * This table is intentionally bounded. It covers Latin, Hebrew, Arabic,
 * Common, Inherited combining marks, a base emoji symbol range, and common
 * default-ignorable code points needed by early shaping work. It is not a
 * replacement for the complete Unicode Character Database.
 */
public object BasicUnicodeData : UnicodeData {
    /**
     * Returns a bounded ISO 15924-style script tag for [codePoint].
     */
    override fun scriptOf(codePoint: Int): String =
        when {
            isCombiningMark(codePoint) || isVariationSelector(codePoint) -> SCRIPT_INHERITED
            isBaseEmoji(codePoint) -> SCRIPT_EMOJI
            isLatin(codePoint) -> SCRIPT_LATIN
            isHebrew(codePoint) -> SCRIPT_HEBREW
            isArabic(codePoint) -> SCRIPT_ARABIC
            else -> SCRIPT_COMMON
        }

    /**
     * Returns a small subset of Unicode bidi classes for [codePoint].
     */
    override fun bidiClassOf(codePoint: Int): String =
        when {
            isDefaultIgnorable(codePoint) -> BIDI_BOUNDARY_NEUTRAL
            isCombiningMark(codePoint) -> BIDI_NONSPACING_MARK
            isLatin(codePoint) -> BIDI_LEFT_TO_RIGHT
            isHebrew(codePoint) -> BIDI_RIGHT_TO_LEFT
            isArabicNumber(codePoint) -> BIDI_ARABIC_NUMBER
            isArabic(codePoint) -> BIDI_ARABIC_LETTER
            isEuropeanNumber(codePoint) -> BIDI_EUROPEAN_NUMBER
            isParagraphSeparator(codePoint) -> BIDI_PARAGRAPH_SEPARATOR
            isSegmentSeparator(codePoint) -> BIDI_SEGMENT_SEPARATOR
            isWhitespace(codePoint) -> BIDI_WHITESPACE
            else -> BIDI_OTHER_NEUTRAL
        }

    /**
     * Returns true for common Default_Ignorable_Code_Point ranges.
     */
    override fun isDefaultIgnorable(codePoint: Int): Boolean =
        codePoint == 0x00AD ||
            codePoint == 0x034F ||
            codePoint == 0x061C ||
            codePoint in 0x115F..0x1160 ||
            codePoint in 0x17B4..0x17B5 ||
            codePoint in 0x180B..0x180F ||
            codePoint in 0x200B..0x200F ||
            codePoint in 0x202A..0x202E ||
            codePoint in 0x2060..0x206F ||
            codePoint == 0x3164 ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint == 0xFEFF ||
            codePoint in 0xFFF0..0xFFF8 ||
            codePoint in 0x1BCA0..0x1BCA3 ||
            codePoint in 0x1D173..0x1D17A ||
            codePoint in 0xE0000..0xE0FFF
}

/**
 * Splits text into shaping-safe ranges such as grapheme, word, and sentence boundaries.
 */
public interface TextSegmenter {
    /**
     * Segments [text] and returns inclusive UTF-16 ranges for the requested segmentation mode.
     */
    public fun segment(text: String): List<IntRange>
}

/**
 * Segments text into pinned KFONT-M5-002 grapheme clusters for early shaping.
 *
 * The default constructor delegates to [DefaultGraphemeTextSegmenter]. The
 * [UnicodeData] constructor keeps the previous conservative code point
 * clustering available for compatibility in callers that explicitly request a
 * bounded legacy Unicode data source.
 *
 * @param delegate Text segmenter used by this compatibility facade.
 */
public class BasicTextSegmenter(
    private val delegate: TextSegmenter = DefaultGraphemeTextSegmenter,
) : TextSegmenter {
    public constructor(unicodeData: UnicodeData) : this(LegacyCodePointTextSegmenter(unicodeData))

    /**
     * Returns inclusive UTF-16 ranges for grapheme clusters.
     */
    override fun segment(text: String): List<IntRange> =
        delegate.segment(text)
}

private class LegacyCodePointTextSegmenter(
    private val unicodeData: UnicodeData,
) : TextSegmenter {
    override fun segment(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        for (codePointRange in codePointRanges(text)) {
            if (ranges.isNotEmpty() && shouldAttachToPreviousCluster(codePointRange.codePoint)) {
                val previous = ranges.removeAt(ranges.lastIndex)
                ranges += previous.first..codePointRange.textRange.last
            } else {
                ranges += codePointRange.textRange
            }
        }
        return ranges
    }

    private fun shouldAttachToPreviousCluster(codePoint: Int): Boolean =
        isCombiningMark(codePoint) || unicodeData.isDefaultIgnorable(codePoint)
}

/**
 * Resolves logical text into bidi runs using Unicode bidi data and paragraph direction.
 */
public interface BidiResolver {
    /**
     * Resolves bidi runs for [request].
     */
    public fun resolve(request: ShapingRequest): List<BidiRun>
}

/**
 * Resolves a bounded set of LTR/RTL runs for the pure Kotlin text stack.
 *
 * This resolver is intentionally not a complete Unicode Bidirectional
 * Algorithm (UAX #9) implementation. It recognizes basic strong LTR/RTL
 * classes, uses [ShapingRequest.paragraphDirection] for the base direction,
 * and attaches neutral text to adjacent strong runs when possible.
 *
 * @param unicodeData Unicode data source used for bidi classification.
 */
public class BasicBidiResolver(
    private val unicodeData: UnicodeData = BasicUnicodeData,
) : BidiResolver {
    /**
     * Resolves coarse bidi runs for [request].
     */
    override fun resolve(request: ShapingRequest): List<BidiRun> {
        val codePoints = codePointRangesFor(request)
        if (codePoints.isEmpty()) return emptyList()

        val baseDirection = baseDirectionFor(request, codePoints)
        val pendingNeutralRanges = mutableListOf<IntRange>()
        val runs = mutableListOf<BidiRun>()
        var currentDirection: TextDirection? = null
        var currentStart = -1
        var currentEnd = -1

        for (codePointRange in codePoints) {
            val strongDirection = strongDirectionOf(codePointRange.codePoint)
            if (strongDirection == null) {
                if (currentDirection == null) {
                    pendingNeutralRanges += codePointRange.textRange
                } else {
                    currentEnd = codePointRange.textRange.last
                }
                continue
            }

            if (currentDirection == null) {
                currentDirection = strongDirection
                currentStart = pendingNeutralRanges.firstOrNull()?.first ?: codePointRange.textRange.first
                currentEnd = codePointRange.textRange.last
                pendingNeutralRanges.clear()
            } else if (currentDirection == strongDirection) {
                currentEnd = codePointRange.textRange.last
            } else {
                runs += bidiRun(currentStart..currentEnd, currentDirection, baseDirection)
                currentDirection = strongDirection
                currentStart = codePointRange.textRange.first
                currentEnd = codePointRange.textRange.last
            }
        }

        if (currentDirection == null) {
            val textRange = pendingNeutralRanges.first().first..pendingNeutralRanges.last().last
            return listOf(bidiRun(textRange, baseDirection, baseDirection))
        }

        runs += bidiRun(currentStart..currentEnd, currentDirection, baseDirection)
        return runs
    }

    private fun baseDirectionFor(request: ShapingRequest, codePoints: List<CodePointRange>): TextDirection =
        when {
            request.paragraphDirection < 0 -> TextDirection.RightToLeft
            request.paragraphDirection > 0 -> TextDirection.LeftToRight
            else -> codePoints.firstNotNullOfOrNull { strongDirectionOf(it.codePoint) } ?: TextDirection.LeftToRight
        }

    private fun strongDirectionOf(codePoint: Int): TextDirection? =
        when (unicodeData.bidiClassOf(codePoint)) {
            BIDI_LEFT_TO_RIGHT -> TextDirection.LeftToRight
            BIDI_RIGHT_TO_LEFT,
            BIDI_ARABIC_LETTER,
            -> TextDirection.RightToLeft
            else -> null
        }
}

/**
 * Groups text into script runs before OpenType shaping.
 */
public interface ScriptItemizer {
    /**
     * Itemizes [request] into script runs.
     */
    public fun itemize(request: ShapingRequest): List<ScriptRun>
}

/**
 * Groups text into bounded script runs for early OpenType shaping.
 *
 * This itemizer is not a complete Unicode Script_Extensions implementation.
 * It groups strong scripts from [UnicodeData.scriptOf] and attaches Common or
 * Inherited code points to the previous strong script, or to the next strong
 * script for leading neutral text.
 *
 * @param unicodeData Unicode data source used for script classification.
 */
public class BasicScriptItemizer(
    private val unicodeData: UnicodeData = BasicUnicodeData,
) : ScriptItemizer {
    /**
     * Returns coarse script runs for [request].
     */
    override fun itemize(request: ShapingRequest): List<ScriptRun> {
        val codePoints = codePointRangesFor(request)
        if (codePoints.isEmpty()) return emptyList()

        val pendingNeutralRanges = mutableListOf<IntRange>()
        val runs = mutableListOf<ScriptRun>()
        var currentScript: String? = null
        var currentStart = -1
        var currentEnd = -1

        for (codePointRange in codePoints) {
            val script = unicodeData.scriptOf(codePointRange.codePoint)
            if (!script.isStrongScript()) {
                if (currentScript == null) {
                    pendingNeutralRanges += codePointRange.textRange
                } else {
                    currentEnd = codePointRange.textRange.last
                }
                continue
            }

            if (currentScript == null) {
                currentScript = script
                currentStart = pendingNeutralRanges.firstOrNull()?.first ?: codePointRange.textRange.first
                currentEnd = codePointRange.textRange.last
                pendingNeutralRanges.clear()
            } else if (currentScript == script) {
                currentEnd = codePointRange.textRange.last
            } else {
                runs += ScriptRun(currentStart..currentEnd, currentScript)
                currentScript = script
                currentStart = codePointRange.textRange.first
                currentEnd = codePointRange.textRange.last
            }
        }

        if (currentScript == null) {
            val fallbackScript = fallbackNeutralScript(codePoints)
            val textRange = pendingNeutralRanges.first().first..pendingNeutralRanges.last().last
            return listOf(ScriptRun(textRange, fallbackScript))
        }

        runs += ScriptRun(currentStart..currentEnd, currentScript)
        return runs
    }

    private fun fallbackNeutralScript(codePoints: List<CodePointRange>): String {
        val scripts = codePoints.map { unicodeData.scriptOf(it.codePoint) }
        return if (scripts.all { it == SCRIPT_INHERITED }) SCRIPT_INHERITED else SCRIPT_COMMON
    }
}

/**
 * Maps a Unicode code point to a glyph id for a resolved typeface.
 *
 * The mapper is intentionally injectable so early pure Kotlin shaping can use
 * deterministic test maps, parsed SFNT cmap readers, or later font fallback
 * sources without changing the shaping engine.
 */
public interface GlyphMapper {
    /**
     * Returns the glyph id for [codePoint] in [typefaceId], or null when the
     * typeface has no supported glyph for that code point.
     */
    public fun glyphIdFor(typefaceId: TypefaceID?, codePoint: Int): Int?
}

/**
 * Maps Unicode code points through parsed SFNT `cmap` tables.
 *
 * When [typefaceId][GlyphMapper.glyphIdFor] is non-null, the mapper uses only
 * the matching table from [cmapsByTypefaceId] because glyph ids are specific to
 * that resolved face. [defaultCMap] is used only for null typeface requests.
 * The mapper returns `null` when no eligible table is available or when the
 * selected table resolves the code point to `.notdef` glyph ID `0`.
 *
 * @param cmapsByTypefaceId Parsed `cmap` tables keyed by resolved typeface identifier.
 * @param defaultCMap Optional `cmap` table used only when no typeface id is supplied.
 */
public class CMapGlyphMapper(
    private val cmapsByTypefaceId: Map<TypefaceID, CMapTable> = emptyMap(),
    private val defaultCMap: CMapTable? = null,
) : GlyphMapper {
    /**
     * Returns the SFNT glyph id for [codePoint] in [typefaceId], or `null` when unavailable.
     */
    override fun glyphIdFor(typefaceId: TypefaceID?, codePoint: Int): Int? {
        val cmap = if (typefaceId == null) {
            defaultCMap
        } else {
            cmapsByTypefaceId[typefaceId]
        } ?: return null
        val glyphId = cmap.lookupGlyphId(codePoint) ?: return null
        return glyphId.takeIf { it != 0 }
    }
}

/**
 * Coordinates Unicode analysis, OpenType substitution, OpenType positioning, and emoji shaping.
 */
public interface OpenTypeShapingEngine {
    /**
     * Shapes [request] and returns positioned glyph runs.
     */
    public fun shape(request: ShapingRequest): ShapingResult
}

/**
 * Produces deterministic glyph runs from the bounded pure Kotlin text primitives.
 *
 * This engine coordinates [TextSegmenter], [ScriptItemizer], [BidiResolver],
 * and [GlyphMapper]. It preserves UTF-16 cluster ranges from segmentation,
 * expands ranges that would otherwise split surrogate pairs, maps each code
 * point through [glyphMapper], and assigns one [ShapingRequest.fontSize]
 * advance per cluster before optional pair-position adjustments. It does not
 * implement full Unicode segmentation, the full Unicode Bidirectional
 * Algorithm, full GSUB, full GPOS, or emoji sequence shaping.
 *
 * Pair kerning is intentionally opt-in. Supplying [kernTablesByTypefaceId]
 * makes parsed legacy OpenType `kern` format `0` pairs available for matching
 * [ShapingRequest.typefaceId] values. Supplying [gposPairTablesByTypefaceId]
 * makes the bounded Kanvas GPOS `kern` pair-position subset available for the
 * same matching policy. Supplying [kernUnitsPerEmByTypefaceId] for the same
 * typeface converts signed font-unit adjustments to logical pixel adjustments
 * using `request.fontSize / unitsPerEm`. The adjustment is applied to the
 * advance of the cluster that owns the left glyph in each adjacent glyph pair,
 * so following glyph origins move through normal advance accumulation. GPOS
 * pairs take precedence over legacy `kern` pairs for the same typeface.
 *
 * @param glyphMapper Code point to glyph id mapper for the selected typeface.
 * @param segmenter Text segmenter used to form conservative shaping clusters.
 * @param scriptItemizer Script itemizer used to group clusters by script.
 * @param bidiResolver Bidi resolver used to assign embedding levels.
 * @param missingGlyphId Glyph id emitted when [glyphMapper] has no mapping.
 * @param kernTablesByTypefaceId Parsed OpenType `kern` tables keyed by the
 * resolved typeface that produced their glyph IDs. Only matching request
 * typefaces participate in kerning.
 * @param gposPairTablesByTypefaceId Parsed bounded OpenType GPOS pair tables
 * keyed by the resolved typeface that produced their glyph IDs. These tables
 * take precedence over legacy `kern` tables when both are present.
 * @param kernUnitsPerEmByTypefaceId Design units per em for the parsed `kern`
 * and GPOS pair tables keyed by typeface. A table present without a positive
 * units-per-em entry is left unapplied and reported through
 * [KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE].
 */
public class BasicOpenTypeShapingEngine(
    private val glyphMapper: GlyphMapper,
    private val segmenter: TextSegmenter = BasicTextSegmenter(),
    private val scriptItemizer: ScriptItemizer = BasicScriptItemizer(),
    private val bidiResolver: BidiResolver = BasicBidiResolver(),
    private val missingGlyphId: Int = 0,
    private val kernTablesByTypefaceId: Map<TypefaceID, OpenTypeKernTable> = emptyMap(),
    private val gposPairTablesByTypefaceId: Map<TypefaceID, OpenTypeGposPairTable> = emptyMap(),
    private val kernUnitsPerEmByTypefaceId: Map<TypefaceID, Int> = emptyMap(),
) : OpenTypeShapingEngine {
    /**
     * Shapes [request] into bounded script/bidi glyph runs.
     */
    override fun shape(request: ShapingRequest): ShapingResult {
        val requestedTextRange = codePointSafeTextRange(request.text, request.textRange) ?: return ShapingResult()
        val textRange = clusterSafeTextRange(request.text, requestedTextRange)
        val scopedRequest = request.copy(textRange = textRange)
        val scriptRuns = scriptItemizer.itemize(scopedRequest)
        val bidiRuns = bidiResolver.resolve(scopedRequest)
        val shapingClusters = shapingClustersFor(request.text, textRange, scriptRuns, bidiRuns)
        if (shapingClusters.isEmpty()) return ShapingResult()

        val diagnostics = mutableListOf<ShapingDiagnostic>()
        val glyphRuns = shapingClusters
            .groupByShapingState()
            .map { group -> shapeGroup(request, group, diagnostics) }

        return ShapingResult(glyphRuns = glyphRuns, diagnostics = diagnostics)
    }

    private fun clusterSafeTextRange(text: String, requestedTextRange: IntRange): IntRange {
        val overlappingClusters = segmenter.segment(text)
            .mapNotNull { clusterRange -> codePointSafeTextRange(text, clusterRange) }
            .filter { clusterRange -> clusterRange.overlaps(requestedTextRange) }

        return if (overlappingClusters.isEmpty()) {
            requestedTextRange
        } else {
            overlappingClusters.minOf { it.first }..overlappingClusters.maxOf { it.last }
        }
    }

    private fun shapingClustersFor(
        text: String,
        textRange: IntRange,
        scriptRuns: List<ScriptRun>,
        bidiRuns: List<BidiRun>,
    ): List<BasicShapingCluster> =
        segmenter.segment(text)
            .mapNotNull { clusterRange -> codePointSafeTextRange(text, clusterRange) }
            .filter { clusterRange -> clusterRange.overlaps(textRange) }
            .distinct()
            .sortedBy { clusterRange -> clusterRange.first }
            .map { clusterRange ->
                BasicShapingCluster(
                    textRange = clusterRange,
                    script = scriptRuns.firstOrNull { it.textRange.overlaps(clusterRange) }?.script ?: SCRIPT_COMMON,
                    bidiRun = bidiRuns.firstOrNull { it.textRange.overlaps(clusterRange) }
                        ?: BidiRun(clusterRange, level = 0, isRightToLeft = false),
                )
            }

    private fun List<BasicShapingCluster>.groupByShapingState(): List<BasicShapingGroup> {
        val groups = mutableListOf<BasicShapingGroup>()
        for (cluster in this) {
            val previous = groups.lastOrNull()
            if (previous != null && previous.canAppend(cluster)) {
                previous.clusterRanges += cluster.textRange
            } else {
                groups += BasicShapingGroup(
                    script = cluster.script,
                    bidiLevel = cluster.bidiRun.level,
                    isRightToLeft = cluster.bidiRun.isRightToLeft,
                    clusterRanges = mutableListOf(cluster.textRange),
                )
            }
        }
        return groups
    }

    private fun shapeGroup(
        request: ShapingRequest,
        group: BasicShapingGroup,
        diagnostics: MutableList<ShapingDiagnostic>,
    ): ShapedGlyphRun {
        val glyphIds = mutableListOf<Int>()
        val clusters = mutableListOf<GlyphCluster>()
        val clusterRanges = if (group.isRightToLeft) group.clusterRanges.asReversed() else group.clusterRanges

        var clusterIndex = 0
        while (clusterIndex < clusterRanges.size) {
            val clusterRange = clusterRanges[clusterIndex]
            val glyphStart = glyphIds.size
            val standardLigature = standardLigatureAt(request, clusterRanges, clusterIndex)
            if (standardLigature != null) {
                glyphIds += standardLigature.glyphId
                clusters += GlyphCluster(
                    textRange = standardLigature.textRange,
                    glyphRange = glyphStart..glyphStart,
                    advanceX = request.fontSize,
                )
                clusterIndex += standardLigature.clusterCount
                continue
            }

            for (codePointRange in codePointRanges(request.text, clusterRange)) {
                val glyphId = glyphMapper.glyphIdFor(request.typefaceId, codePointRange.codePoint)
                if (glyphId == null) {
                    diagnostics += missingGlyphDiagnostic(codePointRange)
                }
                glyphIds += glyphId ?: missingGlyphId
            }
            clusters += GlyphCluster(
                textRange = clusterRange,
                glyphRange = glyphStart..glyphIds.lastIndex,
                advanceX = request.fontSize,
            )
            clusterIndex += 1
        }

        val kernAdvanceAdjustment = applyKernPairs(request, group, glyphIds, clusters, diagnostics)

        return ShapedGlyphRun(
            glyphIds = glyphIds,
            clusters = clusters,
            advanceX = (clusters.size.toDouble() * request.fontSize.toDouble() + kernAdvanceAdjustment).toFloat(),
            advanceY = 0f,
            script = group.script,
            bidiLevel = group.bidiLevel,
            typefaceId = request.typefaceId,
            fontSize = request.fontSize,
        )
    }

    private fun missingGlyphDiagnostic(codePointRange: CodePointRange): ShapingDiagnostic =
        ShapingDiagnostic(
            code = MISSING_GLYPH_DIAGNOSTIC_CODE,
            message = "Missing glyph for U+${codePointRange.codePoint.toUpperHex()}.",
            textRange = codePointRange.textRange,
        )

    private fun standardLigatureAt(
        request: ShapingRequest,
        clusterRanges: List<IntRange>,
        clusterIndex: Int,
    ): StandardLigature? {
        if (request.features.values["liga"] == 0) return null
        if (clusterIndex + 1 >= clusterRanges.size) return null

        val first = singleCodePointRange(request.text, clusterRanges[clusterIndex]) ?: return null
        val second = singleCodePointRange(request.text, clusterRanges[clusterIndex + 1]) ?: return null
        if (first.codePoint != LATIN_SMALL_F_CODE_POINT || second.codePoint != LATIN_SMALL_I_CODE_POINT) {
            return null
        }

        val glyphId = glyphMapper.glyphIdFor(request.typefaceId, LATIN_SMALL_FI_LIGATURE_CODE_POINT)
            ?: return null
        return StandardLigature(
            glyphId = glyphId,
            textRange = first.textRange.first..second.textRange.last,
            clusterCount = 2,
        )
    }

    private fun applyKernPairs(
        request: ShapingRequest,
        group: BasicShapingGroup,
        glyphIds: List<Int>,
        clusters: MutableList<GlyphCluster>,
        diagnostics: MutableList<ShapingDiagnostic>,
    ): Double {
        if (glyphIds.size < 2 || (kernTablesByTypefaceId.isEmpty() && gposPairTablesByTypefaceId.isEmpty())) return 0.0

        val kernContext = pairAdjustmentContextFor(request, group.textRange(), diagnostics) ?: return 0.0
        val glyphClusterIndexes = glyphClusterIndexes(clusters, glyphIds.size)
        var totalAdvanceAdjustment = 0.0
        for (leftGlyphIndex in 0 until glyphIds.lastIndex) {
            val leftGlyphId = glyphIds[leftGlyphIndex]
            val rightGlyphId = glyphIds[leftGlyphIndex + 1]
            val adjustmentUnits = kernContext.lookupKerningAdjustment(
                leftGlyphId = leftGlyphId,
                rightGlyphId = rightGlyphId,
                textRange = clusters.getOrNull(glyphClusterIndexes[leftGlyphIndex])?.textRange,
                diagnostics = diagnostics,
            )
            if (adjustmentUnits == 0) continue

            val clusterIndex = glyphClusterIndexes[leftGlyphIndex]
            if (clusterIndex < 0) continue

            val adjustment = adjustmentUnits.toDouble() * kernContext.fontUnitsToFontSizeUnitsScale
            val cluster = clusters[clusterIndex]
            clusters[clusterIndex] = cluster.copy(advanceX = cluster.advanceX + adjustment.toFloat())
            totalAdvanceAdjustment += adjustment
        }
        return totalAdvanceAdjustment
    }

    private fun pairAdjustmentContextFor(
        request: ShapingRequest,
        textRange: IntRange,
        diagnostics: MutableList<ShapingDiagnostic>,
    ): BasicPairAdjustmentContext? {
        val typefaceId = request.typefaceId
        if (typefaceId == null) {
            diagnostics += ShapingDiagnostic(
                code = KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE,
                message = "Pair-position tables cannot apply because request typefaceId is missing.",
                textRange = textRange,
            )
            return null
        }

        val gposTable = gposPairTablesByTypefaceId[typefaceId]
        val kernTable = kernTablesByTypefaceId[typefaceId]
        if (gposTable == null && kernTable == null) return null
        val tableLabel = pairAdjustmentTableLabel(kernTable = kernTable, gposPairTable = gposTable)

        val unitsPerEm = kernUnitsPerEmByTypefaceId[typefaceId]
        if (unitsPerEm == null) {
            diagnostics += ShapingDiagnostic(
                code = KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE,
                message = "$tableLabel for typeface ${typefaceId.value} cannot apply because unitsPerEm is missing.",
                textRange = textRange,
            )
            return null
        }
        if (unitsPerEm <= 0) {
            diagnostics += ShapingDiagnostic(
                code = KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE,
                message = "$tableLabel for typeface ${typefaceId.value} cannot apply because unitsPerEm $unitsPerEm is not positive.",
                textRange = textRange,
            )
            return null
        }

        return BasicPairAdjustmentContext(
            typefaceId = typefaceId,
            kernTable = kernTable,
            gposPairTable = gposTable,
            tableLabel = tableLabel,
            fontUnitsToFontSizeUnitsScale = request.fontSize.toDouble() / unitsPerEm.toDouble(),
        )
    }

    private fun glyphClusterIndexes(clusters: List<GlyphCluster>, glyphCount: Int): IntArray {
        val indexes = IntArray(glyphCount) { -1 }
        clusters.forEachIndexed { clusterIndex, cluster ->
            for (glyphIndex in cluster.glyphRange) {
                if (glyphIndex in indexes.indices) {
                    indexes[glyphIndex] = clusterIndex
                }
            }
        }
        return indexes
    }

    private fun BasicPairAdjustmentContext.lookupKerningAdjustment(
        leftGlyphId: Int,
        rightGlyphId: Int,
        textRange: IntRange?,
        diagnostics: MutableList<ShapingDiagnostic>,
    ): Int =
        try {
            gposPairTable?.lookupXAdvanceAdjustment(leftGlyphId, rightGlyphId)
                ?: kernTable?.lookupKerningAdjustment(leftGlyphId, rightGlyphId)
                ?: 0
        } catch (error: IllegalArgumentException) {
            diagnostics += ShapingDiagnostic(
                code = KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE,
                message = "$tableLabel for typeface ${typefaceId.value} cannot apply to glyph pair $leftGlyphId,$rightGlyphId: ${error.message}",
                textRange = textRange,
            )
            0
        }
}

/**
 * Shapes text through font fallback runs resolved by the pure Kotlin font core.
 *
 * The engine asks [fontResolver] for concrete [TypefaceID] assignments over the
 * requested text slice, then shapes each resolved run with an internal
 * [BasicOpenTypeShapingEngine] configured from [glyphMapper]. Supplying a
 * [CMapGlyphMapper] connects resolved faces to parsed SFNT `cmap` tables while
 * keeping this layer independent of renderers, scalers, glyph storage, and GPU
 * backends. The original [ShapingRequest.text] is always passed to the
 * delegate with absolute UTF-16 ranges, so emitted [GlyphCluster.textRange]
 * values remain in caller coordinates.
 *
 * Resolver output is normalized to [segmenter] cluster boundaries before
 * delegation. When a resolver splits a base plus combining-mark cluster across
 * multiple runs, the cluster is shaped once with the first covering typeface
 * and adjacent clusters with the same selected typeface are merged back into a
 * single delegated range. If different typefaces cover the same expanded
 * cluster, [CONFLICTING_FONT_RUN_DIAGNOSTIC_CODE] records the conflict and the
 * later conflicting typefaces are skipped for that cluster.
 *
 * Resolved font runs use [ResolvedFontRun.start] and [ResolvedFontRun.end] as
 * offsets relative to the fallback request text. Those offsets are intersected
 * with the cluster-expanded shaping range, sorted, and consumed without
 * duplicating overlapping resolver output. Gaps that no resolver run covers
 * produce [UNRESOLVED_FONT_RUN_DIAGNOSTIC_CODE] diagnostics and no glyphs. Both
 * unresolved fallback runs and `.notdef` glyph mapping use the same stable
 * `text.shaping.fallback-missing` diagnostic family; callers that need the
 * narrower cause must inspect the diagnostic message and text range.
 *
 * @param fontResolver Font core resolver used to assign concrete fallback font
 * runs for the requested text slice.
 * @param glyphMapper Code point to glyph id mapper used by the delegated
 * [BasicOpenTypeShapingEngine]. Use [CMapGlyphMapper] when parsed SFNT `cmap`
 * tables are available.
 * @param segmenter Text segmenter passed to the delegated shaping engine.
 * @param scriptItemizer Script itemizer passed to the delegated shaping engine.
 * @param bidiResolver Bidi resolver passed to the delegated shaping engine.
 * @param missingGlyphId Glyph id emitted by the delegated shaping engine when a
 * resolved font lacks a glyph mapping.
 * @param kernTablesByTypefaceId Parsed OpenType `kern` tables passed to the
 * delegated shaping engine by resolved typeface id.
 * @param kernUnitsPerEmByTypefaceId Design units per em passed to the delegated
 * shaping engine for scaling kern table values into font-size units.
 */
public class FallbackOpenTypeShapingEngine(
    private val fontResolver: FontResolver,
    glyphMapper: GlyphMapper,
    private val segmenter: TextSegmenter = BasicTextSegmenter(),
    scriptItemizer: ScriptItemizer = BasicScriptItemizer(),
    bidiResolver: BidiResolver = BasicBidiResolver(),
    missingGlyphId: Int = 0,
    kernTablesByTypefaceId: Map<TypefaceID, OpenTypeKernTable> = emptyMap(),
    gposPairTablesByTypefaceId: Map<TypefaceID, OpenTypeGposPairTable> = emptyMap(),
    kernUnitsPerEmByTypefaceId: Map<TypefaceID, Int> = emptyMap(),
) : OpenTypeShapingEngine {
    private val delegate = BasicOpenTypeShapingEngine(
        glyphMapper = glyphMapper,
        segmenter = segmenter,
        scriptItemizer = scriptItemizer,
        bidiResolver = bidiResolver,
        missingGlyphId = missingGlyphId,
        kernTablesByTypefaceId = kernTablesByTypefaceId,
        gposPairTablesByTypefaceId = gposPairTablesByTypefaceId,
        kernUnitsPerEmByTypefaceId = kernUnitsPerEmByTypefaceId,
    )

    /**
     * Resolves fallback font runs for [request] and shapes each resolved run.
     */
    override fun shape(request: ShapingRequest): ShapingResult {
        val requestedTextRange = codePointSafeTextRange(request.text, request.textRange) ?: return ShapingResult()
        val clusterTextRange = clusterSafeTextRange(request.text, requestedTextRange)
        val fallbackRequest = FallbackRequest(
            text = request.text.substring(clusterTextRange.first, clusterTextRange.last + 1),
            locale = request.locale,
            preferredFamilies = request.preferredFamilies,
        )

        val glyphRuns = mutableListOf<ShapedGlyphRun>()
        val diagnostics = mutableListOf<ShapingDiagnostic>()
        val rawResolvedRuns = fontResolver.resolve(fallbackRequest)
            .mapIndexedNotNull { order, run -> run.toAbsoluteShapingRun(clusterTextRange, order) }
        val resolvedRuns = clusterSafeResolvedRuns(request.text, clusterTextRange, rawResolvedRuns, diagnostics)
        var cursor = clusterTextRange.first

        for (run in resolvedRuns) {
            if (run.textRange.last < cursor) continue

            val runStart = maxOf(run.textRange.first, cursor)
            if (cursor < runStart) {
                diagnostics += unresolvedFontRunDiagnostic(cursor untilExclusive runStart)
            }

            val runRange = runStart..run.textRange.last
            val runResult = delegate.shape(
                request.copy(
                    textRange = runRange,
                    typefaceId = run.typefaceId,
                ),
            )
            glyphRuns += runResult.glyphRuns
            diagnostics += runResult.diagnostics
            cursor = run.textRange.last + 1
        }

        if (cursor <= clusterTextRange.last) {
            diagnostics += unresolvedFontRunDiagnostic(cursor..clusterTextRange.last)
        }

        return ShapingResult(glyphRuns = glyphRuns, diagnostics = diagnostics)
    }

    private fun clusterSafeTextRange(text: String, requestedTextRange: IntRange): IntRange {
        val overlappingClusters = clusterRanges(text, requestedTextRange)
        return if (overlappingClusters.isEmpty()) {
            requestedTextRange
        } else {
            overlappingClusters.minOf { it.first }..overlappingClusters.maxOf { it.last }
        }
    }

    private fun clusterRanges(text: String, textRange: IntRange): List<IntRange> =
        segmenter.segment(text)
            .mapNotNull { clusterRange -> codePointSafeTextRange(text, clusterRange) }
            .filter { clusterRange -> clusterRange.overlaps(textRange) }
            .distinct()
            .sortedBy { clusterRange -> clusterRange.first }

    private fun clusterSafeResolvedRuns(
        text: String,
        textRange: IntRange,
        rawRuns: List<ResolvedShapingFontRun>,
        diagnostics: MutableList<ShapingDiagnostic>,
    ): List<ResolvedShapingFontRun> {
        val resolvedRuns = mutableListOf<ResolvedShapingFontRun>()
        val clusters = clusterRanges(text, textRange).ifEmpty { listOf(textRange) }

        for (clusterRange in clusters) {
            val coveringRuns = rawRuns
                .filter { run -> run.textRange.overlaps(clusterRange) }
                .sortedWith(compareBy<ResolvedShapingFontRun> { it.textRange.first }.thenBy { it.order })
            if (coveringRuns.isEmpty()) continue

            val selectedRun = coveringRuns.first()
            val conflictingTypefaceIds = coveringRuns
                .map { run -> run.typefaceId }
                .distinct()
                .filter { typefaceId -> typefaceId != selectedRun.typefaceId }
            if (conflictingTypefaceIds.isNotEmpty()) {
                diagnostics += conflictingFontRunDiagnostic(clusterRange, selectedRun.typefaceId, conflictingTypefaceIds)
            }

            resolvedRuns.appendCluster(clusterRange, selectedRun.typefaceId, selectedRun.order)
        }

        return resolvedRuns
    }

    private fun MutableList<ResolvedShapingFontRun>.appendCluster(
        clusterRange: IntRange,
        typefaceId: TypefaceID,
        order: Int,
    ) {
        val previous = lastOrNull()
        if (previous != null &&
            previous.typefaceId == typefaceId &&
            previous.textRange.last + 1 == clusterRange.first
        ) {
            this[lastIndex] = previous.copy(textRange = previous.textRange.first..clusterRange.last)
        } else {
            this += ResolvedShapingFontRun(
                textRange = clusterRange,
                typefaceId = typefaceId,
                order = order,
            )
        }
    }

    private fun ResolvedFontRun.toAbsoluteShapingRun(
        requestedTextRange: IntRange,
        order: Int,
    ): ResolvedShapingFontRun? {
        if (end <= start) return null

        val absoluteStart = requestedTextRange.first + start
        val absoluteEnd = requestedTextRange.first + end - 1
        val first = absoluteStart.coerceAtLeast(requestedTextRange.first)
        val last = absoluteEnd.coerceAtMost(requestedTextRange.last)
        if (first > last) return null

        return ResolvedShapingFontRun(
            textRange = first..last,
            typefaceId = face.typeface.id,
            order = order,
        )
    }

    private infix fun Int.untilExclusive(endExclusive: Int): IntRange =
        this..(endExclusive - 1)

    private fun unresolvedFontRunDiagnostic(textRange: IntRange): ShapingDiagnostic =
        ShapingDiagnostic(
            code = UNRESOLVED_FONT_RUN_DIAGNOSTIC_CODE,
            message = "No resolved font for text range ${textRange.first}..${textRange.last}.",
            textRange = textRange,
        )

    private fun conflictingFontRunDiagnostic(
        textRange: IntRange,
        selectedTypefaceId: TypefaceID,
        skippedTypefaceIds: List<TypefaceID>,
    ): ShapingDiagnostic {
        val skipped = skippedTypefaceIds.joinToString(", ") { typefaceId -> typefaceId.value.toString() }
        return ShapingDiagnostic(
            code = CONFLICTING_FONT_RUN_DIAGNOSTIC_CODE,
            message = "Conflicting resolved fonts for cluster ${textRange.first}..${textRange.last}; using typeface ${selectedTypefaceId.value} and skipping $skipped.",
            textRange = textRange,
        )
    }
}

/**
 * Applies OpenType GSUB glyph substitution lookups.
 *
 * This boundary is intentionally conservative until parsed lookup tables are
 * wired into the text stack. Calling [substitute] without concrete lookup data
 * returns [run] unchanged instead of throwing at runtime or pretending to apply
 * full OpenType substitution.
 */
public class GSUBEngine {
    /**
     * Applies glyph substitutions for [run] with [features].
     */
    public fun substitute(run: ShapedGlyphRun, features: FeatureSet): ShapedGlyphRun =
        run
}

/**
 * Applies OpenType GPOS glyph positioning lookups.
 *
 * This boundary is intentionally conservative until parsed lookup tables are
 * wired into the text stack. Calling [position] without concrete lookup data
 * returns [run] unchanged instead of throwing at runtime or inventing glyph
 * offsets.
 */
public class GPOSEngine {
    /**
     * Applies glyph positioning for [run] with [features].
     */
    public fun position(run: ShapedGlyphRun, features: FeatureSet): ShapedGlyphRun =
        run
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
 *
 * This shaper does not perform font-specific emoji substitution. It detects
 * bounded emoji-style clusters that later GSUB/COLR/bitmap routing can map to
 * actual glyphs: a base emoji with optional variation selectors, followed by
 * zero or more ZWJ plus base emoji parts with their own optional variation
 * selectors. Non-emoji text is skipped.
 */
public class EmojiSequenceShaper {
    /**
     * Shapes emoji sequences in [request].
     */
    public fun shapeEmoji(request: ShapingRequest): List<GlyphCluster> {
        val textRange = codePointSafeTextRange(request.text, request.textRange) ?: return emptyList()
        val codePoints = codePointRanges(request.text, textRange)
        val clusters = mutableListOf<GlyphCluster>()
        var index = 0

        while (index < codePoints.size) {
            val sequence = emojiSequenceAt(codePoints, index)
            if (sequence == null) {
                index += 1
                continue
            }

            clusters += GlyphCluster(
                textRange = sequence.textRange,
                glyphRange = clusters.size..clusters.size,
                advanceX = request.fontSize,
            )
            index = sequence.nextIndex
        }

        return clusters
    }

    private fun emojiSequenceAt(codePoints: List<CodePointRange>, startIndex: Int): EmojiSequence? {
        val first = codePoints.getOrNull(startIndex) ?: return null
        if (!isBaseEmoji(first.codePoint)) return null

        var index = consumeEmojiComponent(codePoints, startIndex) ?: return null
        var last = codePoints[index - 1].textRange.last

        while (index + 1 < codePoints.size && codePoints[index].codePoint == ZERO_WIDTH_JOINER) {
            val nextComponentEnd = consumeEmojiComponent(codePoints, index + 1) ?: break
            last = codePoints[nextComponentEnd - 1].textRange.last
            index = nextComponentEnd
        }

        return EmojiSequence(
            textRange = first.textRange.first..last,
            nextIndex = index,
        )
    }

    private fun consumeEmojiComponent(codePoints: List<CodePointRange>, startIndex: Int): Int? {
        if (!isBaseEmoji(codePoints.getOrNull(startIndex)?.codePoint ?: return null)) return null
        var index = startIndex + 1
        while (index < codePoints.size && isVariationSelector(codePoints[index].codePoint)) {
            index += 1
        }
        if (index < codePoints.size && isEmojiModifier(codePoints[index].codePoint)) {
            index += 1
        }
        while (index < codePoints.size && isVariationSelector(codePoints[index].codePoint)) {
            index += 1
        }
        return index
    }
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

/**
 * Stable spec diagnostic family emitted when glyph or fallback font coverage is missing.
 */
public const val TEXT_SHAPING_FALLBACK_MISSING_DIAGNOSTIC_CODE: String = "text.shaping.fallback-missing"

/**
 * Stable spec diagnostic family emitted when a requested shaping feature cannot be applied.
 */
public const val TEXT_SHAPING_FEATURE_UNSUPPORTED_DIAGNOSTIC_CODE: String = "text.shaping.feature-unsupported"

/**
 * Stable spec diagnostic family emitted when shaping cluster invariants are violated.
 */
public const val TEXT_SHAPING_CLUSTER_INVARIANT_FAILED_DIAGNOSTIC_CODE: String =
    "text.shaping.cluster-invariant-failed"

/**
 * Semantic alias emitted when glyph mapping falls back to `.notdef`.
 */
public const val MISSING_GLYPH_DIAGNOSTIC_CODE: String = TEXT_SHAPING_FALLBACK_MISSING_DIAGNOSTIC_CODE

/**
 * Semantic alias emitted when font fallback leaves a text range without a resolved font.
 */
public const val UNRESOLVED_FONT_RUN_DIAGNOSTIC_CODE: String = TEXT_SHAPING_FALLBACK_MISSING_DIAGNOSTIC_CODE

/**
 * Semantic alias emitted when an opt-in OpenType pair-position table is present but cannot be applied.
 */
public const val KERN_TABLE_UNAPPLIED_DIAGNOSTIC_CODE: String = TEXT_SHAPING_FEATURE_UNSUPPORTED_DIAGNOSTIC_CODE

/**
 * Semantic alias emitted when font fallback assigns different typefaces to the same shaping cluster.
 */
public const val CONFLICTING_FONT_RUN_DIAGNOSTIC_CODE: String =
    TEXT_SHAPING_CLUSTER_INVARIANT_FAILED_DIAGNOSTIC_CODE

private const val SCRIPT_LATIN = "Latn"
private const val SCRIPT_ARABIC = "Arab"
private const val SCRIPT_HEBREW = "Hebr"
private const val SCRIPT_COMMON = "Zyyy"
private const val SCRIPT_INHERITED = "Zinh"
private const val SCRIPT_EMOJI = "Zsye"
private const val LATIN_SMALL_F_CODE_POINT = 0x0066
private const val LATIN_SMALL_I_CODE_POINT = 0x0069
private const val LATIN_SMALL_FI_LIGATURE_CODE_POINT = 0xFB01
private const val ZERO_WIDTH_JOINER = 0x200D

private const val BIDI_LEFT_TO_RIGHT = "L"
private const val BIDI_RIGHT_TO_LEFT = "R"
private const val BIDI_ARABIC_LETTER = "AL"
private const val BIDI_EUROPEAN_NUMBER = "EN"
private const val BIDI_ARABIC_NUMBER = "AN"
private const val BIDI_NONSPACING_MARK = "NSM"
private const val BIDI_BOUNDARY_NEUTRAL = "BN"
private const val BIDI_PARAGRAPH_SEPARATOR = "B"
private const val BIDI_SEGMENT_SEPARATOR = "S"
private const val BIDI_WHITESPACE = "WS"
private const val BIDI_OTHER_NEUTRAL = "ON"

private data class CodePointRange(
    val codePoint: Int,
    val textRange: IntRange,
)

private data class BasicShapingCluster(
    val textRange: IntRange,
    val script: String,
    val bidiRun: BidiRun,
)

private data class BasicShapingGroup(
    val script: String,
    val bidiLevel: Int,
    val isRightToLeft: Boolean,
    val clusterRanges: MutableList<IntRange>,
) {
    fun canAppend(cluster: BasicShapingCluster): Boolean {
        val lastClusterRange = clusterRanges.lastOrNull() ?: return false
        return script == cluster.script &&
            bidiLevel == cluster.bidiRun.level &&
            isRightToLeft == cluster.bidiRun.isRightToLeft &&
            lastClusterRange.last + 1 == cluster.textRange.first
    }
}

private fun BasicShapingGroup.textRange(): IntRange =
    clusterRanges.minOf { it.first }..clusterRanges.maxOf { it.last }

private data class ResolvedShapingFontRun(
    val textRange: IntRange,
    val typefaceId: TypefaceID,
    val order: Int,
)

private data class BasicPairAdjustmentContext(
    val typefaceId: TypefaceID,
    val kernTable: OpenTypeKernTable?,
    val gposPairTable: OpenTypeGposPairTable?,
    val tableLabel: String,
    val fontUnitsToFontSizeUnitsScale: Double,
)

private data class StandardLigature(
    val glyphId: Int,
    val textRange: IntRange,
    val clusterCount: Int,
)

private data class EmojiSequence(
    val textRange: IntRange,
    val nextIndex: Int,
)

private enum class TextDirection {
    LeftToRight,
    RightToLeft,
}

private fun pairAdjustmentTableLabel(
    kernTable: OpenTypeKernTable?,
    gposPairTable: OpenTypeGposPairTable?,
): String =
    when {
        gposPairTable != null && kernTable != null -> "Pair-position table"
        gposPairTable != null -> "GPOS pair table"
        else -> "Kern table"
    }

private fun codePointRanges(text: String): List<CodePointRange> =
    codePointRanges(text, text.indices.takeUnless { it.isEmpty() })

private fun codePointRangesFor(request: ShapingRequest): List<CodePointRange> =
    codePointRanges(request.text, normalizedTextRange(request.text, request.textRange))

private fun codePointRanges(text: String, textRange: IntRange?): List<CodePointRange> {
    if (textRange == null) return emptyList()

    val ranges = mutableListOf<CodePointRange>()
    var index = textRange.first
    while (index <= textRange.last) {
        val codePoint = codePointAt(text, index, textRange.last)
        val last = if (codePoint > 0xFFFF && index < textRange.last) index + 1 else index
        ranges += CodePointRange(codePoint, index..last)
        index = last + 1
    }
    return ranges
}

private fun singleCodePointRange(text: String, textRange: IntRange): CodePointRange? =
    codePointRanges(text, textRange).singleOrNull()

private fun codePointSafeTextRange(text: String, requestedRange: IntRange): IntRange? {
    val normalizedRange = normalizedTextRange(text, requestedRange) ?: return null
    var first = normalizedRange.first
    var last = normalizedRange.last

    if (first > 0 && text[first].isLowSurrogate() && text[first - 1].isHighSurrogate()) {
        first -= 1
    }
    if (last < text.lastIndex && text[last].isHighSurrogate() && text[last + 1].isLowSurrogate()) {
        last += 1
    }

    return first..last
}

private fun normalizedTextRange(text: String, requestedRange: IntRange): IntRange? {
    if (text.isEmpty()) return null
    val first = requestedRange.first.coerceAtLeast(0)
    val last = requestedRange.last.coerceAtMost(text.lastIndex)
    return if (first <= last) first..last else null
}

private fun codePointAt(text: String, index: Int, lastIndex: Int): Int {
    val first = text[index].code
    if (first !in 0xD800..0xDBFF || index >= lastIndex) return first

    val second = text[index + 1].code
    return if (second in 0xDC00..0xDFFF) {
        ((first - 0xD800) shl 10) + (second - 0xDC00) + 0x10000
    } else {
        first
    }
}

private fun String.isStrongScript(): Boolean =
    this != SCRIPT_COMMON && this != SCRIPT_INHERITED

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun Int.toUpperHex(): String =
    toString(radix = 16).uppercase().padStart(4, '0')

private fun bidiRun(textRange: IntRange, direction: TextDirection, baseDirection: TextDirection): BidiRun {
    val level = when {
        direction == TextDirection.RightToLeft -> 1
        baseDirection == TextDirection.RightToLeft -> 2
        else -> 0
    }
    return BidiRun(textRange = textRange, level = level, isRightToLeft = level % 2 == 1)
}

private fun isLatin(codePoint: Int): Boolean =
    codePoint in 0x0041..0x005A ||
        codePoint in 0x0061..0x007A ||
        codePoint in 0x00C0..0x00FF && codePoint != 0x00D7 && codePoint != 0x00F7 ||
        codePoint in 0x0100..0x024F ||
        codePoint in 0x1E00..0x1EFF

private fun isHebrew(codePoint: Int): Boolean =
    codePoint in 0x0590..0x05FF

private fun isArabic(codePoint: Int): Boolean =
    codePoint in 0x0600..0x06FF ||
        codePoint in 0x0750..0x077F ||
        codePoint in 0x08A0..0x08FF ||
        codePoint in 0xFB50..0xFDFF ||
        codePoint in 0xFE70..0xFEFC

private fun isArabicNumber(codePoint: Int): Boolean =
    codePoint in 0x0660..0x0669 ||
        codePoint in 0x06F0..0x06F9

private fun isEuropeanNumber(codePoint: Int): Boolean =
    codePoint in 0x0030..0x0039 ||
        codePoint in 0xFF10..0xFF19

private fun isCombiningMark(codePoint: Int): Boolean =
    codePoint in 0x0300..0x036F ||
        codePoint in 0x0591..0x05BD ||
        codePoint == 0x05BF ||
        codePoint in 0x05C1..0x05C2 ||
        codePoint in 0x05C4..0x05C5 ||
        codePoint == 0x05C7 ||
        codePoint in 0x0610..0x061A ||
        codePoint in 0x064B..0x065F ||
        codePoint == 0x0670 ||
        codePoint in 0x06D6..0x06DC ||
        codePoint in 0x06DF..0x06E4 ||
        codePoint in 0x06E7..0x06E8 ||
        codePoint in 0x06EA..0x06ED ||
        codePoint in 0x1AB0..0x1AFF ||
        codePoint in 0x1DC0..0x1DFF ||
        codePoint in 0x20D0..0x20FF ||
        codePoint in 0xFE20..0xFE2F

private fun isVariationSelector(codePoint: Int): Boolean =
    codePoint in 0xFE00..0xFE0F ||
        codePoint in 0xE0100..0xE01EF

private fun isEmojiModifier(codePoint: Int): Boolean =
    codePoint in 0x1F3FB..0x1F3FF

private fun isBaseEmoji(codePoint: Int): Boolean =
    !isEmojiModifier(codePoint) && codePoint in 0x1F000..0x1FAFF ||
        codePoint in 0x2600..0x27BF

private fun isParagraphSeparator(codePoint: Int): Boolean =
    codePoint == 0x000A ||
        codePoint == 0x000D ||
        codePoint == 0x2029

private fun isSegmentSeparator(codePoint: Int): Boolean =
    codePoint == 0x0009 ||
        codePoint == 0x2028

private fun isWhitespace(codePoint: Int): Boolean =
    codePoint == 0x0020 ||
        codePoint == 0x00A0 ||
        codePoint == 0x1680 ||
        codePoint in 0x2000..0x200A ||
        codePoint == 0x202F ||
        codePoint == 0x205F ||
        codePoint == 0x3000
