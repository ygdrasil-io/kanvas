package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class NonClosedPathsTest {
    @Test
    fun `NonClosedPathsGM matches nonclosedpaths_png within tolerance`() {
        val gm = NonClosedPathsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image nonclosedpaths.png")
        // 216 stroked permutations (3 closure types × 2 styles × 3 caps ×
        // 3 joins × 4 widths) + 3 fill cells. The hairline (width=0) cases
        // fall back to width=1 in our pipeline and drift from upstream.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("NonClosedPathsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("NonClosedPathsGM", comparison.similarity)
        assertTrue(accepted, "NonClosedPathsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "NonClosedPathsGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% (hairline fallback)")
    }
}
