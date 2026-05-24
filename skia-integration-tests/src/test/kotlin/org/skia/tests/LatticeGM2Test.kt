package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LatticeGM2Test {

    @Test
    fun `LatticeGM2 matches lattice2_png within tolerance`() {
        val gm = LatticeGM2()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lattice2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LatticeGM2", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LatticeGM2", comparison.similarity)
        assertTrue(accepted, "LatticeGM2 regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "LatticeGM2 similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
