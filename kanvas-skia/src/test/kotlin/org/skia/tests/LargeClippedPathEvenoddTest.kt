package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LargeClippedPathEvenoddTest {

    @Test
    fun `LargeClippedPathEvenoddGM matches largeclippedpath_evenodd_png within tolerance`() {
        val gm = LargeClippedPathEvenoddGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image largeclippedpath_evenodd.png")
        // Same scene as the winding variant but the flower path is rendered under
        // SkPathFillType.kEvenOdd — the inner-disc subcontour and petal self-
        // intersections subtract via even-odd, producing donut-shaped fills.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("LargeClippedPathEvenoddGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LargeClippedPathEvenoddGM", comparison.similarity)
        assertTrue(accepted, "LargeClippedPathEvenoddGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "LargeClippedPathEvenoddGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=2 floor)",
        )
    }
}
