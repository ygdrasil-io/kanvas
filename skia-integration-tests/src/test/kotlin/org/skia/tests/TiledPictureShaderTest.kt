package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Validates [TiledPictureShaderGM] against the upstream `tiled_picture_shader.png`
 * reference (issue #3398 regression test).
 *
 * The GM tiles a 100×100 picture (dark-blue inset rect + light-blue diagonal)
 * over a clipped 400×350 gray area, then a green stripe at the bottom.
 * Colors are 565-quantised (via [org.skia.tools.ToolUtils.colorTo565]) to
 * match the reference captured on an RGB-565 backbuffer.
 *
 * Tolerance 8 absorbs the 565-quantisation rounding that differs from 8888
 * on the blue channel. Floor 60% — the center tiled area should be large
 * and mostly pixel-perfect; the green stripe at the bottom is simple.
 */
class TiledPictureShaderTest {

    @Test
    fun `TiledPictureShaderGM matches tiled_picture_shader_png within tolerance`() {
        val gm = TiledPictureShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tiled_picture_shader.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TiledPictureShaderGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TiledPictureShaderGM", comparison.similarity)
        assertTrue(accepted, "TiledPictureShaderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "TiledPictureShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
