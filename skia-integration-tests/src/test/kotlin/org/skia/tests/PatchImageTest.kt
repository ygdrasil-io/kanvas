package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PatchImageTest {

    @Test
    fun `PatchImageGM matches patch_image_png within tolerance`() {
        val gm = PatchImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image patch_image.png")
        // mandrill_128 image shader + Coons-patch tessellation; AWT PNG
        // codec vs upstream Skia codec introduces ~1-2 channel drift on the
        // image pixels, plus blend-mode composition. Tolerance 8 absorbs that.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PatchImageGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PatchImageGM", comparison.similarity)
        assertTrue(accepted, "PatchImageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "PatchImageGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
