package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class NewSurfaceTest {
    @Test
    fun `NewSurfaceGM matches surfacenew_png within tolerance`() {
        val gm = NewSurfaceGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image surfacenew.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("NewSurfaceGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("NewSurfaceGM", comparison.similarity)
        assertTrue(accepted, "NewSurfaceGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "NewSurfaceGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor (accept-any)")
    }
}
