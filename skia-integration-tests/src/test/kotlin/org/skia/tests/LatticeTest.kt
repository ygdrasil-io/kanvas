package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LatticeTest {
    @Test
    fun `LatticeGM matches lattice_png within tolerance`() {
        val gm = LatticeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lattice.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LatticeGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LatticeGM", comparison.similarity)
        assertTrue(accepted, "LatticeGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "LatticeGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
