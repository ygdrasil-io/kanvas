package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PatchImagePerspTest {

    @Test
    fun `PatchImagePerspGM matches patch_image_persp_png within tolerance`() {
        val gm = PatchImagePerspGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image patch_image_persp.png")
        // Perspective local matrix (persp0 = 0.00001f) on the image shader.
        // Slight perspective warp + AWT PNG codec drift vs upstream. Tolerance
        // 8 absorbs per-channel drift; 30% floor guards against catastrophic
        // regressions in the patch tessellator or perspective mapping path.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PatchImagePerspGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PatchImagePerspGM", comparison.similarity)
        assertTrue(accepted, "PatchImagePerspGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "PatchImagePerspGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
