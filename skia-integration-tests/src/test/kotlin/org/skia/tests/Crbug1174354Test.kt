package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Validates [Crbug1174354GM] against the upstream Skia reference
 * `crbug_1174354.png`. Exercises four stacked `saveLayer`s with
 * [org.skia.core.SaveLayerRec] `backdrop` blurs of progressively
 * larger σ (5, 15, 30, 70) over a sweep-gradient inner layer.
 *
 * Floor 40 % : the GM is dominated by background gradients ;
 * the upstream reference encodes a pyramid-downsampled blur whose
 * energy distribution doesn't exactly match a direct-convolution
 * clamp blur at the larger σ values (15, 30, 70). We're not yet
 * shooting for σ-pyramid parity — the test guards correctness of
 * the saveLayer/backdrop wiring + the basic clamp-mode behaviour.
 * The 4-tile stack makes ~60% of the canvas a tinted halo where
 * the direct blur diverges from the pyramid blur in the reference.
 */
class Crbug1174354Test {

    @Test
    fun `Crbug1174354GM matches crbug_1174354_png within tolerance`() {
        val gm = Crbug1174354GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1174354.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("Crbug1174354GM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1174354GM", comparison.similarity)
        assertTrue(accepted, "Crbug1174354GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 40.0,
            "Crbug1174354GM similarity ${"%.2f".format(comparison.similarity)}% < 40.0% floor",
        )
    }
}
