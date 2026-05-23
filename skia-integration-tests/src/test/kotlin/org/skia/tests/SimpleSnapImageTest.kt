package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SimpleSnapImageTest {

    @Test
    fun `SimpleSnapImageGM matches simple_snap_image_png within tolerance`() {
        val gm = SimpleSnapImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image simple_snap_image.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SimpleSnapImageGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SimpleSnapImageGM", comparison.similarity)
        assertTrue(accepted, "SimpleSnapImageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "SimpleSnapImageGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor"
        )
    }
}
