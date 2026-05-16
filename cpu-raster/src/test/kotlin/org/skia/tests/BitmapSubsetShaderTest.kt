package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BitmapSubsetShaderTest {

    @Test
    fun `BitmapSubsetShaderGM matches bitmap_subset_shader_png within tolerance`() {
        val gm = BitmapSubsetShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bitmap_subset_shader.png")
        // Two bitmap-shader rect fills with `Scale(0.75) · Rotate(30°)`
        // local matrices. Output matches upstream's tiled-shader
        // rasterizer within sampling round-off ; bilinear residuals
        // along tile seams account for the bulk of mismatching pixels.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BitmapSubsetShaderGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BitmapSubsetShaderGM", comparison.similarity)
        assertTrue(accepted, "BitmapSubsetShaderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 97.0,
            "BitmapSubsetShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 97% floor",
        )
    }
}
