package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientDirtyLaundryTest {

    @Test
    fun `GradientDirtyLaundryGM matches gradient_dirty_laundry_png within tolerance`() {
        val gm = GradientDirtyLaundryGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image gradient_dirty_laundry.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("GradientDirtyLaundryGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("GradientDirtyLaundryGM", comparison.similarity)
        assertTrue(accepted, "GradientDirtyLaundryGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 93.1,
            "GradientDirtyLaundryGM similarity ${"%.2f".format(comparison.similarity)}% < 93.1%",
        )
    }
}
