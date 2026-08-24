package org.graphiks.kanvas.gpu.evidence.compare

import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.test.ComparisonUtils

/** Narrow evidence adapter around the repository's canonical RGBA comparator. */
class EvidenceComparator {
    fun compare(
        actual: ByteArray,
        expected: ByteArray,
        width: Int,
        height: Int,
        policy: ComparisonPolicy,
    ): ImageComparison {
        require(width > 0 && height > 0) { "comparison dimensions must be positive" }
        require(actual.size == width * height * 4) { "actual RGBA byte count does not match dimensions" }
        require(expected.size == actual.size) { "expected RGBA byte count does not match actual" }
        val result = ComparisonUtils.compareRgba(
            actual = actual,
            reference = expected,
            width = width,
            height = height,
            tolerance = policy.perChannelTolerance,
            minSimilarity = policy.minimumSimilarityPercent,
        )
        return ImageComparison(
            passed = result.isPassing,
            similarityPercent = result.similarity,
            differingPixels = result.totalPixels - result.matchingPixels,
            maxChannelDifference = result.maxDiff.maxOrNull() ?: 0,
            meanChannelDifference = result.meanDiff.average(),
            diffRgba = result.diffRgba ?: ByteArray(actual.size),
            policyVersion = policy.version,
        )
    }
}
