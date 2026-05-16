package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug640176Test {

    @Test
    fun `Crbug640176GM matches crbug_640176_png within tolerance`() {
        val gm = Crbug640176GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_640176.png")
        // Conic-w-0.965926 adjacent to two line segments — repro for
        // a chromium AA fill subdivision bug.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug640176GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug640176GM", comparison.similarity)
        assertTrue(accepted, "Crbug640176GM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "Crbug640176GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=1 floor)")
    }
}
