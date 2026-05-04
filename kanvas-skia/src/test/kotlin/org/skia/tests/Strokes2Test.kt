package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Strokes2Test {
    @Test
    fun `Strokes2GM matches strokes_poly_png within tolerance`() {
        val gm = Strokes2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokes_poly.png")
        // 25 cumulatively-rotated 13-segment stroked polylines per pane,
        // exercising rotate(deg, px, py) under both AA and non-AA paths.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Strokes2GM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Strokes2GM", comparison.similarity)
        assertTrue(accepted, "Strokes2GM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "Strokes2GM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
