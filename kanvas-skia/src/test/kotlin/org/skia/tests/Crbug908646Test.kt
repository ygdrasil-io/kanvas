package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug908646Test {

    @Test
    fun `Crbug908646GM matches crbug_908646_png within tolerance`() {
        val gm = Crbug908646GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_908646.png")
        // Multi-contour even-odd AA fill: outer square + two triangles
        // form holes via odd-crossing. Pixel-aligned vertices give a near
        // bit-exact match outside the AA edge band.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug908646GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug908646GM", comparison.similarity)
        assertTrue(accepted, "Crbug908646GM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.0,
            "Crbug908646GM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
