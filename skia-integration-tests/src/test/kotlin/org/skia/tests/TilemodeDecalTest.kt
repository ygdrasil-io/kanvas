package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Raster smoke-test for [TilemodeDecalGM] (`tilemode_decal`, 720×1100).
 *
 * `SkTileMode.kDecal` is fully implemented in `:kanvas-skia` for both
 * image shaders ([SkBitmapShader]) and gradient shaders ([lookupStop]).
 * Five shader recipes × four tile-mode pairs; bicubic Mitchell sampling
 * and decal-edge blending produce per-pixel drift vs. Skia's GPU
 * pipeline, so tolerance=8 is used (matching other image-shader GMs).
 */
class TilemodeDecalTest {

    @Test
    fun `TilemodeDecalGM matches tilemode_decal_png within tolerance`() {
        val gm = TilemodeDecalGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemode_decal.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TilemodeDecalGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TilemodeDecalGM", comparison.similarity)
        assertTrue(accepted, "TilemodeDecalGM regressed below ratchet")
    }
}
