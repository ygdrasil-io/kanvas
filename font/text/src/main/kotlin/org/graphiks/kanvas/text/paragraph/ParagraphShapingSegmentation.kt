package org.graphiks.kanvas.text.paragraph

import org.graphiks.kanvas.font.FallbackRequest
import org.graphiks.kanvas.font.FontResolver
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.font.TypefaceVariationCoordinate
import org.graphiks.kanvas.text.shaping.BasicBidiResolver
import org.graphiks.kanvas.text.shaping.BasicScriptItemizer
import org.graphiks.kanvas.text.shaping.BasicTextSegmenter
import org.graphiks.kanvas.text.shaping.BidiResolver
import org.graphiks.kanvas.text.shaping.ScriptItemizer
import org.graphiks.kanvas.text.shaping.ShapingRequest
import org.graphiks.kanvas.text.shaping.TextSegmenter

public const val PARAGRAPH_CLUSTER_BOUNDARY_VIOLATION_DIAGNOSTIC_CODE: String =
    "text.paragraph.cluster-boundary-violation"

public const val PARAGRAPH_FALLBACK_UNRESOLVED_DIAGNOSTIC_CODE: String =
    "text.paragraph.fallback-unresolved"

public data class ParagraphShapingRequest(
    public val segmentId: String,
    public val textRange: IntRange,
    public val typefaceId: TypefaceID?,
    public val style: TextStyle,
    public val script: String,
    public val bidiLevel: Int,
)

public data class ParagraphShapingPlan(
    public val requests: List<ParagraphShapingRequest>,
    public val placeholderRanges: List<IntRange>,
    public val diagnostics: List<ParagraphLayoutDiagnostic>,
) {
}

public interface ParagraphShapingSegmenter {
    public fun segment(paragraph: Paragraph): ParagraphShapingPlan
}

public class DefaultParagraphShapingSegmenter(
    private val segmenter: TextSegmenter = BasicTextSegmenter(),
    private val scriptItemizer: ScriptItemizer = BasicScriptItemizer(),
    private val bidiResolver: BidiResolver = BasicBidiResolver(),
    @Suppress("UNUSED_PARAMETER")
    private val fontResolver: FontResolver? = null,
) : ParagraphShapingSegmenter {
    override fun segment(paragraph: Paragraph): ParagraphShapingPlan {
        if (paragraph.text.isEmpty()) {
            return ParagraphShapingPlan(
                requests = emptyList(),
                placeholderRanges = emptyList(),
                diagnostics = emptyList(),
            )
        }

        val request = ShapingRequest(
            text = paragraph.text,
            textRange = paragraph.text.indices,
            fontSize = paragraph.textStyles.values.firstOrNull()?.fontSize ?: 12f,
            paragraphDirection = paragraph.paragraphStyle.textDirection.legacyValue,
        )
        val scriptRuns = scriptItemizer.itemize(request)
        val bidiRuns = bidiResolver.resolve(request)

        val diagnostics = mutableListOf<ParagraphLayoutDiagnostic>()
        val placeholderRanges = mutableListOf<IntRange>()
        val pending = mutableListOf<PendingParagraphRequest>()

        segmenter.segment(paragraph.text).forEach { clusterRange ->
            if (paragraph.placeholders.keys.any { placeholderRange -> placeholderRange.overlaps(clusterRange) }) {
                placeholderRanges += clusterRange
                return@forEach
            }
            val leadingStyle = paragraph.styleAtIndex(clusterRange.first)
            if (clusterUsesMultipleStyles(paragraph, clusterRange, leadingStyle)) {
                diagnostics += ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_CLUSTER_BOUNDARY_VIOLATION_DIAGNOSTIC_CODE,
                    message = "Style boundaries must align to grapheme cluster coverage; widening to the leading style range.",
                    textRange = clusterRange,
                    severity = "diagnostic",
                )
            }
            pending += PendingParagraphRequest(
                textRange = clusterRange,
                style = leadingStyle,
                script = scriptRuns.firstOrNull { run -> clusterRange.first in run.textRange }?.script ?: "Zyyy",
                bidiLevel = bidiRuns.firstOrNull { run -> clusterRange.first in run.textRange }?.level ?: 0,
            )
        }

        val requests = pending.coalesce()
            .flatMap { requestCandidate -> resolveTypefaceRuns(paragraph, requestCandidate, diagnostics) }
            .mapIndexed { index, requestCandidate ->
                ParagraphShapingRequest(
                    segmentId = "seg-${index.toString().padStart(3, '0')}",
                    textRange = requestCandidate.textRange,
                    typefaceId = requestCandidate.typefaceId,
                    style = requestCandidate.style,
                    script = requestCandidate.script,
                    bidiLevel = requestCandidate.bidiLevel,
                )
            }

        return ParagraphShapingPlan(
            requests = requests,
            placeholderRanges = placeholderRanges,
            diagnostics = diagnostics.sortedWith(compareBy<ParagraphLayoutDiagnostic> { it.textRange?.first ?: Int.MAX_VALUE }.thenBy { it.code }),
        )
    }

    private fun resolveTypefaceRuns(
        paragraph: Paragraph,
        requestCandidate: PendingParagraphRequest,
        diagnostics: MutableList<ParagraphLayoutDiagnostic>,
    ): List<PendingParagraphRequest> {
        val resolver = fontResolver ?: return listOf(requestCandidate.copy(typefaceId = requestCandidate.style.typefaceId))
        val requestText = paragraph.text.substring(requestCandidate.textRange.first, requestCandidate.textRange.last + 1)
        val resolvedRuns = resolver.resolve(
            FallbackRequest(
                text = requestText,
                locale = requestCandidate.style.locale ?: paragraph.paragraphStyle.defaultLocale,
                preferredFamilies = requestCandidate.style.fontFamilies,
                style = requestCandidate.style.fontStyle,
                variationCoordinates = requestCandidate.style.variationCoordinates.toTypefaceVariationCoordinates(),
            ),
        )
        if (resolvedRuns.isEmpty()) {
            diagnostics += ParagraphLayoutDiagnostic(
                code = PARAGRAPH_FALLBACK_UNRESOLVED_DIAGNOSTIC_CODE,
                message = "No fallback typeface resolved for paragraph segment ${requestCandidate.textRange.first}..${requestCandidate.textRange.last}.",
                textRange = requestCandidate.textRange,
                severity = "refusal",
            )
            return emptyList()
        }
        return resolvedRuns.map { run ->
            requestCandidate.copy(
                textRange = (requestCandidate.textRange.first + run.start)..(requestCandidate.textRange.first + run.end - 1),
                typefaceId = run.face.typeface.id,
            )
        }
    }
}

private data class PendingParagraphRequest(
    val textRange: IntRange,
    val typefaceId: TypefaceID? = null,
    val style: TextStyle,
    val script: String,
    val bidiLevel: Int,
)

private fun List<PendingParagraphRequest>.coalesce(): List<PendingParagraphRequest> {
    if (isEmpty()) return emptyList()
    val coalesced = mutableListOf(first())
    drop(1).forEach { current ->
        val previous = coalesced.last()
        if (
            previous.textRange.last + 1 == current.textRange.first &&
            previous.script == current.script &&
            previous.bidiLevel == current.bidiLevel &&
            previous.style.sameShapingFactsAs(current.style)
        ) {
            coalesced[coalesced.lastIndex] = previous.copy(textRange = previous.textRange.first..current.textRange.last)
        } else {
            coalesced += current
        }
    }
    return coalesced
}

private fun clusterUsesMultipleStyles(
    paragraph: Paragraph,
    clusterRange: IntRange,
    leadingStyle: TextStyle,
): Boolean =
    (clusterRange.first..clusterRange.last).any { index -> paragraph.styleAtIndex(index) != leadingStyle }

private fun Paragraph.styleAtIndex(index: Int): TextStyle =
    textStyles.entries.firstOrNull { (range) -> index in range }?.value ?: TextStyle()

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun TextStyle.sameShapingFactsAs(other: TextStyle): Boolean =
    fontFamilies == other.fontFamilies &&
        fallbackPreference == other.fallbackPreference &&
        typefaceId == other.typefaceId &&
        fontSize == other.fontSize &&
        fontStyle == other.fontStyle &&
        syntheticStylePolicy == other.syntheticStylePolicy &&
        locale == other.locale &&
        scriptHint == other.scriptHint &&
        features == other.features &&
        variationCoordinates == other.variationCoordinates &&
        palette == other.palette &&
        letterSpacing == other.letterSpacing &&
        wordSpacing == other.wordSpacing

private fun Map<String, Float>.toTypefaceVariationCoordinates(): List<TypefaceVariationCoordinate> =
    entries.sortedBy { (axisTag) -> axisTag }.map { (axisTag, value) ->
        TypefaceVariationCoordinate(axisTag = axisTag, value = value.toDouble())
    }
