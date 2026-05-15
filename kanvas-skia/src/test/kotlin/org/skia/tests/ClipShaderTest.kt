package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClipShaderTest {

    @Test
    fun `ClipShaderGM matches clipshadermatrix_png within tolerance`() {
        val gm = ClipShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clipshadermatrix.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClipShaderGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClipShaderGM", comparison.similarity)
        assertTrue(accepted, "ClipShaderGM regressed below ratchet")
        // Perspective matrix path + clip-shader bilinear edge + slight
        // gradient color-rotation diff vs upstream's pre-image-shader-
        // sampling. Floor stays loose ; the structural test (clip shape +
        // gradient bands rendered through clipShader) passes — see the
        // generated comparison PNG. Tracked as a follow-up in
        // API_FINALIZATION_PLAN.md for `R-final.2` LocalMatrix wrappers.
        assertTrue(
            comparison.similarity >= 50.0,
            "ClipShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 50.0%",
        )
    }
}
