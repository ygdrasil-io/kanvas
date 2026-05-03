package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug913349Test {

    @Test
    fun `Crbug913349GM matches crbug_913349_png within tolerance`() {
        val gm = Crbug913349GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_913349.png")
        // 5-vertex line-only AA fill, includes a 2px-tall sliver at the
        // bottom right that exercises winding-count edge cases at very
        // shallow angles.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug913349GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug913349GM", comparison.similarity)
        assertTrue(accepted, "Crbug913349GM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.0,
            "Crbug913349GM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
