package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientMatrixTest {

    @Test
    fun `GradientMatrixGM matches gradient_matrix_png within tolerance`() {
        val gm = GradientMatrixGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image gradient_matrix.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("GradientMatrixGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("GradientMatrixGM", comparison.similarity)
        assertTrue(accepted, "GradientMatrixGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 66.0,
            "GradientMatrixGM similarity ${"%.2f".format(comparison.similarity)}% < 66.0%",
        )
    }
}
