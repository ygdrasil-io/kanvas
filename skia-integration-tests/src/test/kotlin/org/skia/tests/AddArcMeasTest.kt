package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AddArcMeasTest {
    @Test
    fun `AddArcMeasGM matches addarc_meas_png within tolerance`() {
        val gm = AddArcMeasGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image addarc_meas.png")
        // Stroked 400-radius oval + 36 radial black lines, overlaid by 36
        // red lines from SkPathMeasure.getPosTan against an addArc(0, deg)
        // contour. Hits the cubic-Bézier arc flattening + path-measure
        // arc-length integrator end-to-end.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("AddArcMeasGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AddArcMeasGM", comparison.similarity)
        assertTrue(accepted, "AddArcMeasGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "AddArcMeasGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
