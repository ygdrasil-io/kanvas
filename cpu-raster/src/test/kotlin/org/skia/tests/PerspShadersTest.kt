package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase H1-finish-B runner for [PerspShadersGM] (`persp_shaders_aa` and
 * `persp_shaders_bw`).
 *
 * Drawn under a perspective CTM — image cells go through
 * `SkCanvas.drawImageRect`'s axis-aligned fast path which drops the draw
 * under non-axis-aligned matrices in `:kanvas-skia`. Shader-fill cells
 * still render. Loose floor (20%) reflects this known H1.5 gap.
 */
class PerspShadersTest {

    @Test
    fun `PerspShadersGM aa matches persp_shaders_aa_png within tolerance`() {
        val gm = PerspShadersGM(fDoAA = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image persp_shaders_aa.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PerspShadersAaGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PerspShadersAaGM", comparison.similarity)
        assertTrue(accepted, "PerspShadersAaGM regressed below ratchet")
    }

    @Test
    fun `PerspShadersGM bw matches persp_shaders_bw_png within tolerance`() {
        val gm = PerspShadersGM.bw()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image persp_shaders_bw.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PerspShadersBwGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PerspShadersBwGM", comparison.similarity)
        assertTrue(accepted, "PerspShadersBwGM regressed below ratchet")
    }
}
