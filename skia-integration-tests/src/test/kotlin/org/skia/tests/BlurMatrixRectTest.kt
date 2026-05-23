package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurMatrixRectTest {

    @Test
    fun `BlurMatrixRectGM matches reference`() {
        val gm = BlurMatrixRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blur_matrix_rect.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurMatrixRectGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurMatrixRectGM", comparison.similarity)
        assertTrue(accepted, "BlurMatrixRectGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "BlurMatrixRectGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
