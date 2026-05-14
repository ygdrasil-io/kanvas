package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsNoTextureTest {
    @Test
    fun `GradientsNoTextureGM matches gradients_no_texture_png within tolerance`() {
        val gm = GradientsNoTextureGM(true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image gradients_no_texture.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("GradientsNoTextureGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("GradientsNoTextureGM", comparison.similarity)
        assertTrue(accepted, "GradientsNoTextureGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 81.1,
            "GradientsNoTextureGM similarity ${"%.2f".format(comparison.similarity)}% < 81.1% floor",
        )
    }
}
