package org.graphiks.kanvas.text.paragraph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.text.shaping.BasicTextSegmenter
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.ShapingRequest
import org.graphiks.kanvas.text.shaping.TextSegmenter

public const val PARAGRAPH_CLUSTER_BOUNDARY_VIOLATION_DIAGNOSTIC_CODE: String =
    "text.paragraph.cluster-boundary-violation"

public data class ParagraphShapingRequest(
    public val textRange: IntRange,
    public val sourceStyleRange: IntRange,
    public val fontFamilies: List<String> = emptyList(),
    public val fallbackPreference: FallbackPreference = FallbackPreference.SYSTEM_DEFAULT,
    public val typefaceId: TypefaceID? = null,
    public val fontSize: Float = 12f,
    public val locale: String? = null,
    public val scriptHint: String? = null,
    public val features: Map<String, Int> = emptyMap(),
    public val variationCoordinates: Map<String, Float> = emptyMap(),
    public val paragraphDirection: TextDirection = TextDirection.AUTO,
) {
    public fun toShapingRequest(text: String): ShapingRequest =
        ShapingRequest(
            text = text,
            textRange = textRange,
            typefaceId = typefaceId,
            fontSize = fontSize,
            features = FeatureSet(features),
            locale = locale,
            paragraphDirection = paragraphDirection.legacyValue,
            preferredFamilies = fontFamilies,
        )
}

public data class ParagraphShapingPlan(
    public val requests: List<ParagraphShapingRequest> = emptyList(),
    public val placeholderRanges: List<IntRange> = emptyList(),
    public val diagnostics: List<ParagraphLayoutDiagnostic> = emptyList(),
)

public interface ParagraphShapingSegmenter {
    public fun segment(
        paragraph: Paragraph,
        textRange: IntRange,
    ): ParagraphShapingPlan
}

public class BasicParagraphShapingSegmenter(
    private val textSegmenter: TextSegmenter = BasicTextSegmenter(),
) : ParagraphShapingSegmenter {
    override fun segment(
        paragraph: Paragraph,
        textRange: IntRange,
    ): ParagraphShapingPlan {
        val normalizedTextRange = paragraph.normalizedTextRange(textRange) ?: return ParagraphShapingPlan()
        val clusters = textSegmenter.segment(paragraph.text).filter { cluster ->
            cluster.paragraphOverlaps(normalizedTextRange)
        }
        val requests = mutableListOf<ParagraphShapingRequest>()
        val placeholderRanges = mutableListOf<IntRange>()
        val diagnostics = mutableListOf<ParagraphLayoutDiagnostic>()

        clusters.forEach { cluster ->
            if (paragraph.placeholders.keys.any { it.paragraphOverlaps(cluster) }) {
                placeholderRanges += cluster
                return@forEach
            }

            val overlappingStyles = paragraph.overlappingStyleEntries(cluster)
            val selectedEntry = overlappingStyles.firstOrNull()?.let { entry ->
                entry.key to entry.value
            } ?: (cluster to TextStyle())
            if (overlappingStyles.map { it.value.shapingSignature() }.distinct().size > 1) {
                diagnostics += ParagraphLayoutDiagnostic(
                    code = PARAGRAPH_CLUSTER_BOUNDARY_VIOLATION_DIAGNOSTIC_CODE,
                    message = "Style boundary intersects grapheme cluster ${cluster.toParagraphDumpLabel()}; the bounded paragraph segmenter widens to the leading style range.",
                    textRange = cluster,
                    severity = "diagnostic",
                )
            }

            val currentRequest = selectedEntry.toParagraphShapingRequest(
                textRange = cluster,
                paragraphDirection = paragraph.paragraphStyle.textDirection,
            )
            val previous = requests.lastOrNull()
            if (previous != null && previous.canAppend(cluster, selectedEntry.first, selectedEntry.second, paragraph.paragraphStyle.textDirection)) {
                requests[requests.lastIndex] = previous.copy(
                    textRange = previous.textRange.first..cluster.last,
                    sourceStyleRange = previous.sourceStyleRange.first..selectedEntry.first.last,
                )
            } else {
                requests += currentRequest
            }
        }

        return ParagraphShapingPlan(
            requests = requests,
            placeholderRanges = placeholderRanges,
            diagnostics = diagnostics,
        )
    }
}

private fun Paragraph.normalizedTextRange(textRange: IntRange): IntRange? {
    if (text.isEmpty()) return null
    val first = textRange.first.coerceAtLeast(0)
    val last = textRange.last.coerceAtMost(text.lastIndex)
    return if (first <= last) first..last else null
}

private fun Paragraph.overlappingStyleEntries(textRange: IntRange): List<Map.Entry<IntRange, TextStyle>> =
    textStyles.entries
        .filter { (range) -> range.paragraphOverlaps(textRange) }
        .sortedBy { it.key.first }

private fun Pair<IntRange, TextStyle>.toParagraphShapingRequest(
    textRange: IntRange,
    paragraphDirection: TextDirection,
): ParagraphShapingRequest =
    ParagraphShapingRequest(
        textRange = textRange,
        sourceStyleRange = first,
        fontFamilies = second.fontFamilies,
        fallbackPreference = second.fallbackPreference,
        typefaceId = second.typefaceId,
        fontSize = second.fontSize,
        locale = second.locale,
        scriptHint = second.scriptHint,
        features = second.features,
        variationCoordinates = second.variationCoordinates,
        paragraphDirection = paragraphDirection,
    )

private fun ParagraphShapingRequest.canAppend(
    nextCluster: IntRange,
    nextStyleRange: IntRange,
    nextStyle: TextStyle,
    nextParagraphDirection: TextDirection,
): Boolean =
    textRange.last + 1 == nextCluster.first &&
        fontFamilies == nextStyle.fontFamilies &&
        fallbackPreference == nextStyle.fallbackPreference &&
        typefaceId == nextStyle.typefaceId &&
        fontSize == nextStyle.fontSize &&
        locale == nextStyle.locale &&
        scriptHint == nextStyle.scriptHint &&
        features == nextStyle.features &&
        variationCoordinates == nextStyle.variationCoordinates &&
        paragraphDirection == nextParagraphDirection

private fun TextStyle.shapingSignature(): List<Any?> =
    listOf(
        fontFamilies,
        fallbackPreference,
        typefaceId,
        fontSize,
        locale,
        scriptHint,
        features.toSortedMap(),
        variationCoordinates.toSortedMap(),
    )

private fun IntRange.paragraphOverlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun IntRange.toParagraphDumpLabel(): String = "$first..$last"
