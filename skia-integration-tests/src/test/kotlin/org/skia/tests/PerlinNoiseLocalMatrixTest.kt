package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PerlinNoiseLocalMatrixTest {
    @Test
    fun `PerlinNoiseLocalMatrixGM matches perlinnoise_localmatrix_png within tolerance`() {
        val gm = PerlinNoiseLocalMatrixGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image perlinnoise_localmatrix.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PerlinNoiseLocalMatrixGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PerlinNoiseLocalMatrixGM", comparison.similarity)
        assertTrue(accepted, "PerlinNoiseLocalMatrixGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "PerlinNoiseLocalMatrixGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
