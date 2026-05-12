package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug8664Test {

    @Test
    fun `Skbug8664GM matches skbug_8664_png within tolerance`() {
        val gm = Skbug8664GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_8664.png")
        // 830 × 550 canvas. The reference was rendered through the GPU
        // mipmap path under bilinear filtering ; kanvas-skia downsamples
        // via plain bilinear over the source LOD (no mipmap pyramid),
        // so the small variants pick up aliasing the GPU reference
        // smooths away. The clipped + rotated overlay rect adds an
        // edge-only AA differential. Floor reflects the bulk of the
        // image still landing within tolerance.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Skbug8664GM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug8664GM", comparison.similarity)
        assertTrue(accepted, "Skbug8664GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 40.0,
            "Skbug8664GM similarity ${"%.2f".format(comparison.similarity)}% < 40.0% (t=8 floor)",
        )
    }
}
