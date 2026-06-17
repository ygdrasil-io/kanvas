package org.graphiks.kanvas.text.paragraph

import org.graphiks.kanvas.text.shaping.BasicTextSegmenter
import org.graphiks.kanvas.text.shaping.PinnedUnicodeDataGenerator
import org.graphiks.kanvas.text.shaping.PinnedUnicodeDataSetResources
import org.graphiks.kanvas.text.shaping.TextSegmenter
import org.graphiks.kanvas.text.shaping.UnicodeDataSet

public const val PARAGRAPH_LOCALE_BREAK_REFINEMENT_UNAVAILABLE_DIAGNOSTIC_CODE: String =
    "text.paragraph.locale-break-refinement-unavailable"

public const val PARAGRAPH_LINE_BREAK_DATA_UNAVAILABLE_DIAGNOSTIC_CODE: String =
    "text.paragraph.line-break-data-unavailable"

public enum class LineBreakKind {
    MANDATORY,
    ALLOWED,
    PROHIBITED,
}

public data class LineBreakOpportunity(
    public val offset: Int,
    public val kind: LineBreakKind,
    public val reason: String,
    public val clusterRange: IntRange,
)

public data class LineBreakMap(
    public val inputHash: String,
    public val unicodeVersion: String,
    public val inputTextRange: IntRange,
    public val opportunities: List<LineBreakOpportunity> = emptyList(),
    public val diagnostics: List<ParagraphLayoutDiagnostic> = emptyList(),
)

public class Uax14LineBreaker(
    private val unicodeDataSet: UnicodeDataSet? = PinnedUnicodeDataSetResources.load(),
    private val textSegmenter: TextSegmenter = BasicTextSegmenter(),
    private val expectedUnicodeVersion: String = PinnedUnicodeDataGenerator.PinnedUnicodeVersion,
) : LineBreaker {
    public fun analyze(paragraph: Paragraph): LineBreakMap {
        val textRange = if (paragraph.text.isEmpty()) IntRange.EMPTY else paragraph.text.indices
        val data = unicodeDataSet ?: return LineBreakMap(
            inputHash = paragraph.inputHash,
            unicodeVersion = expectedUnicodeVersion,
            inputTextRange = textRange,
            diagnostics = listOf(
                ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_LINE_BREAK_DATA_UNAVAILABLE_DIAGNOSTIC_CODE,
                    message = "Pinned Unicode line break data is unavailable.",
                    textRange = textRange.takeIf { !it.isEmpty() },
                    severity = "refusal",
                ),
            ),
        )
        if (paragraph.text.isEmpty()) {
            return LineBreakMap(
                inputHash = paragraph.inputHash,
                unicodeVersion = data.version.value,
                inputTextRange = textRange,
            )
        }

        val clusters = textSegmenter.segment(paragraph.text)
        val diagnostics = mutableListOf<ParagraphLayoutDiagnostic>()
        localeRefinementDiagnostic(paragraph, data)?.let(diagnostics::add)
        val opportunities = buildList {
            for (index in 1 until clusters.size) {
                val previous = clusters[index - 1]
                val next = clusters[index]
                add(boundaryOpportunity(paragraph, data, previous, next))
            }
            add(
                LineBreakOpportunity(
                    offset = paragraph.text.length,
                    kind = LineBreakKind.MANDATORY,
                    reason = "end-of-text",
                    clusterRange = clusters.last(),
                ),
            )
        }
        return LineBreakMap(
            inputHash = paragraph.inputHash,
            unicodeVersion = data.version.value,
            inputTextRange = textRange,
            opportunities = opportunities,
            diagnostics = diagnostics,
        )
    }

    override fun breakLines(paragraph: Paragraph, maxWidth: Float): List<IntRange> {
        validateLineBreakMaxWidth(maxWidth)
        if (paragraph.text.isEmpty()) return emptyList()

        val analysis = analyze(paragraph)
        val text = paragraph.text
        val clusters = textSegmenter.segment(text)
        val opportunitiesByOffset = analysis.opportunities.associateBy { it.offset }
        val ranges = mutableListOf<IntRange>()
        var lineStart = skipBreakSeparators(text, 0)
        while (lineStart < text.length) {
            var width = 0f
            var lastAllowedOffset: Int? = null
            var emitted = false
            var clusterIndex = clusters.indexOfFirst { cluster -> cluster.first >= lineStart }
            if (clusterIndex < 0) break
            while (clusterIndex < clusters.size) {
                val cluster = clusters[clusterIndex]
                val nextWidth = width + paragraph.estimatedWidthForLineBreak(cluster)
                if (nextWidth > maxWidth && cluster.first > lineStart) {
                    val breakOffset = lastAllowedOffset ?: if (paragraph.paragraphStyle.softWrap) cluster.first else null
                    if (breakOffset != null) {
                        ranges += lineStart..(breakOffset - 1)
                        lineStart = skipBreakSeparators(text, breakOffset)
                        emitted = true
                        break
                    }
                }

                width = nextWidth
                val boundaryOffset = clusters.getOrNull(clusterIndex + 1)?.first ?: text.length
                when (opportunitiesByOffset[boundaryOffset]?.kind) {
                    LineBreakKind.MANDATORY -> {
                        ranges += lineStart..(boundaryOffset - 1)
                        lineStart = skipBreakSeparators(text, boundaryOffset)
                        emitted = true
                        break
                    }
                    LineBreakKind.ALLOWED -> lastAllowedOffset = boundaryOffset
                    else -> Unit
                }
                clusterIndex += 1
            }
            if (!emitted) {
                ranges += lineStart..text.lastIndex
                break
            }
        }
        return ranges
    }

    private fun boundaryOpportunity(
        paragraph: Paragraph,
        unicodeData: UnicodeDataSet,
        previous: IntRange,
        next: IntRange,
    ): LineBreakOpportunity {
        val previousCodePoint = paragraph.text.codePointAt(previous.first)
        val nextCodePoint = paragraph.text.codePointAt(next.first)
        val softWrapEnabled = paragraph.paragraphStyle.softWrap
        return when {
            paragraph.text[next.first] == '\n' || paragraph.text[next.first] == '\r' -> LineBreakOpportunity(
                offset = next.first,
                kind = LineBreakKind.MANDATORY,
                reason = "explicit-newline",
                clusterRange = next,
            )
            paragraph.text[next.first] == ' ' -> optionalOpportunity(
                offset = next.first,
                softWrapEnabled = softWrapEnabled,
                clusterRange = next,
                reason = "space-separator",
            )
            paragraph.text[previous.first] == '-' || unicodeData.lineBreak.valueAt(previousCodePoint) in HyphenBreakClasses -> optionalOpportunity(
                offset = next.first,
                softWrapEnabled = softWrapEnabled,
                clusterRange = next,
                reason = "hyphen",
            )
            areCjkBreakNeighbors(previousCodePoint, nextCodePoint, unicodeData) -> optionalOpportunity(
                    offset = next.first,
                    softWrapEnabled = softWrapEnabled,
                    clusterRange = next,
                    reason = "cjk-cluster-boundary",
                )
            else -> LineBreakOpportunity(
                offset = next.first,
                kind = LineBreakKind.PROHIBITED,
                reason = "default-prohibited",
                clusterRange = next,
            )
        }
    }

    private fun optionalOpportunity(
        offset: Int,
        softWrapEnabled: Boolean,
        clusterRange: IntRange,
        reason: String,
    ): LineBreakOpportunity =
        LineBreakOpportunity(
            offset = offset,
            kind = if (softWrapEnabled) LineBreakKind.ALLOWED else LineBreakKind.PROHIBITED,
            reason = if (softWrapEnabled) reason else "soft-wrap-disabled",
            clusterRange = clusterRange,
        )

    private fun localeRefinementDiagnostic(
        paragraph: Paragraph,
        unicodeData: UnicodeDataSet,
    ): ParagraphLayoutDiagnostic? {
        val locale = paragraph.paragraphStyle.defaultLocale ?: return null
        if (!locale.requiresDictionaryRefinement()) return null
        val affectedRange = textRangeForScripts(
            text = paragraph.text,
            unicodeData = unicodeData,
            scripts = DictionaryRefinementScripts,
        ) ?: textRangeForDictionaryRefinement(paragraph.text) ?: return null
        return ParagraphLayoutDiagnostic(
            code = PARAGRAPH_LOCALE_BREAK_REFINEMENT_UNAVAILABLE_DIAGNOSTIC_CODE,
            message = "Locale-specific line break refinement for '$locale' is unavailable; using base UAX #14 classes only.",
            textRange = affectedRange,
        )
    }
}

private val HyphenBreakClasses: Set<String> = setOf("HY", "BA")
private val CjkBreakClasses: Set<String> = setOf("ID", "CJ", "NS", "H2", "H3", "JL", "JV", "JT")
private val DictionaryRefinementScripts: Set<String> = setOf("Thai", "Laoo", "Khmr")

private fun String.requiresDictionaryRefinement(): Boolean =
    startsWith("th", ignoreCase = true) ||
        startsWith("lo", ignoreCase = true) ||
        startsWith("km", ignoreCase = true)

private fun textRangeForScripts(
    text: String,
    unicodeData: UnicodeDataSet,
    scripts: Set<String>,
): IntRange? {
    if (text.isEmpty()) return null
    var first: Int? = null
    var last: Int? = null
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (unicodeData.script.valueAt(codePoint) in scripts) {
            if (first == null) first = index
            last = index + Character.charCount(codePoint) - 1
        }
        index += Character.charCount(codePoint)
    }
    return if (first != null && last != null) first..last else null
}

private fun textRangeForDictionaryRefinement(text: String): IntRange? {
    if (text.isEmpty()) return null
    var first: Int? = null
    var last: Int? = null
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (codePoint.requiresDictionaryRefinementHeuristic()) {
            if (first == null) first = index
            last = index + Character.charCount(codePoint) - 1
        }
        index += Character.charCount(codePoint)
    }
    return if (first != null && last != null) first..last else null
}

private fun areCjkBreakNeighbors(
    previousCodePoint: Int,
    nextCodePoint: Int,
    unicodeData: UnicodeDataSet,
): Boolean =
    (unicodeData.lineBreak.valueAt(previousCodePoint) in CjkBreakClasses &&
        unicodeData.lineBreak.valueAt(nextCodePoint) in CjkBreakClasses) ||
        (previousCodePoint.isCjkBreakableCodePoint() && nextCodePoint.isCjkBreakableCodePoint())

private fun Int.isCjkBreakableCodePoint(): Boolean =
    this in 0x3400..0x9FFF ||
        this in 0x3040..0x30FF ||
        this in 0xAC00..0xD7AF

private fun Int.requiresDictionaryRefinementHeuristic(): Boolean =
    this in 0x0E00..0x0E7F ||
        this in 0x0E80..0x0EFF ||
        this in 0x1780..0x17FF

private fun skipBreakSeparators(text: String, offset: Int): Int {
    var index = offset
    while (index < text.length && (text[index] == ' ' || text[index] == '\n' || text[index] == '\r')) {
        index += 1
    }
    return index
}

private fun validateLineBreakMaxWidth(maxWidth: Float) {
    require(maxWidth.isFinite() && maxWidth >= 0f) { "maxWidth must be finite and non-negative." }
}

private fun Paragraph.estimatedWidthForLineBreak(range: IntRange): Float =
    placeholders.entries.firstOrNull { (placeholderRange) -> placeholderRange.first <= range.last && range.first <= placeholderRange.last }?.value?.width
        ?: textStyles.entries.firstOrNull { (styleRange) -> range.first in styleRange }?.value?.fontSize
        ?: TextStyle().fontSize
